<template>
  <StudyLayout module-title="英语语法">
    <div class="study-grammar">
      <!-- 断网：可读提示 +「重试」入口（F-BASE-08：V0 不做离线补交，只重试） -->
      <a-alert
        v-if="netError"
        class="study-grammar__net-error"
        type="warning"
        show-icon
        :message="netError.message"
      >
        <template #action>
          <a-button size="small" @click="onRetry">重试</a-button>
        </template>
      </a-alert>

      <a-spin :spinning="loading" class="study-grammar__spin">
        <!-- 语法点列表：学习端用卡片流，不用表格（§9.7） -->
        <ul v-if="points.length > 0" class="study-grammar__list">
          <li
            v-for="point in points"
            :key="point.grammarCode"
            class="study-grammar__item"
            :class="{ 'is-active': current?.grammarCode === point.grammarCode }"
            @click="onPick(point)"
          >
            <span class="study-grammar__item-title">{{ point.title }}</span>
            <span v-if="point.examType" class="study-grammar__item-tag">{{ point.examType }}</span>
          </li>
        </ul>

        <!-- 当前语法点的讲解：最小讲解 + 例句 + 反例 -->
        <section v-if="current" class="study-grammar__detail">
          <h2 class="study-grammar__title">{{ current.title }}</h2>
          <p class="study-grammar__rule">{{ current.rule }}</p>
          <div class="study-grammar__block">
            <span class="study-grammar__block-label">例句</span>
            <p class="study-grammar__block-text">{{ current.example }}</p>
          </div>
          <div class="study-grammar__block">
            <span class="study-grammar__block-label">注意</span>
            <p class="study-grammar__block-text">{{ current.counterExample }}</p>
          </div>
        </section>

        <a-empty v-if="!loading && points.length === 0" class="study-grammar__empty" description="暂时没有语法点" />
      </a-spin>
    </div>

    <template #footer>
      <a-button type="primary" class="study-grammar__submit" :disabled="points.length === 0" @click="onNext">
        换一个语法点
      </a-button>
    </template>
  </StudyLayout>
</template>

<script lang="ts">
  import { defineComponent, onMounted, ref } from 'vue';
  import { useMessage } from '/@/hooks/web/useMessage';
  import StudyLayout from '/@/layouts/study/index.vue';
  import { fetchGrammarPoint, fetchGrammarPoints, type StudyGrammarPointVO } from '../api';
  import { describeNetError, OFFLINE_HINT, RETRY_LABEL, type StudyNetError } from '../net';

  /**
   * 学习端英语语法页（设计规范 §10.2）。
   *
   * <p>一屏一任务：左侧（此处为纵向）语法点列表 + 当前语法点的最小讲解；
   * 页面不含表格，避免窄屏横向滚动（§9.7 / F-16）。</p>
   */
  export default defineComponent({
    name: 'StudyGrammarPage',
    components: { StudyLayout },
    setup() {
      const { createMessage } = useMessage();

      const points = ref<StudyGrammarPointVO[]>([]);
      const current = ref<StudyGrammarPointVO | null>(null);
      const loading = ref(false);
      /** 断网提示与重试入口（F-BASE-08） */
      const netError = ref<StudyNetError | null>(null);
      /** 重试动作：断网后点「重试」重放上一次失败的服务端请求（V0 不做断网补交） */
      const retryAction = ref<null | (() => Promise<void>)>(null);

      const loadPoints = async () => {
        loading.value = true;
        try {
          const result = await fetchGrammarPoints();
          points.value = result?.items ?? [];
          netError.value = null;
          if (points.value.length > 0) {
            await onPick(points.value[0]);
          }
        } catch (e) {
          points.value = [];
          // F-BASE-08：断网 → 可读提示 +「重试」入口；业务错误 → 保留原来的提示语义
          const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
          netError.value = state.offline ? state : null;
          if (state.offline) {
            retryAction.value = () => loadPoints();
          } else {
            createMessage.warning('语法点没取到，稍后再看看');
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

      const onPick = async (point: StudyGrammarPointVO) => {
        current.value = point;
        try {
          current.value = await fetchGrammarPoint(point.grammarCode);
          netError.value = null;
        } catch (e) {
          // 详情取不到时保留列表里的摘要，不打断学习流；断网时补上提示与重试入口
          const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
          netError.value = state.offline ? state : null;
          if (state.offline) {
            retryAction.value = () => onPick(point);
          }
        }
      };

      const onNext = async () => {
        if (points.value.length === 0) {
          return;
        }
        const index = points.value.findIndex((p) => p.grammarCode === current.value?.grammarCode);
        const next = points.value[(index + 1) % points.value.length];
        await onPick(next);
      };

      onMounted(() => {
        loadPoints();
      });

      return { points, current, loading, netError, onPick, onNext, onRetry };
    },
  });
</script>

<style lang="less">
  .study-grammar {
    padding: 16px;

    &__spin {
      display: block;
      min-height: 240px;
    }

    &__list {
      margin: 0;
      padding: 0;
      list-style: none;
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    &__item {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      min-height: 44px;
      padding: 10px 16px;
      border: 1px solid @border-color-light;
      border-radius: 8px;
      background-color: @app-content-background;
      font-size: 16px;
      color: @text-color-base;
      cursor: pointer;

      &.is-active {
        border: 1px solid @primary-color;
        color: @primary-color;
      }
    }

    &__item-title {
      flex: 1 1 auto;
      min-width: 0;
      word-break: break-word;
    }

    &__item-tag {
      flex: 0 0 auto;
      font-size: 12px;
      color: @text-color-call-out;
    }

    &__detail {
      margin-top: 16px;
      padding: 16px;
      border: 1px solid @border-color-light;
      border-radius: 12px;
      background-color: @white;
    }

    &__title {
      margin: 0;
      font-size: 18px;
      font-weight: 600;
      line-height: 1.4;
      color: @text-color-base;
    }

    &__rule {
      margin: 12px 0 0;
      font-size: 16px;
      line-height: 1.5715;
      color: @text-color-base;
    }

    &__block {
      margin-top: 12px;
    }

    &__block-label {
      font-size: 14px;
      color: @text-color-call-out;
    }

    &__block-text {
      margin: 4px 0 0;
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
