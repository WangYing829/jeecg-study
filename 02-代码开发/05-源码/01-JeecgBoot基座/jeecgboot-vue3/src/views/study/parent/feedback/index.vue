<template>
  <StudyParentShell
    :child-name="childName"
    :child-code="childCode"
    :child-options="childOptions"
    @switch-child="onSwitch"
  >
    <div class="study-parent-feedback">
      <!-- 断网：可读提示 +「重试」入口（F-BASE-08：V0 不做离线补交，只重试） -->
      <a-alert
        v-if="netError"
        class="study-parent-feedback__net-error"
        type="warning"
        show-icon
        :message="netError.message"
      >
        <template #action>
          <a-button size="small" @click="onRetry">重试</a-button>
        </template>
      </a-alert>

      <section class="study-parent-feedback__section">
        <h2 class="study-parent-feedback__section-title">一句话反馈</h2>
        <p class="study-parent-feedback__section-desc">选一篇最近的作文，用一句话说清楚哪里需要调整</p>
        <a-select
          v-model:value="targetCode"
          class="study-parent-feedback__target"
          :loading="loadingDrafts"
          :options="draftOptions"
          placeholder="选择一篇最近的作文"
        />
        <a-input
          v-model:value="content"
          class="study-parent-feedback__input"
          type="textarea"
          :rows="4"
          :maxlength="100"
          :disabled="!targetCode"
          placeholder="例如：开头写得挺稳，可以把第二段再写细一点"
        />
        <div class="study-parent-feedback__actions">
          <a-button class="study-parent-feedback__submit" :loading="submitting" :disabled="!canSubmit" @click="onSubmit">
            提交反馈
          </a-button>
        </div>
        <p v-if="!loadingDrafts && !draftOptions.length" class="study-parent-feedback__empty">
          这个孩子还没有提交过作文，作文提交后就能在这里留言
        </p>
      </section>

      <section class="study-parent-feedback__section">
        <h2 class="study-parent-feedback__section-title">反馈须知</h2>
        <ul class="study-parent-feedback__notes">
          <li class="study-parent-feedback__note">反馈只针对当前孩子，不会出现在其他人的页面上</li>
          <li class="study-parent-feedback__note">提交后由带教老师统一处理，处理结果会在周报里体现</li>
          <li class="study-parent-feedback__note">页面上的数据来自孩子自己的学习记录，只做客观呈现</li>
        </ul>
      </section>
    </div>
  </StudyParentShell>
</template>

