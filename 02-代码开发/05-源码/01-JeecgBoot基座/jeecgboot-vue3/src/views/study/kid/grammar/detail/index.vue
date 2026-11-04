<template>
  <StudyLayout module-title="语法讲解">
    <div class="study-kid-grammar-detail">
      <ChildSwitcher class="study-kid-grammar-detail__child" />

      <!-- 断网：可读提示 + 重试入口（F-BASE-08） -->
      <a-alert
        v-if="netError"
        class="study-kid-grammar-detail__net-error"
        type="warning"
        show-icon
        :message="netError.message"
      >
        <template #action>
          <a-button size="small" @click="onRetry">重试</a-button>
        </template>
      </a-alert>

      <a-spin :spinning="loading" class="study-kid-grammar-detail__spin">
        <template v-if="point">
          <div class="study-kid-grammar-detail__head">
            <h2 class="study-kid-grammar-detail__title">{{ point.title }}</h2>
            <span v-if="point.examType" class="study-kid-grammar-detail__tag">{{ point.examType }}</span>
          </div>

          <!-- 规则 -->
          <section class="study-kid-grammar-detail__block">
            <h3 class="study-kid-grammar-detail__block-title">规则</h3>
            <p class="study-kid-grammar-detail__rule">{{ point.rule }}</p>
          </section>

          <!-- 例句 -->
          <section v-if="point.example" class="study-kid-grammar-detail__block">
            <h3 class="study-kid-grammar-detail__block-title">例句</h3>
            <p class="study-kid-grammar-detail__text">{{ point.example }}</p>
          </section>

          <!-- 反例 -->
          <section v-if="point.counterExample" class="study-kid-grammar-detail__block">
            <h3 class="study-kid-grammar-detail__block-title">注意</h3>
            <p class="study-kid-grammar-detail__text">{{ point.counterExample }}</p>
          </section>
        </template>

        <a-empty v-if="!loading && !point" class="study-kid-grammar-detail__empty" description="暂时没有这个语法点" />
      </a-spin>
    </div>

    <template #footer>
      <a-button
        type="primary"
        class="study-kid-grammar-detail__primary"
        :disabled="!point"
        @click="onGoTransform"
      >
        去变形器
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
  import { fetchGrammarPoint, type StudyGrammarPointVO } from '../../../api';
  import { describeNetError, OFFLINE_HINT, RETRY_LABEL, type StudyNetError } from '../../../net';

  /**
   * 学习端语法点详情页（设计规范 §10.2）。
   *
   * 一屏一任务：规则、例句、注意点三块，讲完就走。
   * 页面不含表格，避免窄屏横向滚动（§9.7 / F-16）。
   */
  export default defineComponent({
    name: 'StudyKidGrammarDetailPage',
    components: { ChildSwitcher, StudyLayout },
    setup() {
      const route = useRoute();
      const router = useRouter();
      const { createMessage } = useMessage();

      const point = ref<StudyGrammarPointVO | null>(null);
      const loading = ref(false);
      /** 断网提示与重试入口（F-BASE-08） */
      const netError = ref<StudyNetError | null>(null);
      /** 重试动作：断网后点重试重放上一次失败的服务端请求 */
      const retryAction = ref<null | (() => Promise<void>)>(null);

      /** 当前语法点的业务ID */
      const grammarCode = computed(() => String(route.params.code || ''));

      const load = async () => {
        const code = grammarCode.value;
        if (!code) {
          return;
        }
        loading.value = true;
        try {
          point.value = await fetchGrammarPoint(code);
          netError.value = null;
        } catch (e) {
          point.value = null;
          // F-BASE-08：断网给可读提示与重试入口，业务错误保留原来的提示语义
          const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
          netError.value = state.offline ? state : null;
          if (state.offline) {
            retryAction.value = () => load();
          } else {
            createMessage.warning('这个语法点没取到，稍后再看看');
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

      /** 去变形器，并把当前语法点带过去 */
      const onGoTransform = () => {
        router.push({ path: '/study/kid/grammar/transform', query: { grammarCode: grammarCode.value } });
      };

      onMounted(() => {
        load();
      });

      return { point, loading, netError, onRetry, onGoTransform };
    },
  });
</script>

<style lang="less">
  .study-kid-grammar-detail {
    padding: 16px;

    &__child {
      margin-bottom: 12px;
    }

    &__spin {
      display: block;
      min-height: 240px;
    }

    &__head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
    }

    &__title {
      margin: 0;
      font-size: 18px;
      font-weight: 600;
      line-height: 1.4;
      color: @text-color-base;
    }

    &__tag {
      flex: 0 0 auto;
      font-size: 12px;
      color: @text-color-call-out;
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

    &__rule {
      margin: 8px 0 0;
      font-size: 18px;
      line-height: 1.5715;
      color: @text-color-base;
    }

    &__text {
      margin: 8px 0 0;
      font-size: 16px;
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
