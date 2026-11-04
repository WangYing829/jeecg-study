<template>
  <StudyLayout module-title="背诵统计">
    <div class="study-kid-word-stats">
      <!-- 断网：可读提示 +「重试」入口（F-BASE-08：V0 不做离线补交，只重试） -->
      <a-alert
        v-if="netError"
        class="study-kid-word-stats__net-error"
        type="warning"
        show-icon
        :message="netError.message"
      >
        <template #action>
          <a-button size="small" @click="onRetry">重试</a-button>
        </template>
      </a-alert>

      <a-spin :spinning="loading" class="study-kid-word-stats__spin">
        <div class="study-kid-word-stats__head">
          <Icon icon="ant-design:bar-chart-outlined" class="study-kid-word-stats__head-icon" />
          <h2 class="study-kid-word-stats__title">我的背诵情况</h2>
        </div>

        <section class="study-kid-word-stats__section">
          <StudyStatCards :items="statItems" :columns-per-row="2" />
        </section>

        <p class="study-kid-word-stats__hint">
          这些数字只来自你自己的学习记录，随时可以继续背
        </p>
      </a-spin>
    </div>

    <template #footer>
      <a-button
        type="primary"
        class="study-kid-word-stats__primary"
        :loading="loading"
        @click="onGoWord"
      >
        去背单词
      </a-button>
    </template>
  </StudyLayout>
</template>

<script lang="ts">
  import { computed, defineComponent, onMounted, ref } from 'vue';
  import { useRouter } from 'vue-router';
  import { Icon } from '/@/components/Icon';
  import { useMessage } from '/@/hooks/web/useMessage';
  import StudyLayout from '/@/layouts/study/index.vue';
  import StudyStatCards from '/@/components/study/StudyStatCards.vue';
  import { fetchWordStats, type StudyWordStatsVO } from '../../../api';
  import { describeNetError, OFFLINE_HINT, RETRY_LABEL, type StudyNetError } from '../../../net';

  /** 线别：课内（IN）。V1 范围是课内线优先 */
  const LINE_TYPE = 'IN';

  /**
   * 孩子端背诵统计页（设计规范 §10.2）。
   *
   * 红线要点：
   * 只给自己的客观计数，不与任何人横向比较（R-04）；
   * 不出现任何他人标识，也不出现分数类字段（R-04 / R04-4）。
   *
   * ⚠ 注释里不要写 HTML 标签：红线扫描取的是尖括号之间的文本（R04-3）。
   */
  export default defineComponent({
    name: 'StudyKidWordStatsPage',
    components: { Icon, StudyLayout, StudyStatCards },
    setup() {
      const router = useRouter();
      const { createMessage } = useMessage();

      const stats = ref<StudyWordStatsVO | null>(null);
      const loading = ref(false);
      /** 断网提示与重试入口（F-BASE-08） */
      const netError = ref<StudyNetError | null>(null);
      /** 重试动作：断网后点「重试」重放上一次失败的服务端请求 */
      const retryAction = ref<null | (() => Promise<void>)>(null);

      /** 统计卡：只给客观计数 */
      const statItems = computed(() => [
        { key: 'total', title: '词表里的词', value: stats.value?.totalWords ?? 0 },
        { key: 'learned', title: '已经学会', value: stats.value?.learnedWords ?? 0 },
        { key: 'queue', title: '还要背', value: stats.value?.queueWords ?? 0 },
        { key: 'due', title: '今天到期', value: stats.value?.dueWords ?? 0 },
        { key: 'review', title: '等着复习', value: stats.value?.reviewPending ?? 0 },
        { key: 'streak', title: '连续天数', value: stats.value?.streakDays ?? 0 },
      ]);

      const load = async () => {
        loading.value = true;
        try {
          stats.value = await fetchWordStats(LINE_TYPE);
          netError.value = null;
        } catch (e) {
          // F-BASE-08：断网 → 可读提示 +「重试」入口；业务错误 → 保留原来的提示语义
          const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
          netError.value = state.offline ? state : null;
          if (state.offline) {
            retryAction.value = () => load();
          } else {
            createMessage.warning('统计没取到，稍后再看看');
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

      /** 底部主按钮：进入背单词页 */
      const onGoWord = () => {
        router.push('/study/word');
      };

      onMounted(() => {
        load();
      });

      return {
        statItems,
        loading,
        netError,
        onRetry,
        onGoWord,
      };
    },
  });
</script>

<style lang="less">
  .study-kid-word-stats {
    padding: 16px;

    &__spin {
      display: block;
      min-height: 240px;
    }

    &__head {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 12px;
    }

    &__head-icon {
      font-size: 20px;
      color: @primary-color;
    }

    &__title {
      margin: 0;
      font-size: 18px;
      font-weight: 600;
      line-height: 1.4;
      color: @text-color-base;
    }

    &__section {
      margin-bottom: 16px;
    }

    &__hint {
      margin: 0;
      padding: 12px 16px;
      border: 1px solid @border-color-light;
      border-radius: 8px;
      background-color: @app-content-background;
      font-size: 14px;
      line-height: 1.5715;
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
