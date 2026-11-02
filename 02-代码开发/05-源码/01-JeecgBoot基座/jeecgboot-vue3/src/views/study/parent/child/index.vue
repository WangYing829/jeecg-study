<template>
  <StudyParentShell :child-name="childName" :child-code="childCode" :child-options="childOptions" @switch-child="onSwitch">
    <a-spin :spinning="loading">
      <!-- 断网：可读提示 +「重试」入口（F-BASE-08：断网不能伪装成「档案不存在」） -->
      <a-alert
        v-if="netError"
        class="study-parent-child__error"
        type="warning"
        show-icon
        :message="netError.message"
      >
        <template #action>
          <a-button size="small" @click="onRetry">重试</a-button>
        </template>
      </a-alert>

      <!-- 越权（4010）：统一口径，不换成「数据不存在」（§11.3） -->
      <a-alert
        v-else-if="forbidden || detailForbidden"
        class="study-parent-child__error"
        type="warning"
        show-icon
        message="无权查看该孩子的数据"
      />

      <a-alert v-else-if="notFound" class="study-parent-child__error" type="warning" show-icon message="找不到这个孩子的档案" />

      <template v-else>
        <!-- 孩子信息条 -->
        <section class="study-parent-child__section">
          <div class="study-parent-child__head">
            <a-avatar
              v-if="form.avatar"
              class="study-parent-child__avatar"
              :size="56"
              :src="form.avatar"
              :alt="form.nickname"
            />
            <a-avatar v-else class="study-parent-child__avatar study-parent-child__avatar--empty" :size="56">
              {{ (form.nickname || '?').slice(0, 1) }}
            </a-avatar>
            <div class="study-parent-child__head-text">
              <div class="study-parent-child__name">{{ form.nickname || '未命名' }}</div>
              <div class="study-parent-child__code">{{ routeCode }}</div>
            </div>
          </div>
        </section>

        <!-- 档案表单：窄表单用 vertical（§8.4②） -->
        <section class="study-parent-child__section">
          <a-form :layout="vertical">
            <a-row :gutter="16">
              <a-col :span="12">
                <a-form-item label="昵称">
                  <a-input v-model:value="form.nickname" :maxlength="32" placeholder="孩子的昵称" />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="性别">
                  <a-select v-model:value="form.gender" :options="genderOptions" />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="生日">
                  <a-date-picker v-model:value="form.birthday" value-format="YYYY-MM-DD" placeholder="可选" style="width: 100%" />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="年级">
                  <a-select v-model:value="form.gradeCode" :options="gradeOptions" allow-clear placeholder="可选" />
                </a-form-item>
              </a-col>
              <a-col :span="24">
                <a-form-item label="头像">
                  <a-input v-model:value="form.avatar" placeholder="图片地址（可选）" />
                </a-form-item>
              </a-col>
            </a-row>
          </a-form>

          <div class="study-parent-child__actions">
            <a-button type="primary" :loading="submitting" @click="onSave">保存</a-button>
            <a-button @click="openPinModal">重置 PIN</a-button>
            <a-button v-if="routeCode" @click="goRelations">关联管理</a-button>
            <a-popconfirm
              title="停用后这个孩子将不能登录，数据会保留，确定吗？"
              ok-text="确定"
              cancel-text="取消"
              @confirm="onDisable"
            >
              <a-button danger>停用该孩子</a-button>
            </a-popconfirm>
          </div>
        </section>
      </template>
    </a-spin>

    <!-- PIN 重置弹窗：明文只此一次 -->
    <a-modal
      v-model:open="pinModalOpen"
      title="重置 PIN"
      :footer="null"
      :mask-closable="false"
      :body-style="STUDY_MODAL_BODY_STYLE"
    >
      <div class="study-parent-child__secret">
        <div class="study-parent-child__secret-value">{{ newPin }}</div>
        <p class="study-parent-child__secret-hint">{{ ONE_TIME_HINT }}</p>
      </div>
    </a-modal>
  </StudyParentShell>
</template>

