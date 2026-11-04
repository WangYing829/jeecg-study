<template>
  <StudyLayout module-title="口述确认">
    <div class="study-kid-math-oral">
      <ChildSwitcher class="study-kid-math-oral__child" />

      <!-- 断网：可读提示 + 重试入口（F-BASE-08） -->
      <a-alert
        v-if="netError"
        class="study-kid-math-oral__net-error"
        type="warning"
        show-icon
        :message="netError.message"
      >
        <template #action>
          <a-button size="small" @click="onRetry">重试</a-button>
        </template>
      </a-alert>

      <a-spin :spinning="loading" class="study-kid-math-oral__spin">
        <!-- 口述任务 -->
        <section class="study-kid-math-oral__task">
          <h2 class="study-kid-math-oral__title">{{ brief?.title || '讲给我听' }}</h2>
          <p class="study-kid-math-oral__prompt">{{ brief?.prompt || oralPrompt }}</p>
          <p class="study-kid-math-oral__prompt-meta">要说清 {{ brief?.keyPointCount ?? 0 }} 个要点</p>
        </section>

        <!-- 口述内容 -->
        <div class="study-kid-math-oral__entry">
          <a-textarea
            v-model:value="speechText"
            class="study-kid-math-oral__input"
            :rows="5"
            placeholder="把你想到的写下来"
          />
        </div>

        <!--
          反馈区：只有提交之后才渲染，未提交时这里连 DOM 都不存在（R-01 / §10.6）。
          反馈只给命中 / 还要说的关键词与一条追问，不给答案（R-01）。
        -->
        <section v-if="result" class="study-kid-math-oral__feedback" :class="result.passed ? 'is-right' : 'is-wrong'">
          <div class="study-kid-math-oral__verdict">
            <Icon v-if="result.passed" icon="ant-design:check-circle" />
            <Icon v-else icon="ant-design:smile" />
            <span class="study-kid-math-oral__verdict-text">{{ result.passed ? '讲清楚了' : '再补充一点' }}</span>
          </div>

          <div v-if="result.matched.length > 0" class="study-kid-math-oral__group">
            <span class="study-kid-math-oral__group-label">已经说到</span>
            <ul class="study-kid-math-oral__list">
              <li v-for="(item, index) in result.matched" :key="index" class="study-kid-math-oral__item">
                {{ item }}
              </li>
            </ul>
          </div>

          <div v-if="result.missing.length > 0" class="study-kid-math-oral__group">
            <span class="study-kid-math-oral__group-label">还可以再说说</span>
            <ul class="study-kid-math-oral__list">
              <li v-for="(item, index) in result.missing" :key="index" class="study-kid-math-oral__item">
                {{ item }}
              </li>
            </ul>
          </div>

          <p v-if="result.followUp" class="study-kid-math-oral__follow">{{ result.followUp }}</p>
          <p v-if="result.motherPassed" class="study-kid-math-oral__state">这一关已经过了</p>
        </section>
      </a-spin>
    </div>

    <template #footer>
      <a-button
        type="primary"
        class="study-kid-math-oral__primary"
        :loading="submitting"
        :disabled="!canSubmit"
        @click="onSubmit"
      >
        {{ result ? '再说一次' : '提交口述' }}
      </a-button>
    </template>
  </StudyLayout>
</template>

