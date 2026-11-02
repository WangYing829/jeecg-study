<template>
  <StudyLayout module-title="语文写作">
    <div class="study-writing">
      <!-- 断网：可读提示 +「重试」入口（F-BASE-08：V0 不做离线补交，只重试） -->
      <a-alert
        v-if="netError"
        class="study-writing__net-error"
        type="warning"
        show-icon
        :message="netError.message"
      >
        <template #action>
          <a-button size="small" @click="onRetry">重试</a-button>
        </template>
      </a-alert>

      <a-spin :spinning="loading" class="study-writing__spin">
        <section v-if="question" class="study-writing__task">
          <h2 class="study-writing__stem">{{ question.stem }}</h2>

          <!-- 第一步：先列提纲（写作未过提纲不允许提交初稿，服务端 4002） -->
          <div v-if="!outlineDone" class="study-writing__step">
            <h3 class="study-writing__step-title">先列三个提纲</h3>
            <p class="study-writing__step-hint">想清楚要写哪三块，再动笔会顺很多</p>
            <a-input
              v-for="(item, index) in outline"
              :key="index"
              v-model:value="outline[index]"
              class="study-writing__outline"
              :placeholder="`第 ${index + 1} 块写什么`"
              :size="large"
            />
          </div>

          <!-- 第二步：写初稿 -->
          <div v-else class="study-writing__step">
            <h3 class="study-writing__step-title">按提纲写初稿</h3>
            <p class="study-writing__step-hint">提纲：{{ outlineText }}</p>
            <a-input
              v-model:value="draft"
              class="study-writing__draft"
              type="textarea"
              :rows="8"
              placeholder="在这里写下你的作文"
            />
          </div>

          <section v-if="result" class="study-writing__feedback" :class="result.correct ? 'is-right' : 'is-wrong'">
            <div class="study-writing__verdict">
              <Icon v-if="result.correct" icon="ant-design:check-circle" />
              <Icon v-else icon="ant-design:smile" />
              <span class="study-writing__verdict-text">{{ result.correct ? '写下来了' : '再改一改' }}</span>
              <span class="study-writing__source">{{ sourceLabel }}</span>
            </div>
            <p v-if="result.basis" class="study-writing__basis">{{ result.basis }}</p>
          </section>
        </section>

        <a-empty v-if="!loading && !question" class="study-writing__empty" description="暂时没有写作题" />
      </a-spin>
    </div>

    <template #footer>
      <a-button type="primary" class="study-writing__submit" :loading="submitting" :disabled="!canSubmit" @click="onSubmit">
        {{ outlineDone ? '提交初稿' : '下一步：写初稿' }}
      </a-button>
    </template>
  </StudyLayout>
</template>

