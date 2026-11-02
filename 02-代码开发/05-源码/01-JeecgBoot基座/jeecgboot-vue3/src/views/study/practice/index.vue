<template>
  <StudyLayout module-title="练习" :streak-days="streakDays">
    <div class="study-practice">
      <!-- 断网：可读提示 +「重试」入口（F-BASE-08：V0 不做离线补交，只重试） -->
      <a-alert
        v-if="netError"
        class="study-practice__net-error"
        type="warning"
        show-icon
        :message="netError.message"
      >
        <template #action>
          <a-button size="small" @click="onRetry">重试</a-button>
        </template>
      </a-alert>

      <a-spin :spinning="loading" class="study-practice__spin">
        <section v-if="question" class="study-practice__task">
          <div class="study-practice__meta">
            <span class="study-practice__type">{{ typeLabel }}</span>
            <span v-if="question.difficulty" class="study-practice__difficulty">难度 {{ question.difficulty }} 级</span>
          </div>

          <h2 class="study-practice__stem">{{ question.stem }}</h2>

          <!-- 选择题：每个选项都是 44px 以上的点击目标（§10.1） -->
          <div v-if="options.length > 0" class="study-practice__options">
            <a-button
              v-for="opt in options"
              :key="opt.key"
              class="study-practice__option"
              :class="{ 'is-selected': selected === opt.key }"
              @click="selected = opt.key"
            >
              <span class="study-practice__option-key">{{ opt.key }}</span>
              <span class="study-practice__option-text">{{ opt.text }}</span>
            </a-button>
          </div>

          <!-- 填空 / 数值题：按服务端给的作答入口形态输入 -->
          <div v-else class="study-practice__entry">
            <a-input
              v-model:value="filled"
              class="study-practice__input"
              :placeholder="question.answerEntry?.placeholder || '在这里写下你的答案'"
              :size="large"
            />
            <span v-if="question.answerEntry?.unit" class="study-practice__unit">{{ question.answerEntry.unit }}</span>
          </div>
        </section>

        <!-- 反馈区：只有作答之后才渲染；未作答时这里连 DOM 都不存在（R-01 / §10.6） -->
        <section v-if="result" class="study-practice__feedback" :class="result.correct ? 'is-right' : 'is-wrong'">
          <div class="study-practice__verdict">
            <Icon v-if="result.correct" icon="ant-design:check-circle" />
            <Icon v-else icon="ant-design:smile" />
            <span class="study-practice__verdict-text">{{ result.correct ? '对了' : '再看一次' }}</span>
            <!-- 判定来源必须显式标注（§10.3 强制规则 1） -->
            <span class="study-practice__source">{{ sourceLabel }}</span>
          </div>

          <!-- 判定依据可展开查看（§10.3 强制规则 2） -->
          <div class="study-practice__basis">
            <a-button type="link" class="study-practice__basis-toggle" @click="basisVisible = !basisVisible">
              {{ basisVisible ? '收起判定依据' : '看看判定依据' }}
            </a-button>
            <p v-if="basisVisible" class="study-practice__basis-text">{{ result.basis }}</p>
          </div>

          <!-- 讲解入口：只有点击后才去取讲解内容 -->
          <div class="study-practice__explain">
            <a-button type="link" class="study-practice__explain-toggle" @click="onLoadExplain">
              {{ explainText ? '收起讲解' : '看讲解' }}
            </a-button>
            <p v-if="explainText" class="study-practice__explain-text">{{ explainText }}</p>
          </div>

          <!-- 需要补错因时（服务端 4001）给出中性选项，不做负向表述 -->
          <div v-if="causeRequired" class="study-practice__cause">
            <p class="study-practice__cause-title">先看看是哪一步卡住了</p>
            <div class="study-practice__cause-list">
              <a-button
                v-for="item in causeOptions"
                :key="item"
                class="study-practice__cause-item"
                :class="{ 'is-selected': cause === item }"
                @click="onPickCause(item)"
              >
                {{ item }}
              </a-button>
            </div>
          </div>
        </section>

        <a-empty v-if="!loading && !question" class="study-practice__empty" description="暂时没有可做的题" />
      </a-spin>
    </div>

    <template #footer>
      <a-button
        type="primary"
        class="study-practice__submit"
        :loading="submitting"
        :disabled="!canSubmit"
        @click="onSubmit"
      >
        {{ result ? '再来一题' : '提交作答' }}
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
    fetchPracticeExplain,
    fetchPracticeQuestion,
    submitPracticeAnswer,
    type StudyAnswerResultVO,
    type StudyPracticeQuestionVO,
  } from '../api';
  import { describeNetError, OFFLINE_HINT, RETRY_LABEL, type StudyNetError } from '../net';

  /** 练习类型（与后端 moduleType 对齐） */
  const TYPES = [
    { value: 'word', label: '背单词' },
    { value: 'math', label: '数学' },
    { value: 'grammar', label: '英语语法' },
    { value: 'writing', label: '语文写作' },
    { value: 'wrong', label: '错题重做' },
  ];

  /** 错因候选（中性表述，不做负向评价） */
  const CAUSE_OPTIONS = ['概念不清', '计算失误', '审题偏差'];

  /**
   * 学习端练习页（设计规范 §10.2 / §10.3 / §10.6）。
   *
   * <p>红线要点：</p>
   * <ul>
   *   <li>取题接口不返回答案字段，未作答时<b>讲解区根本不进 DOM</b>（R-01）；</li>
   *   <li>判定来源必须显示，「按答案判定」/「AI 辅助讲解，判定以规则为准」（§10.3）；</li>
   *   <li>反馈文案保持中性，不出现负向表述（§10.3 强制规则 4）。</li>
   * </ul>
   */
  export default defineComponent({
    name: 'StudyPracticePage',
    components: { Icon, StudyLayout },
    setup() {
      const { createMessage } = useMessage();

      const type = ref('word');
      const question = ref<StudyPracticeQuestionVO | null>(null);
      const result = ref<StudyAnswerResultVO | null>(null);
      const selected = ref('');
      const filled = ref('');
      const explainText = ref('');
      const basisVisible = ref(false);
      const cause = ref('');
      const causeRequired = ref(false);
      const loading = ref(false);
      const submitting = ref(false);
      const streakDays = ref(0);
      /** 断网提示与重试入口（F-BASE-08） */
      const netError = ref<StudyNetError | null>(null);
      /** 重试动作：断网后点「重试」重放上一次失败的服务端请求（V0 不做断网补交） */
      const retryAction = ref<null | (() => Promise<void>)>(null);

      const typeLabel = computed(() => TYPES.find((t) => t.value === type.value)?.label || '练习');
      const sourceLabel = computed(() =>
        result.value?.source === 'rule+model' ? 'AI 辅助讲解，判定以规则为准' : '按答案判定',
      );

      const options = computed(() => {
        const raw = question.value?.options;
        if (!raw) {
          return [];
        }
        try {
          const parsed = JSON.parse(raw);
          const list = Array.isArray(parsed) ? parsed : parsed?.options || [];
          return (list as any[]).map((item: any, index: number) => ({
            key: String(item?.key ?? item?.label ?? String.fromCharCode(65 + index)),
            text: String(item?.text ?? item?.label ?? item),
          }));
        } catch (e) {
          return [];
        }
      });

      const canSubmit = computed(() => {
        if (!question.value || submitting.value) {
          return false;
        }
        return options.value.length > 0 ? Boolean(selected.value) : Boolean(filled.value.trim());
      });

      const loadQuestion = async () => {
        loading.value = true;
        result.value = null;
        explainText.value = '';
        basisVisible.value = false;
        causeRequired.value = false;
        cause.value = '';
        selected.value = '';
        filled.value = '';
        try {
          question.value = await fetchPracticeQuestion(type.value);
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

      const onLoadExplain = async () => {
        if (explainText.value) {
          explainText.value = '';
          return;
        }
        if (!question.value) {
          return;
        }
        try {
          const detail = await fetchPracticeExplain(question.value.contentId);
          explainText.value = detail?.text || '暂时没有更多讲解';
        } catch (e) {
          explainText.value = '暂时没有更多讲解';
        }
      };

      const onPickCause = async (value: string) => {
        cause.value = value;
        causeRequired.value = false;
        await onSubmit();
      };

      const onSubmit = async () => {
        if (result.value) {
          await loadQuestion();
          return;
        }
        if (!question.value || !canSubmit.value) {
          return;
        }
        submitting.value = true;
        try {
          const payload: Record<string, any> = {
            contentId: question.value.contentId,
            answer: options.value.length > 0 ? selected.value : filled.value.trim(),
          };
          if (cause.value) {
            payload.errorCause = cause.value;
          }
          result.value = await submitPracticeAnswer(payload);
          causeRequired.value = false;
        } catch (e: any) {
          const text = String(e?.message || '');
          // F-BASE-08：断网 → 可读提示 +「重试」入口；已写的答案保留，不做本地补交
          const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
          netError.value = state.offline ? state : null;
          if (state.offline) {
            retryAction.value = () => onSubmit();
          } else if (text.includes('错因')) {
            causeRequired.value = true;
          } else {
            createMessage.warning('这次提交没成功，再试一次');
          }
        } finally {
          submitting.value = false;
        }
      };

      onMounted(() => {
        loadQuestion();
      });

      return {
        TYPES,
        CAUSE_OPTIONS,
        type,
        typeLabel,
        question,
        result,
        selected,
        filled,
        explainText,
        basisVisible,
        cause,
        causeRequired,
        loading,
        submitting,
        streakDays,
        options,
        canSubmit,
        sourceLabel,
        netError,
        loadQuestion,
        onRetry,
        onLoadExplain,
        onPickCause,
        onSubmit,
      };
    },
  });
</script>

<style lang="less">
  .study-practice {
    padding: 16px;

    &__spin {
      display: block;
      min-height: 240px;
    }

    &__meta {
      display: flex;
      align-items: center;
      gap: 16px;
      font-size: 14px;
      line-height: 1.4;
      color: @text-color-call-out;
    }

    &__stem {
      margin: 12px 0 0;
      font-size: 18px;
      font-weight: 500;
      line-height: 1.4;
      color: @text-color-base;
    }

    &__options {
      display: flex;
      flex-direction: column;
      gap: 12px;
      margin-top: 16px;
    }

    &__option {
      display: flex;
      align-items: center;
      gap: 12px;
      width: 100%;
      min-height: 44px;
      padding: 10px 16px;
      font-size: 16px;
      text-align: left;
      border-radius: 8px;

      &.is-selected {
        border: 1px solid @primary-color;
        color: @primary-color;
      }
    }

    &__option-key {
      flex: 0 0 auto;
      font-weight: 600;
    }

    &__option-text {
      flex: 1 1 auto;
      min-width: 0;
      word-break: break-word;
    }

    &__entry {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-top: 16px;
    }

    &__input {
      flex: 1 1 auto;
      min-width: 0;
      min-height: 44px;
      font-size: 16px;
    }

    &__unit {
      flex: 0 0 auto;
      font-size: 16px;
      color: @text-color-call-out;
    }

    /* 反馈区：柔和底色，不用大面积纯红（§10.3 / F-22） */
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

    &__basis,
    &__explain {
      margin-top: 8px;
    }

    &__basis-text,
    &__explain-text {
      margin: 4px 0 0;
      font-size: 14px;
      line-height: 1.5715;
      color: @text-color-base;
    }

    &__cause {
      margin-top: 12px;
    }

    &__cause-title {
      margin: 0;
      font-size: 14px;
      line-height: 1.5715;
      color: @text-color-call-out;
    }

    &__cause-list {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      margin-top: 8px;
    }

    &__cause-item {
      min-height: 44px;
      font-size: 14px;
      border-radius: 8px;

      &.is-selected {
        border: 1px solid @primary-color;
        color: @primary-color;
      }
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
