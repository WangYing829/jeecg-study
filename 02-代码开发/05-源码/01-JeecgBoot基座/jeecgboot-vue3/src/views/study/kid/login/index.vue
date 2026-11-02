<template>
  <!-- 孩子端复用学习端布局（设计规范 §9.5 / §9.8）；未登录不启动用眼会话 -->
  <StudyLayout module-title="登录" :eye-session="false">
    <div class="study-kid-login">
      <!-- 步骤①：输入家庭码 -->
      <section v-if="step === 'code'" class="study-kid-login__panel">
        <h1 class="study-kid-login__title">请输入家庭码</h1>
        <p class="study-kid-login__hint">家庭码由家长给你，一共 6 位</p>

        <a-input
          v-model:value="familyCode"
          class="study-kid-login__input"
          :maxlength="6"
          placeholder="6 位家庭码"
          size="large"
          autocomplete="off"
          @press-enter="onLookup"
        />

        <a-alert
          v-if="errorText"
          class="study-kid-login__error"
          type="warning"
          show-icon
          :message="errorText"
        >
          <template #action>
            <a-button v-if="offline" size="small" @click="onPrimary">重试</a-button>
          </template>
        </a-alert>
      </section>

      <!-- 步骤②：选孩子 + PIN -->
      <section v-else class="study-kid-login__panel">
        <h1 class="study-kid-login__title">选择你的名字</h1>
        <p class="study-kid-login__hint">再输入你的 PIN 就可以开始</p>

        <!-- 候选孩子：只显示头像 + 昵称（服务端就不下发别的，界面也不补） -->
        <div class="study-kid-login__candidates">
          <a-button
            v-for="item in candidates"
            :key="item.id"
            class="study-kid-login__candidate"
            :class="{ 'is-active': item.id === selectedId }"
            @click="onSelect(item.id)"
          >
            <a-avatar
              v-if="item.avatar"
              class="study-kid-login__avatar"
              :size="56"
              :src="item.avatar"
              :alt="item.nickname"
            />
            <a-avatar v-else class="study-kid-login__avatar study-kid-login__avatar--empty" :size="56">
              {{ (item.nickname || '?').slice(0, 1) }}
            </a-avatar>
            <span class="study-kid-login__candidate-name">{{ item.nickname }}</span>
          </a-button>
        </div>

        <a-input-password
          v-model:value="pin"
          class="study-kid-login__input"
          :maxlength="6"
          placeholder="4–6 位 PIN"
          size="large"
          autocomplete="off"
          @press-enter="onLogin"
        />

        <a-alert
          v-if="errorText"
          class="study-kid-login__error"
          type="warning"
          show-icon
          :message="errorText"
        >
          <template #action>
            <a-button v-if="offline" size="small" @click="onPrimary">重试</a-button>
          </template>
        </a-alert>

        <a-button class="study-kid-login__back" type="link" @click="backToCode">返回上一步</a-button>
      </section>
    </div>

    <!-- 底部主操作条：一屏只允许一个主按钮（§10.2） -->
    <template #footer>
      <a-button
        class="study-kid-login__primary"
        type="primary"
        size="large"
        :loading="loading"
        :disabled="!canSubmit"
        @click="onPrimary"
      >
        {{ step === 'code' ? '下一步' : '进入' }}
      </a-button>
    </template>
  </StudyLayout>
</template>

