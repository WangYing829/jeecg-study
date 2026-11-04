<template>
  <StudyLayout module-title="今日首页">
    <div class="study-kid-home">
      <!-- 断网：可读提示 +「重试」入口（F-BASE-08：V0 不做离线补交，只重试） -->
      <a-alert
        v-if="netError"
        class="study-kid-home__net-error"
        type="warning"
        show-icon
        :message="netError.message"
      >
        <template #action>
          <a-button size="small" @click="onRetry">重试</a-button>
        </template>
      </a-alert>

      <a-spin :spinning="loading" class="study-kid-home__spin">
        <section class="study-kid-home__section">
          <div class="study-kid-home__head">
            <Icon icon="ant-design:home-outlined" class="study-kid-home__head-icon" />
            <h2 class="study-kid-home__title">今天练了多少</h2>
          </div>
          <StudyStatCards :items="todayItems" :columns-per-row="2" />
        </section>

        <section class="study-kid-home__section">
          <div class="study-kid-home__head">
            <Icon icon="ant-design:schedule-outlined" class="study-kid-home__head-icon" />
            <h2 class="study-kid-home__title">今天要复习的</h2>
          </div>
          <p v-if="!loading && queueRows.length === 0" class="study-kid-home__hint">今天没有要复习的内容</p>
          <StudyDataList
            v-else
            mode="card"
            :columns="queueColumns"
            :data-source="queueRows"
            row-key="errorCode"
          />
        </section>
      </a-spin>
    </div>

    <template #footer>
      <a-button
        type="primary"
        class="study-kid-home__primary"
        :loading="loading"
        @click="onGoReview"
      >
        去复习
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
  import StudyDataList from '/@/components/study/StudyDataList.vue';
  import StudyStatCards from '/@/components/study/StudyStatCards.vue';
  import {
    fetchReviewQueue,
    fetchTodayStats,
    type StudyReviewQueueItemVO,
    type StudyTodayStatsVO,
  } from '../../api';
  import { describeNetError, OFFLINE_HINT, RETRY_LABEL, type StudyNetError } from '../../net';
  import { kidDateLabel, kidModuleLabel } from '../kidCommon';

  /**
   * 孩子端今日首页（设计规范 §10.2）。
   *
   * 红线要点：
   * 只给自己的客观进度（题数 / 会做 / 还要练 / 用时），不给分数，也不与任何人横向比较（R-04）；
   * 不出现任何他人标识（R-04）；
   * 一屏一任务：概览 + 今日复习入口，底部只有一个主按钮（§10.2）。
   *
   * ⚠ 注释里不要写 HTML 标签：红线扫描取的是尖括号之间的文本，
   *   注释里的标签会让注释散文被当成用户可见文案扫（R04-3）。
   */
  export default defineComponent({
    name: 'StudyKidHomePage',
    components: { Icon, StudyLayout, StudyDataList, StudyStatCards },
    setup() {
      const router = useRouter();
      const { createMessage } = useMessage();

      const today = ref<StudyTodayStatsVO | null>(null);
      const queueRaw = ref<StudyReviewQueueItemVO[]>([]);
      const loading = ref(false);
      /** 断网提示与重试入口（F-BASE-08） */
      const netError = ref<StudyNetError | null>(null);
      /** 重试动作：断网后点「重试」重放上一次失败的服务端请求（V0 不做断网补交） */
      const retryAction = ref<null | (() => Promise<void>)>(null);

      /** 今日概览：只统计自己的客观进度 */
      const todayItems = computed(() => [
        { key: 'total', title: '今天做的题', value: today.value?.totalCount ?? 0 },
        { key: 'solved', title: '已经会做', value: today.value?.correctCount ?? 0 },
        { key: 'again', title: '还要再练', value: today.value?.wrongCount ?? 0 },
        { key: 'time', title: '用时（分钟）', value: today.value?.durationMinutes ?? 0 },
      ]);

      /** 今日复习队列的列（学习端用卡片形态，§9.7） */
      const queueColumns = [
        { key: 'sourceModule', title: '来自' },
        { key: 'contentId', title: '题目编号' },
        { key: 'queueDate', title: '排期' },
      ];

      /** 队列行：展示用的中文标签在页面里定，不把编码直接甩给孩子 */
      const queueRows = computed<Record<string, any>[]>(() =>
        queueRaw.value.map((item) => ({
          errorCode: item.errorCode,
          sourceModule: kidModuleLabel(item.sourceModule),
          contentId: item.contentId,
          queueDate: kidDateLabel(item.queueDate),
        })),
      );

      const load = async () => {
        loading.value = true;
        try {
          const stats = await fetchTodayStats();
          today.value = stats;
          const queue = await fetchReviewQueue();
          queueRaw.value = Array.isArray(queue) ? queue : queue?.items ?? [];
          netError.value = null;
        } catch (e) {
          // F-BASE-08：断网 → 可读提示 +「重试」入口；业务错误 → 保留原来的提示语义
          const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
          netError.value = state.offline ? state : null;
          if (state.offline) {
            retryAction.value = () => load();
          } else {
            createMessage.warning('今天的进度没取到，稍后再看看');
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

      /** 底部主按钮：进入今日复习 */
      const onGoReview = () => {
        router.push('/study/kid/review');
      };

      onMounted(() => {
        load();
      });

      return {
        todayItems,
        queueColumns,
        queueRows,
        loading,
        netError,
        onRetry,
        onGoReview,
      };
    },
  });
</script>

<style lang="less">
  .study-kid-home {
    padding: 16px;

    &__spin {
      display: block;
      min-height: 240px;
    }

    &__section {
      margin-bottom: 16px;
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
      font-size: 16px;
      font-weight: 600;
      line-height: 1.4;
      color: @text-color-base;
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
