<template>
  <StudyLayout module-title="变式练习">
    <div class="study-kid-math-variant">
      <ChildSwitcher class="study-kid-math-variant__child" />

      <!-- 断网：可读提示 + 重试入口（F-BASE-08） -->
      <a-alert
        v-if="netError"
        class="study-kid-math-variant__net-error"
        type="warning"
        show-icon
        :message="netError.message"
      >
        <template #action>
          <a-button size="small" @click="onRetry">重试</a-button>
        </template>
      </a-alert>

      <a-spin :spinning="loading" class="study-kid-math-variant__spin">
        <template v-if="current">
          <div class="study-kid-math-variant__meta">
            <span class="study-kid-math-variant__layer">{{ current.layerName || `第 ${currentIndex + 1} 层` }}</span>
            <span class="study-kid-math-variant__progress">第 {{ currentIndex + 1 }} / {{ variants.length }} 题</span>
          </div>

          <!-- 题干 18px（§10.1） -->
          <h2 class="study-kid-math-variant__stem">{{ current.stem }}</h2>

          <!-- 作答入口 -->
          <div v-if="!result" class="study-kid-math-variant__entry">
            <a-input
              v-model:value="answer"
              class="study-kid-math-variant__input"
              size="large"
              placeholder="在这里写下你的答案"
              @pressEnter="onSubmit"
            />
          </div>

          <!--
            反馈区：只有提交之后才渲染，未作答时这里连 DOM 都不存在（R-01 / §10.6）。
            判定来源必须显示，判定依据可以展开（§10.3）。
          -->
          <section
            v-if="result"
            class="study-kid-math-variant__feedback"
            :class="result.correct ? 'is-right' : 'is-wrong'"
          >
            <div class="study-kid-math-variant__verdict">
              <Icon v-if="result.correct" icon="ant-design:check-circle" />
              <Icon v-else icon="ant-design:smile" />
              <span class="study-kid-math-variant__verdict-text">{{ result.correct ? '对了' : '再看一次' }}</span>
              <span class="study-kid-math-variant__source">{{ sourceLabel }}</span>
            </div>

            <div class="study-kid-math-variant__basis">
              <a-button type="link" class="study-kid-math-variant__basis-toggle" @click="basisVisible = !basisVisible">
                {{ basisVisible ? '收起判定依据' : '看看判定依据' }}
              </a-button>
              <p v-if="basisVisible" class="study-kid-math-variant__basis-text">{{ result.basis }}</p>
            </div>

            <!-- 步骤级错因：服务端给了就显示 -->
            <ul v-if="stepTags.length > 0" class="study-kid-math-variant__tags">
              <li v-for="(tag, index) in stepTags" :key="index" class="study-kid-math-variant__tag">
                第 {{ (tag.stepIndex ?? 0) + 1 }} 步 · {{ tag.errorCause }}
              </li>
            </ul>

            <!-- 错因补充：中性表述，不做负向评价（§10.3） -->
            <div v-if="!result.correct && !cause" class="study-kid-math-variant__cause">
              <p class="study-kid-math-variant__cause-title">先看看是哪一步卡住了</p>
              <div class="study-kid-math-variant__cause-list">
                <a-button
                  v-for="item in CAUSE_OPTIONS"
                  :key="item"
                  class="study-kid-math-variant__cause-item"
                  @click="onPickCause(item)"
                >
                  {{ item }}
                </a-button>
              </div>
            </div>
          </section>

          <!-- 过关结果：只有提交过关之后才渲染（R-01） -->
          <section v-if="passResult" class="study-kid-math-variant__pass" :class="passResult.passed ? 'is-right' : 'is-wrong'">
            <div class="study-kid-math-variant__verdict">
              <Icon v-if="passResult.passed" icon="ant-design:check-circle" />
              <Icon v-else icon="ant-design:smile" />
              <span class="study-kid-math-variant__verdict-text">
                {{ passResult.passed ? '这一关过了' : '还差一点' }}
              </span>
            </div>
            <p v-if="passResult.hint" class="study-kid-math-variant__pass-text">{{ passResult.hint }}</p>
            <p v-if="passResult.followUpQuestion" class="study-kid-math-variant__pass-text">
              {{ passResult.followUpQuestion }}
            </p>
            <a-button v-if="!passResult.passed" class="study-kid-math-variant__oral-entry" @click="onGoOral">
              去讲一遍
            </a-button>
          </section>
        </template>

        <a-empty v-if="!loading && variants.length === 0" class="study-kid-math-variant__empty" description="暂时没有变式" />
      </a-spin>
    </div>

    <template #footer>
      <a-button
        type="primary"
        class="study-kid-math-variant__primary"
        :loading="submitting"
        :disabled="!canSubmit"
        @click="onPrimary"
      >
        {{ primaryLabel }}
      </a-button>
    </template>
  </StudyLayout>