<script lang="ts">
  import { computed, defineComponent, ref } from 'vue';
  import { useRouter } from 'vue-router';
  import { useUserStore } from '/@/store/modules/user';
  import StudyLayout from '/@/layouts/study/index.vue';
  import { describeNetError, OFFLINE_HINT, RETRY_LABEL } from '../../net';
  import {
    FAMILY_CODE_ERROR,
    FAMILY_CODE_LENGTH,
    LOCKED_HINT,
    PIN_ERROR,
    isFamilyCodeFormatValid,
    isPinFormatValid,
    lookupKids,
    loginKid,
    normalizeFamilyCode,
    type StudyKidCandidateVO,
  } from './kidAuthApi';

  /**
   * 孩子端两步登录页（设计规范 §9.8 / §10.2）。
   *
   * <p><b>流程</b>：① 家庭码 → 候选孩子列表；② 选孩子 + PIN → 进入学习端。</p>
   *
   * <p><b>不暴露他人信息</b>（§6.2 / §9.8）：</p>
   * <ul>
   *   <li>步骤①只显示候选孩子的头像与昵称——服务端就不下发账号名等字段，
   *       界面也<b>不凭空补</b>；</li>
   *   <li>家庭码错误统一提示「家庭码不正确」，<b>不区分「家庭不存在」与「码错了」</b>；</li>
   *   <li>PIN 错误是中性表述，<b>不显示剩余次数</b>（§9.8）；</li>
   *   <li>登录成功后只展示家长<b>昵称</b>，不出现家长账号标识。</li>
   * </ul>
   *
   * <p><b>断网不能伪装成「码错了」</b>（F-BASE-08）：网络不可达时给统一的可读提示
   * 与「重试」入口，而不是回落到「家庭码不正确」——那会把网络问题说成孩子输错了。</p>
   */
  export default defineComponent({
    name: 'StudyKidLoginPage',
    components: { StudyLayout },
    setup() {
      const router = useRouter();
      const userStore = useUserStore();

      /** 当前步骤：code = 步骤①家庭码，pin = 步骤②选孩子 + PIN */
      const step = ref<'code' | 'pin'>('code');
      const familyCode = ref('');
      const pin = ref('');
      const candidates = ref<StudyKidCandidateVO[]>([]);
      const selectedId = ref('');
      const loading = ref(false);
      const errorText = ref('');
      /** 当前提示是不是「断网」——决定要不要给「重试」入口 */
      const offline = ref(false);

      const canSubmitCode = computed(() => isFamilyCodeFormatValid(familyCode.value) && !loading.value);
      const canSubmitPin = computed(() => Boolean(selectedId.value) && isPinFormatValid(pin.value) && !loading.value);
      const canSubmit = computed(() => (step.value === 'code' ? canSubmitCode.value : canSubmitPin.value));

      /** 底部主操作条上的那一个主按钮：按当前步骤分发 */
      const onPrimary = () => {
        if (step.value === 'code') {
          onLookup();
        } else {
          onLogin();
        }
      };

      /**
       * 步骤①：提交家庭码。
       *
       * <p>失败一律提示「家庭码不正确」——不区分「没有这个家庭」与「码输错了」，
       * 避免被用来探测家庭是否存在（§6.2）。
       * 但<b>断网要单独说</b>：那是网络问题，不是码错了（F-BASE-08）。</p>
       */
      const onLookup = async () => {
        if (!canSubmitCode.value) {
          return;
        }
        loading.value = true;
        errorText.value = '';
        offline.value = false;
        try {
          const code = normalizeFamilyCode(familyCode.value);
          const list = (await lookupKids(code)) || [];
          candidates.value = list;
          selectedId.value = list[0]?.id || '';
          if (list.length === 0) {
            // 没有候选孩子：留在步骤①，提示不区分原因
            errorText.value = FAMILY_CODE_ERROR;
          } else {
            step.value = 'pin';
          }
        } catch (e) {
          candidates.value = [];
          selectedId.value = '';
          const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
          offline.value = state.offline;
          errorText.value = state.offline ? state.message : FAMILY_CODE_ERROR;
        } finally {
          loading.value = false;
        }
      };

      /** 步骤②：选中候选孩子 */
      const onSelect = (id: string) => {
        selectedId.value = id;
        errorText.value = '';
        offline.value = false;
      };

      /**
       * 步骤②：提交 PIN。
       *
       * <p>PIN 错误用中性表述；锁定提示也不含内部计数细节（§9.8）。</p>
       */
      const onLogin = async () => {
        if (!canSubmitPin.value) {
          return;
        }
        loading.value = true;
        errorText.value = '';
        offline.value = false;
        try {
          const result = await loginKid(selectedId.value, pin.value.trim());
          if (result?.token) {
            // token 必须交给底座 userStore：后续学习端请求由 axios 层统一从
            // store 取 token 注入请求头，自建的浏览器存储键不会被带上。
            userStore.setToken(result.token);
            router.push('/study/word');
          } else {
            errorText.value = PIN_ERROR;
          }
        } catch (e) {
          const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
          if (state.offline) {
            offline.value = true;
            errorText.value = state.message;
            return;
          }
          const message = String(e?.message || '');
          if (message.includes('锁') || message.includes('频繁')) {
            errorText.value = LOCKED_HINT;
          } else {
            errorText.value = PIN_ERROR;
          }
        } finally {
          loading.value = false;
        }
      };

      /** 返回步骤① */
      const backToCode = () => {
        step.value = 'code';
        pin.value = '';
        errorText.value = '';
        offline.value = false;
      };

      return {
        step,
        familyCode,
        pin,
        candidates,
        selectedId,
        loading,
        errorText,
        offline,
        canSubmit,
        onPrimary,
        onSelect,
        onLookup,
        onLogin,
        backToCode,
        FAMILY_CODE_LENGTH,
      };
    },
  });
</script>

<style lang="less">
  .study-kid-login {
    /* 孩子端固定浅色主题（§9.7 / §10.2）；底色由学习端布局提供 */
    display: flex;
    align-items: center;
    justify-content: center;
    min-height: 100%;
    padding: 24px 16px;
    color: @text-color-base;

    &__panel {
      width: 100%;
      max-width: 420px;
    }

    /* 字号按学习端放宽（§10.2）：正文 16px、题干 18px */
    &__title {
      margin: 0 0 8px;
      font-size: 20px;
      font-weight: 600;
      line-height: 1.4;
      color: @text-color-base;
    }

    &__hint {
      margin: 0 0 24px;
      font-size: 14px;
      line-height: 1.5715;
      color: @text-color-call-out;
    }

    &__input {
      margin-bottom: 16px;
      font-size: 18px;
    }

    &__error {
      margin-bottom: 16px;
    }

    /* 底部主操作条里的主按钮（§10.2）：≥44px，拇指可达 */
    &__primary {
      width: 100%;
      min-height: 44px;
      font-size: 18px;
      border-radius: 8px;
    }

    &__back {
      margin-top: 12px;
      font-size: 14px;
    }

    /* 候选孩子：头像 + 昵称，点击目标 ≥44px（§10.1） */
    &__candidates {
      display: flex;
      flex-wrap: wrap;
      gap: 16px;
      margin-bottom: 24px;
    }

    &__candidate {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 8px;
      width: auto;
      min-width: 88px;
      min-height: 44px;
      height: auto;
      padding: 12px;
      border: 1px solid transparent;
      border-radius: 12px;
      background-color: @component-background;
      cursor: pointer;
      transition: border-color 0.2s;

      &:hover {
        border-color: fade(@primary-color, 40%);
      }

      &.is-active {
        border-color: @primary-color;
      }
    }

    &__avatar {
      &--empty {
        background-color: fade(@primary-color, 12%);
        color: @primary-color;
        font-size: 20px;
        font-weight: 600;
      }
    }

    &__candidate-name {
      font-size: 16px;
      line-height: 1.4;
      color: @text-color-base;
    }
  }
</style>
