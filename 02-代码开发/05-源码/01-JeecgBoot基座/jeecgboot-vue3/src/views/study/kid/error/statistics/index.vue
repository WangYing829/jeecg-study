<template>
  <StudyLayout module-title="错题统计">
    <div class="study-kid-error-statistics">
      <!-- 断网：可读提示 +「重试」入口（F-BASE-08：V0 不做离线补交，只重试） -->
      <a-alert
        v-if="netError"
        class="study-kid-error-statistics__net-error"
        type="warning"
        show-icon
        :message="netError.message"
      >
        <template #action>
          <a-button size="small" @click="onRetry">重试</a-button>
        </template>
      </a-alert>

      <a-spin :spinning="loading" class="study-kid-error-statistics__spin">
        <div class="study-kid-error-statistics__head">
          <Icon icon="ant-design:pie-chart-outlined" class="study-kid-error-statistics__head-icon" />
          <h2 class="study-kid-error-statistics__title">我卡在哪</h2>
        </div>

        <section class="study-kid-error-statistics__section">
          <StudyStatCards :items="summaryItems" :columns-per-row="2" />
        </section>

        <section class="study-kid-error-statistics__section">
          <h3 class="study-kid-error-statistics__subtitle">每种卡点出现了多少次</h3>
          <p v-if="!loading && causeRows.length === 0" class="study-kid-error-statistics__hint">
            还没有可以统计的错题
          </p>
          <StudyDataList
            v-else
            mode="card"
            :columns="causeColumns"
            :data-source="causeRows"
            row-key="cause"
          />
        </section>
      </a-spin>
    </div>

    <template #footer>
      <a-button
        type="primary"
        class="study-kid-error-statistics__primary"
        :loading="loading"
        @click="onGoList"
      >
        回到我的错题本
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
  import { fetchCurrentKid, fetchErrorStatistics } from '../../../api';
  import { describeNetError, OFFLINE_HINT, RETRY_LABEL, type StudyNetError } from '../../../net';

  /**
   * 孩子端错题统计页（设计规范 §10.2）。
   *
   * 红线要点：
   * 只统计自己的错题（孩子的身份来自会话，服务端按登录账号做数据范围判定，R-04 / ADR-018）；
   * 只给卡点的种类与次数，是客观计数，不给分数，也不与任何人横向比较（R-04）；
   * 错因名称是中性表述，不给孩子贴负面标签（§10.3）。
   *
   * ⚠ 注释里不要写 HTML 标签：红线扫描取的是尖括号之间的文本（R04-3）。
   */
  export default defineComponent({
    name: 'StudyKidErrorStatisticsPage',
    components: { Icon, StudyLayout, StudyDataList, StudyStatCards },
    setup() {
      const router = useRouter();
      const { createMessage } = useMessage();

      /** 错因 → 次数（服务端返回的是 Map） */
      const statistics = ref<Record<string, number>>({});
      const loading = ref(false);
      /** 断网提示与重试入口（F-BASE-08） */
      const netError = ref<StudyNetError | null>(null);
      /** 重试动作：断网后点「重试」重放上一次失败的服务端请求 */
      const retryAction = ref<null | (() => Promise<void>)>(null);

      /** 错因分布的列 */
      const causeColumns = [
        { key: 'cause', title: '卡在哪' },
        { key: 'count', title: '出现次数' },
      ];

      /** 错因分布行：次数多的排在前面 */
      const causeRows = computed<Record<string, any>[]>(() =>
        Object.entries(statistics.value || {})
          .map(([cause, count]) => ({ cause, count }))
          .sort((a, b) => b.count - a.count),
      );

      /** 概览：错题总数 / 出现过的卡点种类 / 最多的那一种 */
      const summaryItems = computed(() => {
        const rows = causeRows.value;
        const total = rows.reduce((sum, row) => sum + (Number(row.count) || 0), 0);
        const kinds = rows.filter((row) => Number(row.count) > 0).length;
        const top = rows.find((row) => Number(row.count) > 0);
        return [
          { key: 'total', title: '记下的错题', value: total },
          { key: 'kinds', title: '出现过的卡点', value: kinds },
          { key: 'top', title: '最常卡的地方', value: top ? top.cause : '还没有' },
        ];
      });

      const load = async () => {
        loading.value = true;
        try {
          // 孩子身份来自会话；childCode 只用于请求参数，服务端仍按登录账号做数据范围判定
          const kid = await fetchCurrentKid();
          const result = await fetchErrorStatistics(kid?.childCode);
          statistics.value = result || {};
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

      /** 底部主按钮：回到错题本列表 */
      const onGoList = () => {
        router.push('/study/kid/error');
      };

      onMounted(() => {
        load();
      });

      return {
        causeColumns,
        causeRows,
        summaryItems,
        loading,
        netError,
        onRetry,
        onGoList,
      };
    },
  });
</script>

<style lang="less">
  .study-kid-error-statistics {
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

    &__subtitle {
      margin: 0 0 12px;
      font-size: 16px;
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