<script lang="ts">
  import { computed, defineComponent, onMounted, ref, watch } from 'vue';
  import { useRoute, useRouter } from 'vue-router';
  import { message } from 'ant-design-vue';
  import StudyParentShell from '/@/components/study/StudyParentShell.vue';
  import { isForbidden, useParentChild } from '../useParentChild';
  import { describeNetError, OFFLINE_HINT, RETRY_LABEL, type StudyNetError } from '../../net';
  import {
    GENDER_OPTIONS,
    GRADE_OPTIONS,
    ONE_TIME_HINT,
    disableChild,
    fetchChildDetail,
    resetChildPin,
    updateChild,
  } from '../familyApi';

  /** 弹窗正文样式（设计规范 §8.5） */
  const STUDY_MODAL_BODY_STYLE = { paddingTop: '12px' };

  /**
   * 家长端「孩子档案编辑」页（设计规范 §5.5）。
   *
   * <p>独立表单页（非列表页）：窄表单用 {@code layout="vertical"}，
   * 短字段 {@code :span="12"} / 长文本 {@code :span="24"}（§8.4②）。</p>
   *
   * <p><b>红线口径</b>：性别、生日、年级都<b>可选</b>，
   * 且<b>不参与任何对外比较</b>——本页面只做档案维护，不产出任何比较类指标（R-04）。</p>
   */
  export default defineComponent({
    name: 'StudyParentChildProfilePage',
    components: { StudyParentShell },
    setup() {
      const route = useRoute();
      const router = useRouter();

      const form = ref<{ nickname: string; avatar: string; gender: number; birthday: string; gradeCode: string }>({
        nickname: '',
        avatar: '',
        gender: 0,
        birthday: '',
        gradeCode: '',
      });
      const loading = ref(false);
      const submitting = ref(false);
      const notFound = ref(false);
      /** 断网错误态（F-BASE-08：断网不能伪装成「档案不存在」） */
      const netError = ref<StudyNetError | null>(null);
      /** 档案本身的越权（4010），与「取不到档案」区分开 */
      const detailForbidden = ref(false);
      const pinModalOpen = ref(false);
      const newPin = ref('');

      const { childCode, childName, childOptions, forbidden, loadChildren } = useParentChild((state) => {
        // 孩子列表取不到：断网给可读提示与重试入口（F-BASE-08）
        netError.value = state.offline ? state : null;
      });

      /**
       * 路由上的孩子业务 id。
       *
       * <p>没有 {@code :code} 时用<b>顶部条当前选中的孩子</b>——
       * 家长端外壳自带孩子切换条，「当前孩子」是这一层的既有语义，
       * 档案页直接沿用，不额外造一套选择器。</p>
       */
      const routeCode = computed(() => String(route.params.code || childCode.value || ''));

      const genderOptions = GENDER_OPTIONS;
      const gradeOptions = GRADE_OPTIONS;

      const load = async () => {
        const code = routeCode.value;
        if (!code) {
          notFound.value = true;
          return;
        }
        loading.value = true;
        notFound.value = false;
        netError.value = null;
        detailForbidden.value = false;
        try {
          const detail = await fetchChildDetail(code);
          form.value = {
            nickname: detail?.nickname || '',
            avatar: detail?.avatar || '',
            gender: detail?.gender ?? 0,
            birthday: detail?.birthday || '',
            gradeCode: detail?.gradeCode || '',
          };
        } catch (e) {
          // 三种失败要分开说：断网 / 越权 / 真的没有这孩子（§11.3 + F-BASE-08）
          const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
          if (state.offline) {
            netError.value = state;
          } else if (isForbidden(e)) {
            detailForbidden.value = true;
          } else {
            notFound.value = true;
          }
        } finally {
          loading.value = false;
        }
      };

      const onRetry = async () => {
        await load();
      };

      const onSwitch = (value: string) => {
        childCode.value = value;
      };

      /** 保存档案 */
      const onSave = async () => {
        const code = routeCode.value;
        if (!code) {
          return;
        }
        submitting.value = true;
        try {
          await updateChild(code, {
            nickname: form.value.nickname.trim(),
            avatar: form.value.avatar.trim(),
            gender: form.value.gender,
            birthday: form.value.birthday || undefined,
            gradeCode: form.value.gradeCode || undefined,
          });
          message.success('已保存');
          await load();
        } catch (e) {
          message.error(String(e?.message || '保存失败'));
        } finally {
          submitting.value = false;
        }
      };

      /** 重置 PIN：明文只此一次 */
      const openPinModal = async () => {
        const code = routeCode.value;
        if (!code) {
          return;
        }
        try {
          const pin = await resetChildPin(code);
          newPin.value = pin || '';
          pinModalOpen.value = true;
        } catch (e) {
          message.error(String(e?.message || '重置失败'));
        }
      };

      /** 停用孩子 */
      const onDisable = async () => {
        const code = routeCode.value;
        if (!code) {
          return;
        }
        try {
          await disableChild(code);
          message.success('已停用');
          await load();
        } catch (e) {
          message.error(String(e?.message || '操作失败'));
        }
      };

      /** 进入该孩子的关联管理页（路径带 code，页面据此定位孩子） */
      const goRelations = () => {
        const code = routeCode.value;
        if (!code) {
          return;
        }
        router.push(`/study-parent/child/${code}/relations`);
      };

      watch(routeCode, () => {
        load();
      });

      onMounted(async () => {
        await loadChildren();
        await load();
      });

      return {
        childCode,
        childName,
        childOptions,
        form,
        loading,
        submitting,
        notFound,
        netError,
        forbidden,
        detailForbidden,
        pinModalOpen,
        newPin,
        routeCode,
        genderOptions,
        gradeOptions,
        STUDY_MODAL_BODY_STYLE,
        ONE_TIME_HINT,
        onRetry,
        onSwitch,
        onSave,
        openPinModal,
        goRelations,
        onDisable,
      };
    },
  });
</script>

<style lang="less">
  .study-parent-child {
    &__section {
      margin-bottom: 16px;
    }

    &__head {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 16px;
      border-radius: 8px;
      background-color: @component-background;
    }

    &__avatar {
      &--empty {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        background-color: fade(@primary-color, 12%);
        color: @primary-color;
        font-size: 20px;
        font-weight: 600;
      }
    }

    &__head-text {
      min-width: 0;
    }

    &__name {
      font-size: 16px;
      font-weight: 600;
      line-height: 1.4;
      color: @text-color-base;
    }

    &__code {
      font-size: 13px;
      color: @text-color-call-out;
    }

    &__actions {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      margin-top: 16px;
    }

    &__error {
      margin-bottom: 16px;
    }

    &__secret {
      text-align: center;
    }

    &__secret-value {
      margin-bottom: 12px;
      padding: 16px;
      border-radius: 8px;
      background-color: fade(@primary-color, 8%);
      font-size: 20px;
      font-weight: 600;
      letter-spacing: 6px;
      color: @primary-color;
    }

    &__secret-hint {
      margin: 0;
      font-size: 14px;
      line-height: 1.5715;
      color: @text-color-call-out;
    }
  }
</style>
