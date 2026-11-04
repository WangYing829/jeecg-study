<template>
  <StudyLayout module-title="母题关卡">
    <div class="study-kid-math-mother">
      <ChildSwitcher class="study-kid-math-mother__child" />

      <!-- 断网：可读提示 + 重试入口（F-BASE-08） -->
      <a-alert
        v-if="netError"
        class="study-kid-math-mother__net-error"
        type="warning"
        show-icon
        :message="netError.message"
      >
        <template #action>
          <a-button size="small" @click="onRetry">重试</a-button>
        </template>
      </a-alert>

      <a-spin :spinning="loading" class="study-kid-math-mother__spin">
        <template v-if="detail">
          <h2 class="study-kid-math-mother__title">{{ detail.title }}</h2>

          <!-- 条件类型 -->
          <section v-if="detail.conditionTypes && detail.conditionTypes.length > 0" class="study-kid-math-mother__block">
            <h3 class="study-kid-math-mother__block-title">条件类型</h3>
            <div
              v-for="item in detail.conditionTypes"
              :key="item.key || item.label"
              class="study-kid-math-mother__condition"
            >
              <span class="study-kid-math-mother__condition-label">{{ item.label }}</span>
              <span class="study-kid-math-mother__condition-value">{{ (item.variants || []).join('、') }}</span>
            </div>
          </section>

          <!-- 步骤 -->
          <section v-if="detail.steps && detail.steps.length > 0" class="study-kid-math-mother__block">
            <h3 class="study-kid-math-mother__block-title">解题步骤</h3>
            <div v-for="(step, index) in detail.steps" :key="index" class="study-kid-math-mother__step">
              <span class="study-kid-math-mother__step-title">{{ step.title }}</span>
              <p v-if="step.formula" class="study-kid-math-mother__formula">{{ step.formula }}</p>
              <p v-if="step.note" class="study-kid-math-mother__note">{{ step.note }}</p>
            </div>
          </section>

          <!-- 易错点 -->
          <section v-if="detail.pitfalls && detail.pitfalls.length > 0" class="study-kid-math-mother__block">
            <h3 class="study-kid-math-mother__block-title">容易卡住的地方</h3>
            <ul class="study-kid-math-mother__pitfalls">
              <li v-for="(item, index) in detail.pitfalls" :key="index" class="study-kid-math-mother__pitfall">
                {{ item }}
              </li>
            </ul>
          </section>

          <!--
            关键关系：
            它属于解题要领，必须排在「先把变式做完」之后，所以只在这一关已经过关时才渲染。
            没过关时这里只有一句提示，页面不给出关键关系的内容（R-01）。
          -->
          <section class="study-kid-math-mother__block">
            <h3 class="study-kid-math-mother__block-title">关键关系</h3>
            <p v-if="passed && detail.keyRelation" class="study-kid-math-mother__relation">
              {{ detail.keyRelation }}
            </p>
            <p v-else class="study-kid-math-mother__relation-hint">
              先把这一关的变式做完、讲一遍关键关系，这里就会出现。
            </p>
          </section>

          <!-- 下一站：讲解与变式（底部主操作条只有一个主按钮，其余入口放这里） -->
          <div class="study-kid-math-mother__entries">
            <a-button class="study-kid-math-mother__entry" @click="onGoTeach">看母题思路</a-button>
            <a-button class="study-kid-math-mother__entry" @click="onGoVariant">
              做变式（{{ variantCount }} 道）
            </a-button>
          </div>
        </template>

        <a-empty v-if="!loading && !detail" class="study-kid-math-mother__empty" description="暂时没有这道题" />
      </a-spin>
    </div>

    <template #footer>
      <a-button
        type="primary"
        class="study-kid-math-mother__primary"
        :disabled="!detail"
        @click="onGoVariant"
      >
        去变式
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
    fetchMathMotherVariants,
    fetchMathProgress,
    type StudyMathMotherDetailVO,
  } from '../../../api';
  import { describeNetError, OFFLINE_HINT, RETRY_LABEL, type StudyNetError } from '../../../net';

  /**
   * 学习端母题关卡页（设计规范 §10.2）。
   *
   * 一屏一任务：这一页只做一件事——看清这道题的结构，决定下一步去哪。
   * 关键关系要等这一关过关后才出现，页面在过关前不给出它（R-01）。
   */
  export default defineComponent({
    name: 'StudyKidMathMotherPage',
    components: { ChildSwitcher, StudyLayout },
    setup() {
      const route = useRoute();
      const router = useRouter();
      const { createMessage } = useMessage();

      const detail = ref<StudyMathMotherDetailVO | null>(null);
      const variantCount = ref(0);
      /** 这一关是否已经过关（过关后才给关键关系，R-01） */
      const passed = ref(false);
      const loading = ref(false);
      /** 断网提示与重试入口（F-BASE-08） */
      const netError = ref<StudyNetError | null>(null);
      /** 重试动作：断网后点重试重放上一次失败的服务端请求 */
      const retryAction = ref<null | (() => Promise<void>)>(null);

      /** 当前关卡的业务ID */
      const motherCode = computed(() => String(route.params.code || ''));

      const load = async () => {
        const code = motherCode.value;
        if (!code) {
          return;
        }
        loading.value = true;
        try {
          const [detailData, variants, progress] = await Promise.all([
            fetchMathMotherDetail(code),
            fetchMathMotherVariants(code),
            fetchMathProgress(),
          ]);
          detail.value = detailData || null;
          variantCount.value = Array.isArray(variants) ? variants.length : 0;
          passed.value = Boolean(progress?.masteredMotherCodes?.includes(code));
          netError.value = null;
        } catch (e) {
          detail.value = null;
          variantCount.value = 0;
          // F-BASE-08：断网给可读提示与重试入口，业务错误保留原来的提示语义
          const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
          netError.value = state.offline ? state : null;
          if (state.offline) {
            retryAction.value = () => load();
          } else {
            createMessage.warning('这道题没取到，稍后再看看');
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

      /** 去母题思路 */
      const onGoTeach = () => {
        router.push(`/study/kid/math/mother/${motherCode.value}/teach`);
      };

      /** 去变式练习 */
      const onGoVariant = () => {
        router.push(`/study/kid/math/variant/${motherCode.value}`);
      };

      onMounted(() => {
        load();
      });

      return {
        detail,
        variantCount,
        passed,
        loading,
        netError,
        onRetry,
        onGoTeach,
        onGoVariant,
      };
    },
  });
</script>

<style lang="less">
  .study-kid-math-mother {
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

    &__relation {
      margin: 8px 0 0;
      font-size: 16px;
      line-height: 1.5715;
      color: @text-color-base;
    }

    &__relation-hint {
      margin: 8px 0 0;
      font-size: 14px;
      line-height: 1.5715;
      color: @text-color-call-out;
    }

    &__entries {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      margin-top: 16px;
    }

    /* 点击目标 ≥44px（§10.1） */
    &__entry {
      flex: 1 1 auto;
      min-width: 140px;
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
