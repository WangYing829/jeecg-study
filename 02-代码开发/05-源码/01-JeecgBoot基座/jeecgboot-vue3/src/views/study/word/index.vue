<template>
  <StudyLayout module-title="背单词">
    <div class="study-word">
      <!-- 断网：可读提示 +「重试」入口（F-BASE-08：V0 不做离线补交，只重试） -->
      <a-alert
        v-if="netError"
        class="study-word__net-error"
        type="warning"
        show-icon
        :message="netError.message"
      >
        <template #action>
          <a-button size="small" @click="onRetry">重试</a-button>
        </template>
      </a-alert>

      <a-spin :spinning="loading" class="study-word__spin">
        <section v-if="word" class="study-word__task">
          <div class="study-word__meta">
            <span class="study-word__line">{{ lineLabel }}</span>
            <span v-if="word.pos" class="study-word__pos">{{ word.pos }}</span>
          </div>

          <h2 class="study-word__meaning">{{ word.meaning }}</h2>
          <p v-if="word.phonetic" class="study-word__phonetic">{{ word.phonetic }}</p>
          <p v-if="word.contextSentence" class="study-word__sentence">{{ word.contextSentence }}</p>

          <div class="study-word__entry">
            <a-input
              v-model:value="typed"
              class="study-word__input"
              placeholder="按意思把单词写出来"
              :size="large"
              @press-enter="onSubmit"
            />
          </div>

          <!-- 提交后的反馈：只有提交过才渲染 -->
          <section v-if="feedback" class="study-word__feedback" :class="feedback.correct ? 'is-right' : 'is-wrong'">
            <div class="study-word__verdict">
              <Icon v-if="feedback.correct" icon="ant-design:check-circle" />
              <Icon v-else icon="ant-design:smile" />
              <span class="study-word__verdict-text">{{ feedback.correct ? '对了' : '再看一次' }}</span>
            </div>
            <p v-if="feedback.basis" class="study-word__basis">{{ feedback.basis }}</p>
          </section>
        </section>

        <a-empty v-if="!loading && !word" class="study-word__empty" description="暂时没有要背的词" />
      </a-spin>
    </div>

    <template #footer>
      <a-button
        type="primary"
        class="study-word__submit"
        :loading="submitting"
        :disabled="!typed.trim()"
        @click="onSubmit"
      >
        {{ feedback ? '下一个词' : '提交' }}
      </a-button>
    </template>
  </StudyLayout>
</template>

<script lang="ts">
  import { computed, defineComponent, onMounted, ref } from 'vue';
  import { Icon } from '/@/components/Icon';
  import { useMessage } from '/@/hooks/web/useMessage';
  import StudyLayout from '/@/layouts/study/index.vue';
  import { fetchWordPractice, submitWordPractice, type StudyWordPracticeVO } from '../api';
  import { describeNetError, OFFLINE_HINT, RETRY_LABEL, type StudyNetError } from '../net';

  /**
   * 学习端背单词页（设计规范 §10.2）。
   *
   * <p>红线要点：服务端<b>不下发拼写</b>（拼写就是拼写题的答案），
   * 页面只展示释义与挖空例句，拼写由孩子自己写（红线 R-01）。</p>
   */
  export default defineComponent({
    name: 'StudyWordPage',
    components: { Icon, StudyLayout },
    setup() {
      const { createMessage } = useMessage();

      /** 线别：课内（IN）。V1 课内线优先，课外线属于 V3 范围 */
      const LINE_TYPE = 'IN';

      const word = ref<StudyWordPracticeVO | null>(null);
      const typed = ref('');
      const feedback = ref<{ correct?: boolean; basis?: string } | null>(null);
      const loading = ref(false);
      const submitting = ref(false);
      /** 断网提示与重试入口（F-BASE-08） */
      const netError = ref<StudyNetError | null>(null);
      /** 重试动作：断网后点「重试」重放上一次失败的服务端请求（V0 不做断网补交） */
      const retryAction = ref<null | (() => Promise<void>)>(null);

      const lineLabel = computed(() => (word.value?.lineType === 'OUT' ? '课外单词' : '课内单词'));

      const loadWord = async () => {
        loading.value = true;
        feedback.value = null;
        typed.value = '';
        try {
          // 线别必须显式带上：/word/practice/next 的 lineType 虽是可选参数，
          // 但为空时服务端按「线别只能是 IN（课内）或 OUT（课外）」直接 400。
          // V1 范围是课内线优先（PRD「背单词模块（课内线优先）」），所以固定走 IN。
          word.value = await fetchWordPractice(LINE_TYPE);
          netError.value = null;
        } catch (e) {
          word.value = null;
          // F-BASE-08：断网 → 可读提示 +「重试」入口；业务错误 → 保留原来的提示语义
          const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
          netError.value = state.offline ? state : null;
          if (state.offline) {
            retryAction.value = () => loadWord();
          } else {
            createMessage.warning('单词没取到，稍后再看看');
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

      const onSubmit = async () => {
        if (feedback.value) {
          await loadWord();
          return;
        }
        if (!word.value || !typed.value.trim()) {
          return;
        }
        submitting.value = true;
        try {
          const result = await submitWordPractice({
            wordCode: word.value.wordCode,
            answer: typed.value.trim(),
            lineType: word.value.lineType,
          });
          feedback.value = {
            correct: Boolean((result as any)?.correct),
            basis: (result as any)?.basis,
          };
        } catch (e) {
          // F-BASE-08：断网 → 可读提示 +「重试」入口；已写的单词保留，不做本地判分
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

      onMounted(() => {
        loadWord();
      });

      return {
        word,
        typed,
        feedback,
        loading,
        submitting,
        lineLabel,
        netError,
        loadWord,
        onRetry,
        onSubmit,
      };
    },
  });
</script>

<style lang="less">
  .study-word {
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

    &__meaning {
      margin: 12px 0 0;
      font-size: 18px;
      font-weight: 500;
      line-height: 1.4;
      color: @text-color-base;
    }

    &__phonetic {
      margin: 8px 0 0;
      font-size: 16px;
      color: @text-color-call-out;
    }

    &__sentence {
      margin: 12px 0 0;
      padding: 12px 16px;
      border-radius: 8px;
      background-color: @app-content-background;
      font-size: 16px;
      line-height: 1.5715;
      color: @text-color-base;
    }

    &__entry {
      margin-top: 16px;
    }

    &__input {
      min-height: 44px;
      font-size: 16px;
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
