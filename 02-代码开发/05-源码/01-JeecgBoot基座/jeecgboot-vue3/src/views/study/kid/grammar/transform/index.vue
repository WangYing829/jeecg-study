<template>
  <StudyLayout module-title="句子变形器">
    <div class="study-kid-grammar-transform">
      <ChildSwitcher class="study-kid-grammar-transform__child" />

      <!-- 断网：可读提示 + 重试入口（F-BASE-08） -->
      <a-alert
        v-if="netError"
        class="study-kid-grammar-transform__net-error"
        type="warning"
        show-icon
        :message="netError.message"
      >
        <template #action>
          <a-button size="small" @click="onRetry">重试</a-button>
        </template>
      </a-alert>

      <a-spin :spinning="loading" class="study-kid-grammar-transform__spin">
        <!-- 输入：中文意思 -->
        <section class="study-kid-grammar-transform__task">
          <label class="study-kid-grammar-transform__label" for="study-kid-grammar-transform-input">
            想说的中文意思
          </label>
          <a-textarea
            id="study-kid-grammar-transform-input"
            v-model:value="zhMeaning"
            class="study-kid-grammar-transform__input"
            :rows="3"
            placeholder="例如：我昨天去了公园"
          />
        </section>

        <!-- 结果：五种形态；讲解不可用时给提示，不给空卡片 -->
        <template v-if="result">
          <p v-if="result.hint" class="study-kid-grammar-transform__hint">{{ result.hint }}</p>

          <ul v-if="variants.length > 0" class="study-kid-grammar-transform__list">
            <li
              v-for="(item, index) in variants"
              :key="index"
              class="study-kid-grammar-transform__card"
              :class="{ 'is-wrong': item.wrong }"
            >
              <div class="study-kid-grammar-transform__card-head">
                <span class="study-kid-grammar-transform__card-name">{{ item.name || item.kind }}</span>
                <span v-if="item.wrong" class="study-kid-grammar-transform__card-flag">这样写不对</span>
              </div>
              <p class="study-kid-grammar-transform__card-text">{{ item.text }}</p>
              <p v-if="item.usage" class="study-kid-grammar-transform__card-usage">{{ item.usage }}</p>
            </li>
          </ul>

          <a-empty
            v-else
            class="study-kid-grammar-transform__empty"
            description="暂时没有可用的变形结果"
          />
        </template>
      </a-spin>
    </div>

    <template #footer>
      <a-button
        type="primary"
        class="study-kid-grammar-transform__primary"
        :loading="submitting"
        :disabled="!canSubmit"
        @click="onSubmit"
      >
        {{ result ? '再变一次' : '变一下' }}
      </a-button>
    </template>
  </StudyLayout>
</template>