</template>

<script lang="ts">
  import { computed, defineComponent, onMounted, ref } from 'vue';
  import { useRoute, useRouter } from 'vue-router';
  import { Icon } from '/@/components/Icon';
  import ChildSwitcher from '/@/components/study/ChildSwitcher.vue';
  import { useMessage } from '/@/hooks/web/useMessage';
  import StudyLayout from '/@/layouts/study/index.vue';
  import {
    fetchMathMotherVariants,
    submitMathMotherPass,
    submitMathVariantAnswer,
    type StudyMathPassResultVO,
    type StudyMathStepTagVO,
    type StudyMathVariantAnswerResultVO,
    type StudyMathVariantVO,
  } from '../../../api';
  import { describeNetError, OFFLINE_HINT, RETRY_LABEL, type StudyNetError } from '../../../net';

  /** 错因候选（中性表述，与后端错因五维一致） */
  const CAUSE_OPTIONS = ['概念不清', '审题偏差', '计算失误', '方法不会', '时间不够'];

  /**
   * 学习端变式练习页（设计规范 §10.2 / §10.3）。
   *
   * 一屏一任务：一次只做一道变式，做完才出判定。
   * 判定由服务端规则给出，页面只展示判定来源与判定依据（R-02）。
   * 变式响应里没有答案字段，反馈区在提交之前根本不进 DOM（R-01）。
   */
  export default defineComponent({
    name: 'StudyKidMathVariantPage',
    components: { ChildSwitcher, Icon, StudyLayout },
    setup() {
      const route = useRoute();
      const router = useRouter();
      const { createMessage } = useMessage();

      const variants = ref<StudyMathVariantVO[]>([]);
      const currentIndex = ref(0);
      const answer = ref('');
      const result = ref<StudyMathVariantAnswerResultVO | null>(null);
      const passResult = ref<StudyMathPassResultVO | null>(null);
      const basisVisible = ref(false);
      const cause = ref('');
      /** 本次作答过的变式：过关判定时一并提交 */
      const answers = ref<{ variantCode: string; answer: string }[]>([]);
      const loading = ref(false);
      const submitting = ref(false);
      /** 断网提示与重试入口（F-BASE-08） */
      const netError = ref<StudyNetError | null>(null);
      /** 重试动作：断网后点重试重放上一次失败的服务端请求 */
      const retryAction = ref<null | (() => Promise<void>)>(null);

      /** 当前这一道变式 */
      const current = computed(() => variants.value[currentIndex.value]);

      /** 判定来源（§10.3 强制规则 1） */
      const sourceLabel = computed(() =>
        result.value?.judgeSource === 'rule+model' ? 'AI 辅助讲解，判定以规则为准' : '按答案判定',
      );

      /** 步骤级错因 */
      const stepTags = computed<StudyMathStepTagVO[]>(() => result.value?.stepTags || []);

      /** 底部主按钮文案 */
      const primaryLabel = computed(() => {
        if (!current.value) {
          return '提交作答';
        }
        if (!result.value) {
          return '提交作答';
        }
        return currentIndex.value < variants.value.length - 1 ? '下一题' : '提交过关';
      });

      /** 能不能提交 */
      const canSubmit = computed(() => {
        if (!current.value || submitting.value || passResult.value) {
          return false;
        }
        if (!result.value) {
          return Boolean(answer.value.trim());
        }
        return true;
      });

      /** 当前关卡的业务ID */
      const motherCode = computed(() => String(route.params.code || ''));

      const load = async () => {
        const code = motherCode.value;
        if (!code) {
          return;
        }
        loading.value = true;
        try {
          const list = await fetchMathMotherVariants(code);
          variants.value = Array.isArray(list) ? list : [];
          currentIndex.value = 0;
          answer.value = '';
          result.value = null;
          passResult.value = null;
          basisVisible.value = false;
          cause.value = '';
          answers.value = [];
          netError.value = null;
        } catch (e) {
          variants.value = [];
          // F-BASE-08：断网给可读提示与重试入口，业务错误保留原来的提示语义
          const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
          netError.value = state.offline ? state : null;
          if (state.offline) {
            retryAction.value = () => load();
          } else {
            createMessage.warning('变式没取到，稍后再看看');
          }
        } finally {
          loading.value = false;
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

      /** 提交这一道变式 */
      const onSubmit = async () => {
        const variant = current.value;
        if (!variant || !answer.value.trim()) {
          return;
        }
        submitting.value = true;
        try {
          const payload: Record<string, any> = { answer: answer.value.trim() };
          if (cause.value) {
            payload.errorCause = cause.value;
          }
          result.value = await submitMathVariantAnswer(motherCode.value, variant.variantCode, payload);
          answers.value = [
            ...answers.value.filter((item) => item.variantCode !== variant.variantCode),
            { variantCode: variant.variantCode, answer: answer.value.trim() },
          ];
          cause.value = '';
          basisVisible.value = false;
          netError.value = null;
        } catch (e) {
          // F-BASE-08：断网给可读提示与重试入口，业务错误保留原来的提示语义
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

      /** 选错因后重新提交这一道 */
      const onPickCause = async (value: string) => {
        cause.value = value;
        result.value = null;
        await onSubmit();
      };

      /** 下一题 */
      const onNext = () => {
        if (currentIndex.value < variants.value.length - 1) {
          currentIndex.value += 1;
          answer.value = '';
          result.value = null;
          basisVisible.value = false;
          cause.value = '';
        }
      };

      /** 提交过关判定 */
      const onPass = async () => {
        submitting.value = true;
        try {
          passResult.value = await submitMathMotherPass(motherCode.value, { variantResults: answers.value });
          netError.value = null;
        } catch (e) {
          // F-BASE-08：断网给可读提示与重试入口，业务错误保留原来的提示语义
          const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
          netError.value = state.offline ? state : null;
          if (state.offline) {
            retryAction.value = () => onPass();
          } else {
            createMessage.warning('这次提交没成功，再试一次');
          }
        } finally {
          submitting.value = false;
        }
      };

      /** 底部主按钮：按当前状态走提交作答 / 下一题 / 提交过关 */
      const onPrimary = async () => {
        if (!result.value) {
          await onSubmit();
          return;
        }
        if (currentIndex.value < variants.value.length - 1) {
          onNext();
          return;
        }
        await onPass();
      };

      /** 去口述 */
      const onGoOral = () => {
        router.push(`/study/kid/math/oral/${motherCode.value}`);
      };

      onMounted(() => {
        load();
      });

      return {
        CAUSE_OPTIONS,
        variants,
        currentIndex,
        current,
        answer,
        result,
        passResult,
        basisVisible,
        cause,
        stepTags,
        sourceLabel,
        primaryLabel,
        canSubmit,
        loading,
        submitting,
        netError,
        onRetry,
        onSubmit,
        onPickCause,
        onPrimary,
        onGoOral,
      };
    },
  });
</script>

<style lang="less">
  .study-kid-math-variant {
    padding: 16px;

    &__child {
      margin-bottom: 12px;
    }

    &__spin {
      display: block;
      min-height: 240px;
    }

    &__meta {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      font-size: 14px;
      line-height: 1.4;
      color: @text-color-call-out;
    }

    &__layer {
      font-weight: 600;
      color: @primary-color;
    }

    /* 题干 18px（§10.1） */
    &__stem {
      margin: 12px 0 0;
      font-size: 18px;
      font-weight: 500;
      line-height: 1.4;
      color: @text-color-base;
    }

    &__entry {
      margin-top: 16px;
    }

    &__input {
      min-height: 44px;
      font-size: 16px;
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

    &__source {
      padding: 4px 8px;
      border-radius: 4px;
      font-size: 12px;
      font-weight: 400;
      color: @text-color-call-out;
      background-color: @white;
    }

    &__basis {
      margin-top: 8px;
    }

    &__basis-text {
      margin: 4px 0 0;
      font-size: 14px;
      line-height: 1.5715;
      color: @text-color-base;
    }

    &__tags {
      margin: 8px 0 0;
      padding-left: 16px;
    }

    &__tag {
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
    }

    &__pass {
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

    &__pass-text {
      margin: 8px 0 0;
      font-size: 16px;
      line-height: 1.5715;
      color: @text-color-base;
    }

    &__oral-entry {
      margin-top: 12px;
      min-height: 44px;
      font-size: 16px;
      border-radius: 8px;
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