<script lang="ts">
  import { computed, defineComponent, onMounted, ref } from 'vue';
  import { useRoute } from 'vue-router';
  import { Icon } from '/@/components/Icon';
  import ChildSwitcher from '/@/components/study/ChildSwitcher.vue';
  import { useMessage } from '/@/hooks/web/useMessage';
  import StudyLayout from '/@/layouts/study/index.vue';
  import {
    fetchMathMotherNarrative,
    submitMathMotherNarrative,
    submitMathMotherPass,
    submitPracticeOral,
    type StudyMathNarrativeBriefVO,
    type StudyMathNarrativeResultVO,
    type StudyOralResultVO,
  } from '../../../api';
  import { describeNetError, OFFLINE_HINT, RETRY_LABEL, type StudyNetError } from '../../../net';

  /** 口述任务的兜底提示（不含任何答案） */
  const ORAL_PROMPT = '说一说你在这道题里看到了哪几个量，它们之间是什么关系。';

  /** 口述反馈的展示形态（两个接口的返回统一到这里） */
  interface StudyOralView {
    passed: boolean;
    matched: string[];
    missing: string[];
    followUp: string;
    motherPassed: boolean;
  }

  /**
   * 学习端口述确认页（设计规范 §10.2）。
   *
   * 这一页只做口述：写下想法 → 提交 → 看命中的要点、还要说的要点和一条追问。
   * 它不出现任何一题的答案，也不出现关键关系的内容（R-01）。
   */
  export default defineComponent({
    name: 'StudyKidMathOralPage',
    components: { ChildSwitcher, Icon, StudyLayout },
    setup() {
      const route = useRoute();
      const { createMessage } = useMessage();

      const brief = ref<StudyMathNarrativeBriefVO | null>(null);
      const speechText = ref('');
      const result = ref<StudyOralView | null>(null);
      const loading = ref(false);
      const submitting = ref(false);
      /** 断网提示与重试入口（F-BASE-08） */
      const netError = ref<StudyNetError | null>(null);
      /** 重试动作：断网后点重试重放上一次失败的服务端请求 */
      const retryAction = ref<null | (() => Promise<void>)>(null);

      /** 口述任务的兜底提示 */
      const oralPrompt = ORAL_PROMPT;

      /** 当前题的业务ID */
      const code = computed(() => String(route.params.code || ''));

      /** 能不能提交 */
      const canSubmit = computed(() => Boolean(speechText.value.trim()) && !submitting.value);

      /** 口述任务说明：取不到时不影响口述本身 */
      const loadBrief = async () => {
        try {
          brief.value = await fetchMathMotherNarrative(code.value);
        } catch (e) {
          brief.value = null;
        }
      };

      /** 练习域的返回 → 统一展示形态 */
      const fromOralVO = (data: StudyOralResultVO): StudyOralView => ({
        passed: Boolean(data?.passed),
        matched: data?.matchedKeywords || [],
        missing: data?.missingKeywords || [],
        followUp: data?.followUpQuestion || '',
        motherPassed: Boolean(data?.motherPassed),
      });

      /** 母题域的返回 → 统一展示形态 */
      const fromNarrativeVO = (data: StudyMathNarrativeResultVO): StudyOralView => ({
        passed: Boolean(data?.narrativePassed),
        matched: data?.hitKeywords || [],
        missing: data?.missingKeywords || [],
        followUp: data?.followUpQuestion || '',
        motherPassed: false,
      });

      /** 提交口述 */
      const onSubmit = async () => {
        const text = speechText.value.trim();
        if (!text) {
          return;
        }
        submitting.value = true;
        try {
          const data = await submitPracticeOral(code.value, { speechText: text });
          result.value = fromOralVO(data);
          netError.value = null;
        } catch (e: any) {
          // F-BASE-08：断网给可读提示与重试入口，业务错误保留原来的提示语义
          const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
          netError.value = state.offline ? state : null;
          if (state.offline) {
            retryAction.value = () => onSubmit();
          }
          const message = String(e?.message || '');
          // 练习域按内容ID收口：这个ID在练习域取不到时，退回母题域同一个判定口径
          if (message.includes('内容不存在')) {
            try {
              const narrative = await submitMathMotherNarrative(code.value, { narrative: text });
              result.value = fromNarrativeVO(narrative);
              netError.value = null;
            } catch (fallbackError: any) {
              const fallbackState = describeNetError(fallbackError, OFFLINE_HINT, RETRY_LABEL);
              netError.value = fallbackState.offline ? fallbackState : null;
              if (fallbackState.offline) {
                retryAction.value = () => onSubmit();
              }
            }
          } else if (!state.offline) {
            createMessage.warning('这次提交没成功，再试一次');
          }
        } finally {
          submitting.value = false;
        }
        // 口述讲完后再过一次过关判定：变式那部分由变式页提交，这一步只带口述内容
        if (result.value) {
          try {
            await submitMathMotherPass(code.value, { narrative: speechText.value.trim() });
          } catch (e) {
            // 过关判定失败不打断口述反馈
          }
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
        loading.value = true;
        loadBrief().finally(() => {
          loading.value = false;
        });
      });

      return {
        brief,
        speechText,
        result,
        oralPrompt,
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
  .study-kid-math-oral {
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

    &__title {
      margin: 0;
      font-size: 18px;
      font-weight: 600;
      line-height: 1.4;
      color: @text-color-base;
    }

    &__prompt {
      margin: 8px 0 0;
      font-size: 16px;
      line-height: 1.5715;
      color: @text-color-base;
    }

    &__prompt-meta {
      margin: 4px 0 0;
      font-size: 14px;
      line-height: 1.5715;
      color: @text-color-call-out;
    }

    &__entry {
      margin-top: 16px;
    }

    &__input {
      font-size: 16px;
      line-height: 1.5715;
    }

    /* 反馈区：柔和底色，不用大面积纯红（§10.3 / F-22） */
    &__feedback {
      margin-top: 16px;
      padding: 12px 16px;
      border: 1px solid @border-color-light;
      border-radius: 12px;
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

    &__group {
      margin-top: 12px;
    }

    &__group-label {
      font-size: 14px;
      line-height: 1.4;
      color: @text-color-call-out;
    }

    &__list {
      margin: 4px 0 0;
      padding-left: 16px;
    }

    &__item {
      font-size: 16px;
      line-height: 1.5715;
      color: @text-color-base;
    }

    &__follow {
      margin: 12px 0 0;
      font-size: 16px;
      line-height: 1.5715;
      color: @text-color-base;
    }

    &__state {
      margin: 8px 0 0;
      font-size: 14px;
      line-height: 1.4;
      color: @text-color-call-out;
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