<script lang="ts">
  import { computed, defineComponent, onMounted, ref } from 'vue';
  import { useRoute } from 'vue-router';
  import ChildSwitcher from '/@/components/study/ChildSwitcher.vue';
  import { useMessage } from '/@/hooks/web/useMessage';
  import StudyLayout from '/@/layouts/study/index.vue';
  import {
    transformGrammarSentence,
    type StudyGrammarTransformResultVO,
    type StudyGrammarTransformVO,
  } from '../../../api';
  import { describeNetError, OFFLINE_HINT, RETRY_LABEL, type StudyNetError } from '../../../net';

  /**
   * 学习端句子变形器页（设计规范 §10.2）。
   *
   * 一屏一任务：写下中文意思 → 拿到几种说法。
   * 变形结果由服务端的规则产出，错误示范会单独标出来（§10.3 的中性表述）。
   */
  export default defineComponent({
    name: 'StudyKidGrammarTransformPage',
    components: { ChildSwitcher, StudyLayout },
    setup() {
      const route = useRoute();
      const { createMessage } = useMessage();

      const zhMeaning = ref('');
      const result = ref<StudyGrammarTransformResultVO | null>(null);
      const loading = ref(false);
      const submitting = ref(false);
      /** 断网提示与重试入口（F-BASE-08） */
      const netError = ref<StudyNetError | null>(null);
      /** 重试动作：断网后点重试重放上一次失败的服务端请求 */
      const retryAction = ref<null | (() => Promise<void>)>(null);

      /** 语法点业务ID：从语法详情页带过来 */
      const grammarCode = computed(() => String(route.query.grammarCode || ''));

      /** 变形结果里的各种形态 */
      const variants = computed<StudyGrammarTransformVO[]>(() => result.value?.variants || []);

      /** 能不能提交 */
      const canSubmit = computed(() => Boolean(zhMeaning.value.trim()) && !submitting.value);

      /** 提交变形 */
      const onSubmit = async () => {
        const text = zhMeaning.value.trim();
        if (!text) {
          return;
        }
        submitting.value = true;
        try {
          const data = await transformGrammarSentence({
            zhMeaning: text,
            grammarCode: grammarCode.value || undefined,
          });
          result.value = data || null;
          netError.value = null;
        } catch (e) {
          result.value = null;
          // F-BASE-08：断网给可读提示与重试入口，业务错误保留原来的提示语义
          const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
          netError.value = state.offline ? state : null;
          if (state.offline) {
            retryAction.value = () => onSubmit();
          } else {
            createMessage.warning('这次变形没成功，再试一次');
          }
        } finally {
          submitting.value = false;
        }
      };

      /** 断网后点重试：重放上一次失败的服务端请求 */
      const onRetry = async () => {
        const action = retryAction.value;
        retryAction.value = null;
        netError.value = null;
        if (action) {
          await action();
        }
      };

      onMounted(() => {
        loading.value = false;
      });

      return {
        zhMeaning,
        result,
        variants,
        grammarCode,
        canSubmit,
        loading,
        submitting,
        netError,
        onSubmit,
        onRetry,
      };
    },
  });
</script>

<style lang="less">
  .study-kid-grammar-transform {
    padding: 16px;

    &__child {
      margin-bottom: 12px;
    }

    &__spin {
      display: block;
      min-height: 240px;
    }

    &__task {
      padding: 16px;
      border: 1px solid @border-color-light;
      border-radius: 12px;
      background-color: @app-content-background;
    }

    &__label {
      display: block;
      font-size: 16px;
      font-weight: 600;
      line-height: 1.4;
      color: @text-color-base;
    }

    &__input {
      margin-top: 8px;
      font-size: 16px;
      line-height: 1.5715;
    }

    &__hint {
      margin: 16px 0 0;
      font-size: 14px;
      line-height: 1.5715;
      color: @text-color-call-out;
    }

    &__list {
      margin: 12px 0 0;
      padding: 0;
      list-style: none;
      display: flex;
      flex-direction: column;
      gap: 12px;
    }

    &__card {
      padding: 12px 16px;
      border: 1px solid @border-color-light;
      border-radius: 12px;
      background-color: @app-content-background;

      /* 错误示范用柔和底色，不用大面积纯红（F-22） */
      &.is-wrong {
        background-color: @danger-background-color;
      }
    }

    &__card-head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
    }

    &__card-name {
      font-size: 16px;
      font-weight: 600;
      line-height: 1.4;
      color: @text-color-base;
    }

    &__card-flag {
      flex: 0 0 auto;
      padding: 4px 8px;
      border-radius: 4px;
      font-size: 12px;
      color: @text-color-call-out;
      background-color: @white;
    }

    &__card-text {
      margin: 8px 0 0;
      font-size: 18px;
      line-height: 1.5715;
      color: @text-color-base;
      word-break: break-word;
    }

    &__card-usage {
      margin: 4px 0 0;
      font-size: 14px;
      line-height: 1.5715;
      color: @text-color-call-out;
    }

    &__empty {
      padding: 24px 0;
    }

    /* 断网提示条（F-BASE-08） */
    &__net-error {
      margin-bottom: 16px;
    }

    &__primary {
      width: 100%;
      min-height: 44px;
      font-size: 18px;
      border-radius: 8px;
    }

    @media (max-width: 992px) {
      padding: 12px;
    }
  }
</style>
