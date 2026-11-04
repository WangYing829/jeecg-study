<template>
  <StudyLayout module-title="母题思路">
    <div class="study-kid-math-teach">
      <ChildSwitcher class="study-kid-math-teach__child" />

      <!-- 断网：可读提示 + 重试入口（F-BASE-08） -->
      <a-alert
        v-if="netError"
        class="study-kid-math-teach__net-error"
        type="warning"
        show-icon
        :message="netError.message"
      >
        <template #action>
          <a-button size="small" @click="onRetry">重试</a-button>
        </template>
      </a-alert>

      <a-spin :spinning="loading" class="study-kid-math-teach__spin">
        <template v-if="detail">
          <h2 class="study-kid-math-teach__title">{{ detail.title }}</h2>

          <!-- 思路：条件类型 -->
          <section
            v-if="detail.conditionTypes && detail.conditionTypes.length > 0"
            class="study-kid-math-teach__block"
          >
            <h3 class="study-kid-math-teach__block-title">先看清条件</h3>
            <div
              v-for="item in detail.conditionTypes"
              :key="item.key || item.label"
              class="study-kid-math-teach__condition"
            >
              <span class="study-kid-math-teach__condition-label">{{ item.label }}</span>
              <span class="study-kid-math-teach__condition-value">{{ (item.variants || []).join('、') }}</span>
            </div>
          </section>

          <!-- 思路：步骤 -->
          <section v-if="detail.steps && detail.steps.length > 0" class="study-kid-math-teach__block">
            <h3 class="study-kid-math-teach__block-title">一步一步来</h3>
            <div v-for="(step, index) in detail.steps" :key="index" class="study-kid-math-teach__step">
              <span class="study-kid-math-teach__step-title">{{ step.title }}</span>
              <p v-if="step.formula" class="study-kid-math-teach__formula">{{ step.formula }}</p>
              <p v-if="step.note" class="study-kid-math-teach__note">{{ step.note }}</p>
            </div>
          </section>

          <!-- 思路：容易卡住的地方 -->
          <section v-if="detail.pitfalls && detail.pitfalls.length > 0" class="study-kid-math-teach__block">
            <h3 class="study-kid-math-teach__block-title">容易卡住的地方</h3>
            <ul class="study-kid-math-teach__pitfalls">
              <li v-for="(item, index) in detail.pitfalls" :key="index" class="study-kid-math-teach__pitfall">
                {{ item }}
              </li>
            </ul>
          </section>

          <!-- 口述任务说明：只给任务，不给答案（R-01） -->
          <section v-if="brief" class="study-kid-math-teach__block">
            <h3 class="study-kid-math-teach__block-title">讲给我听</h3>
            <p class="study-kid-math-teach__prompt">{{ brief.prompt || oralPrompt }}</p>
            <p class="study-kid-math-teach__prompt-meta">
              要说清 {{ brief.keyPointCount ?? 0 }} 个要点
            </p>
          </section>
        </template>

        <a-empty v-if="!loading && !detail" class="study-kid-math-teach__empty" description="暂时没有这道题" />
      </a-spin>
    </div>

    <template #footer>
      <a-button
        type="primary"
        class="study-kid-math-teach__primary"
        :disabled="!detail"
        @click="onGoOral"
      >
        去口述
      </a-button>
    </template>
  </StudyLayout>
</template>