<script lang="ts">
  import { computed, defineComponent, onMounted, ref } from 'vue';
  import { Icon } from '/@/components/Icon';
  import { useMessage } from '/@/hooks/web/useMessage';
  import StudyLayout from '/@/layouts/study/index.vue';
  import {
    fetchPracticeQuestion,
    submitPracticeAnswer,
    type StudyAnswerResultVO,
    type StudyPracticeQuestionVO,
  } from '../api';
  import { describeNetError, OFFLINE_HINT, RETRY_LABEL, type StudyNetError } from '../net';

  /**
   * 学习端语文写作页（设计规范 §10.2）。
   *
   * <p>两步流程：<b>先列提纲</b> → <b>再写初稿</b>。
   * 服务端在提纲未完成时拒绝提交初稿（4002），页面与后端保持同一口径。</p>
   */
  export default defineComponent({
    name: 'StudyWritingPage',
    components: { Icon, StudyLayout },
    setup() {
      const { createMessage } = useMessage();

      const question = ref<StudyPracticeQuestionVO | null>(null);
      const outline = ref<string[]>(['', '', '']);
      const outlineDone = ref(false);
      const draft = ref('');
      const result = ref<StudyAnswerResultVO | null>(null);
      const loading = ref(false);
      const submitting = ref(false);
      /** 断网提示与重试入口（F-BASE-08） */
      const netError = ref<StudyNetError | null>(null);
      /** 重试动作：断网后点「重试」重放上一次失败的服务端请求（V0 不做断网补交） */
      const retryAction = ref<null | (() => Promise<void>)>(null);

      const outlineText = computed(() => outline.value.filter((item) => item.trim()).join(' / '));
      const sourceLabel = computed(() =>
        result.value?.source === 'rule+model' ? 'AI 辅助讲解，判定以规则为准' : '按答案判定',
      );
      const canSubmit = computed(() => {
        if (outlineDone.value) {
          return Boolean(draft.value.trim());
        }
        return outline.value.filter((item) => item.trim()).length >= 3;
      });

      const loadQuestion = async () => {
        loading.value = true;
        outline.value = ['', '', ''];
        outlineDone.value = false;
        draft.value = '';
        result.value = null;
        try {
          question.value = await fetchPracticeQuestion('writing');
          netError.value = null;
        } catch (e) {
          question.value = null;
          // F-BASE-08：断网 → 可读提示 +「重试」入口；业务错误 → 保留原来的提示语义
          const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
          netError.value = state.offline ? state : null;
          if (state.offline) {
            retryAction.value = () => loadQuestion();
          } else {
            createMessage.warning('题目没取到，稍后再看看');
          }
        } finally {
          loading.value = false;
        }
        // 写作情境页带过来的经历：从 history.state 取（正文不塞进 URL），
        // 直接落到草稿里并跳过提纲这一步——孩子已经写过了，不必再写一遍。
        const prefill = (window.history.state as { experience?: string } | null)?.experience;
        if (typeof prefill === 'string' && prefill.trim()) {
          draft.value = prefill.trim();
          outlineDone.value = true;
        }
      };

      const onSubmit = async () => {
        if (!question.value || !canSubmit.value) {
          return;
        }
        if (!outlineDone.value) {
          outlineDone.value = true;
          return;
        }
        submitting.value = true;
        try {
          result.value = await submitPracticeAnswer({
            contentId: question.value.contentId,
            answer: draft.value.trim(),
            contentType: 'writing',
          });
        } catch (e) {
          // F-BASE-08：断网 → 可读提示 +「重试」入口；已写的正文保留，不做本地判分
          const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
          netError.value = state.offline ? state : null;
          if (state.offline) {
            retryAction.value = () => onSubmit();
          } else {
            createMessage.warning('这次提交没成功，再试一次');
          }
        } finally {
          submitting.value = false;
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

      onMounted(() => {
        loadQuestion();
      });

      return {
        question,
        outline,
        outlineDone,
        draft,
        result,
        loading,
        submitting,
        outlineText,
        sourceLabel,
        canSubmit,
        netError,
        loadQuestion,
        onRetry,
        onSubmit,
      };
    },
  });
</script>

<style lang="less">
  .study-writing {
    padding: 16px;

    &__spin {
      display: block;
      min-height: 240px;
    }

    &__stem {
      margin: 0;
      font-size: 18px;
      font-weight: 500;
      line-height: 1.4;
      color: @text-color-base;
    }

    &__step {
      margin-top: 16px;
    }

    &__step-title {
      margin: 0;
      font-size: 16px;
      font-weight: 600;
      line-height: 1.4;
      color: @text-color-base;
    }

    &__step-hint {
      margin: 4px 0 12px;
      font-size: 14px;
      line-height: 1.5715;
      color: @text-color-call-out;
    }

    &__outline {
      min-height: 44px;
      margin-bottom: 12px;
      font-size: 16px;
    }

    &__draft {
      font-size: 16px;
      line-height: 1.5715;
    }

    &__feedback {
      margin-top: 16px;
      padding: 12px 16px;
      border: 1px solid @border-color-light;
      border-radius: 8px;
      background-color: @app-content-background;

      &.is-right {
        background-color: @success-background-color;
      }

      &.is-wrong {
        background-color: @danger-background-color;
      }
    }

    &__verdict {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 16px;
      font-weight: 600;
      line-height: 1.4;
      color: @text-color-base;
    }

    &__source {
      padding: 4px 8px;
      border-radius: 4px;
      font-size: 12px;
      font-weight: 400;
      color: @text-color-call-out;
      background-color: @white;
    }

    &__basis {
      margin: 8px 0 0;
      font-size: 14px;
      line-height: 1.5715;
      color: @text-color-base;
    }

    &__empty {
      padding: 24px 0;
    }

    /* 断网提示条（F-BASE-08） */
    &__net-error {
      margin-bottom: 16px;
    }

    &__submit {
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