<script lang="ts">
  import { computed, defineComponent, onMounted, ref, watch } from 'vue';
  import { useMessage } from '/@/hooks/web/useMessage';
  import StudyParentShell from '/@/components/study/StudyParentShell.vue';
  import { fetchParentWritingDrafts, submitParentFeedback } from '../../api';
  import { useParentChild } from '../useParentChild';
  import { describeNetError, OFFLINE_HINT, RETRY_LABEL, type StudyNetError } from '../../net';

  /**
   * 家长端一句话反馈页（设计规范 §9.6，需求 F-PAR-02）。
   *
   * <p>家长端唯一的写入口之一：提交一条<b>文字反馈</b>，不修改任何学习数据。
   * 文案不涉及人与人之间的说法（红线 R-04 / 自检清单 #30）。</p>
   *
   * <p>反馈按 ADR-025 落在<b>写作域</b>：家长先选一篇最近的作文，
   * 再对这一篇留一句话（需求 F-PAR-02：某篇作文 / 某次练习）。</p>
   */
  export default defineComponent({
    name: 'StudyParentFeedbackPage',
    components: { StudyParentShell },
    setup() {
      const { createMessage } = useMessage();
      const content = ref('');
      const submitting = ref(false);
      const loadingDrafts = ref(false);
      const targetCode = ref<string | undefined>(undefined);
      const drafts = ref<{ writingCode: string; title?: string }[]>([]);
      /** 断网提示与重试入口（F-BASE-08） */
      const netError = ref<StudyNetError | null>(null);
      /** 重试动作：断网后点「重试」重放上一次失败的服务端请求（V0 不做断网补交） */
      const retryAction = ref<null | (() => Promise<void>)>(null);

      const { childCode, childName, childOptions, loadChildren } = useParentChild((state) => {
        // 孩子列表没取到时同样给出可读提示与重试入口，不再静默失败（F-BASE-08）
        if (state.offline) {
          netError.value = state;
          retryAction.value = () => loadChildren();
        }
      });

      const draftOptions = computed(() =>
        drafts.value.map((item) => ({
          value: item.writingCode,
          label: item.title ? item.title : item.writingCode,
        }))
      );

      const canSubmit = computed(() => Boolean(targetCode.value) && Boolean(content.value.trim()));

      const loadDrafts = async () => {
        loadingDrafts.value = true;
        try {
          const list = (await fetchParentWritingDrafts(childCode.value)) || [];
          drafts.value = list as { writingCode: string; title?: string }[];
          // 默认选中最近的一篇
          targetCode.value = drafts.value.length ? drafts.value[0].writingCode : undefined;
          netError.value = null;
        } catch (e) {
          drafts.value = [];
          targetCode.value = undefined;
          // F-BASE-08：断网 → 可读提示 +「重试」入口；不再静默失败
          const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
          netError.value = state.offline ? state : null;
          if (state.offline) {
            retryAction.value = () => loadDrafts();
          }
        } finally {
          loadingDrafts.value = false;
        }
      };

      /** 断网后点「重试」：重放上一次失败的服务端请求 */
      const onRetry = async () => {
        const action = retryAction.value;
        retryAction.value = null;
        netError.value = null;
        if (action) {
          await action();
        }
      };

      const onSubmit = async () => {
        if (!canSubmit.value) {
          return;
        }
        submitting.value = true;
        try {
          await submitParentFeedback({
            targetCode: targetCode.value as string,
            content: content.value.trim(),
          });
          content.value = '';
          createMessage.success('反馈已提交，谢谢你的说明');
        } catch (e) {
          // F-BASE-08：断网 → 可读提示 +「重试」入口；已写的反馈保留，不重复提交
          const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
          netError.value = state.offline ? state : null;
          if (state.offline) {
            retryAction.value = () => onSubmit();
          } else {
            createMessage.warning('这次提交没成功，稍后再试');
          }
        } finally {
          submitting.value = false;
        }
      };

      const onSwitch = (value: string) => {
        childCode.value = value;
      };

      watch(childCode, () => {
        loadDrafts();
      });

      onMounted(async () => {
        await loadChildren();
        await loadDrafts();
      });

      return {
        childCode,
        childName,
        childOptions,
        content,
        submitting,
        loadingDrafts,
        targetCode,
        draftOptions,
        canSubmit,
        netError,
        onSubmit,
        onSwitch,
        onRetry,
      };
    },
  });
</script>

<style lang="less">
  .study-parent-feedback {
    &__section {
      margin-bottom: 16px;
      padding: 16px;
      border: 1px solid @border-color-light;
      border-radius: 6px;
      background-color: @app-content-background;
    }

    /* 断网提示条（F-BASE-08） */
    &__net-error {
      margin-bottom: 16px;
    }

    &__section-title {
      margin: 0;
      font-size: 15px;
      font-weight: 600;
      line-height: 1.4;
      color: @text-color-base;
    }

    &__section-desc {
      margin: 4px 0 12px;
      font-size: 14px;
      line-height: 1.5715;
      color: @text-color-call-out;
    }

    &__target {
      width: 100%;
      margin-bottom: 12px;
    }

    &__input {
      font-size: 14px;
      line-height: 1.5715;
    }

    &__actions {
      display: flex;
      justify-content: flex-end;
      margin-top: 12px;
    }

    &__submit {
      min-width: 96px;
    }

    &__empty {
      margin: 12px 0 0;
      font-size: 14px;
      line-height: 1.5715;
      color: @text-color-call-out;
    }

    &__notes {
      margin: 12px 0 0;
      padding-left: 16px;
      list-style: disc;
    }

    &__note {
      padding: 4px 0;
      font-size: 14px;
      line-height: 1.5715;
      color: @text-color-call-out;
    }
  }
</style>