<script lang="ts">
  import { computed, defineComponent, onMounted, ref } from 'vue';
  import { useRoute, useRouter } from 'vue-router';
  import ChildSwitcher from '/@/components/study/ChildSwitcher.vue';
  import { useMessage } from '/@/hooks/web/useMessage';
  import StudyLayout from '/@/layouts/study/index.vue';
  import {
    fetchMathMotherDetail,
    fetchMathMotherNarrative,
    type StudyMathMotherDetailVO,
    type StudyMathNarrativeBriefVO,
  } from '../../../../api';
  import { describeNetError, OFFLINE_HINT, RETRY_LABEL, type StudyNetError } from '../../../../net';

  /** 口述任务的兜底提示（服务端没给提示时用这句，不含任何答案） */
  const ORAL_PROMPT = '说一说你在这道题里看到了哪几个量，它们之间是什么关系。';

  /**
   * 学习端母题思路页（设计规范 §10.2）。
   *
   * 这一页只讲思路：条件、步骤、容易卡住的地方，再给出口述任务说明。
   * 它不出现关键关系，也不出现任何一题的答案（R-01）。
   */
  export default defineComponent({
    name: 'StudyKidMathMotherTeachPage',
    components: { ChildSwitcher, StudyLayout },
    setup() {
      const route = useRoute();
      const router = useRouter();
      const { createMessage } = useMessage();

      const detail = ref<StudyMathMotherDetailVO | null>(null);
      const brief = ref<StudyMathNarrativeBriefVO | null>(null);
      const loading = ref(false);
      /** 断网提示与重试入口（F-BASE-08） */
      const netError = ref<StudyNetError | null>(null);
      /** 重试动作：断网后点重试重放上一次失败的服务端请求 */
      const retryAction = ref<null | (() => Promise<void>)>(null);

      /** 口述任务的兜底提示 */
      const oralPrompt = ORAL_PROMPT;

      /** 当前关卡的业务ID */
      const motherCode = computed(() => String(route.params.code || ''));

      const load = async () => {
        const code = motherCode.value;
        if (!code) {
          return;
        }
        loading.value = true;
        try {
          const detailData = await fetchMathMotherDetail(code);
          detail.value = detailData || null;
          netError.value = null;
        } catch (e) {
          detail.value = null;
          // F-BASE-08：断网给可读提示与重试入口，业务错误保留原来的提示语义
          const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
          netError.value = state.offline ? state : null;
          if (state.offline) {
            retryAction.value = () => load();
          } else {
            createMessage.warning('思路没取到，稍后再看看');
          }
        } finally {
          loading.value = false;
        }
        // 口述任务说明取不到时不影响思路阅读，单独兜底
        try {
          brief.value = await fetchMathMotherNarrative(code);
        } catch (e) {
          brief.value = null;
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

      /** 去口述 */
      const onGoOral = () => {
        router.push(`/study/kid/math/oral/${motherCode.value}`);
      };

      onMounted(() => {
        load();
      });

      return {
        detail,
        brief,
        oralPrompt,
        loading,
        netError,
        onRetry,
        onGoOral,
      };
    },
  });
</script>

<style lang="less">
  .study-kid-math-teach {
    padding: 16px;

    &__child {
      margin-bottom: 12px;
    }

    &__spin {
      display: block;
      min-height: 240px;
    }

    &__title {
      margin: 0;
      font-size: 18px;
      font-weight: 600;
      line-height: 1.4;
      color: @text-color-base;
    }

    &__block {
      margin-top: 16px;
      padding: 16px;
      border: 1px solid @border-color-light;
      border-radius: 12px;
      background-color: @app-content-background;
    }

    &__block-title {
      margin: 0;
      font-size: 16px;
      font-weight: 600;
      line-height: 1.4;
      color: @text-color-base;
    }

    &__condition {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 12px;
      padding: 8px 0;
    }

    &__condition-label {
      flex: 0 0 auto;
      font-size: 14px;
      line-height: 1.4;
      color: @text-color-call-out;
    }

    &__condition-value {
      flex: 1 1 auto;
      min-width: 0;
      font-size: 16px;
      line-height: 1.4;
      color: @text-color-base;
      text-align: right;
      word-break: break-word;
    }

    &__step {
      margin-top: 12px;
    }

    &__step-title {
      font-size: 16px;
      font-weight: 500;
      line-height: 1.4;
      color: @text-color-base;
    }

    &__formula {
      margin: 4px 0 0;
      font-size: 18px;
      line-height: 1.5715;
      color: @primary-color;
      word-break: break-word;
    }

    &__note {
      margin: 4px 0 0;
      font-size: 14px;
      line-height: 1.5715;
      color: @text-color-call-out;
    }

    &__pitfalls {
      margin: 8px 0 0;
      padding-left: 16px;
    }

    &__pitfall {
      font-size: 16px;
      line-height: 1.5715;
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
