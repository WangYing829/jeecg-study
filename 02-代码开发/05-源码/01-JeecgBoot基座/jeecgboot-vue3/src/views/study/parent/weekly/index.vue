<template>
  <StudyParentShell
    :child-name="childName"
    :child-code="childCode"
    :child-options="childOptions"
    @switch-child="onSwitch"
  >
    <a-spin :spinning="loading" class="study-parent-weekly__spin">
      <!-- 断网：可读提示 +「重试」入口（F-BASE-08：V0 不做离线补交，只重试） -->
      <a-alert
        v-if="netError"
        class="study-parent-weekly__net-error"
        type="warning"
        show-icon
        :message="netError.message"
      >
        <template #action>
          <a-button size="small" @click="onRetry">重试</a-button>
        </template>
      </a-alert>

      <!-- 区间概览：只展示当前孩子自己的客观数据（R-04） -->
      <section class="study-parent-weekly__section">
        <h2 class="study-parent-weekly__section-title">这段时间的总体情况</h2>
        <StudyStatCards :items="statItems" />
      </section>

      <section class="study-parent-weekly__section">
        <h2 class="study-parent-weekly__section-title">
          逐日记录<span v-if="rangeText" class="study-parent-weekly__range">{{ rangeText }}</span>
        </h2>
        <StudyDataList :columns="dailyColumns" :data-source="dailyRows" row-key="date" />
      </section>

      <a-empty v-if="!loading && !hasData" class="study-parent-weekly__empty" description="暂时还没有学习记录" />
    </a-spin>

    <a-alert
      v-if="forbidden"
      class="study-parent-weekly__forbidden"
      type="warning"
      show-icon
      message="无权查看该孩子的数据"
    />
  </StudyParentShell>
</template>

<script lang="ts">
  import { computed, defineComponent, onMounted, ref, watch } from 'vue';
  import StudyParentShell from '/@/components/study/StudyParentShell.vue';
  import StudyStatCards from '/@/components/study/StudyStatCards.vue';
  import StudyDataList from '/@/components/study/StudyDataList.vue';
  import { fetchParentWeekly, type StudyParentDailyItemVO } from '../../api';
  import { isForbidden, useParentChild } from '../useParentChild';
  import { describeNetError, OFFLINE_HINT, RETRY_LABEL, type StudyNetError } from '../../net';

  /**
   * 家长端周报页（设计规范 §9.6）。
   *
   * <p>只读页面：只展示<b>当前孩子自己</b>的客观趋势与状态，
   * 不涉及其他任何人（红线 R-04 / 自检清单 #30）。</p>
   *
   * <p>与「学习报告」页的分工：总览页放掌握情况 / 错因分布 / 薄弱考点；
   * 本页只放<b>时间维度</b>的逐日记录与区间汇总。</p>
   */
  export default defineComponent({
    name: 'StudyParentWeeklyPage',
    components: { StudyParentShell, StudyStatCards, StudyDataList },
    setup() {
      const weekly = ref<{
        fromDate?: string;
        toDate?: string;
        activeDays?: number;
        totalEyeMinutes?: number;
        totalRestCount?: number;
        totalErrorCount?: number;
        totalWritingCount?: number;
        days?: StudyParentDailyItemVO[];
      } | null>(null);
      /** 断网提示与重试入口（F-BASE-08） */
      const netError = ref<StudyNetError | null>(null);
      /** 重试动作：断网后点「重试」重放上一次失败的服务端请求（V0 不做断网补交） */
      const retryAction = ref<null | (() => Promise<void>)>(null);

      const { childCode, childName, childOptions, loading, forbidden, loadChildren } = useParentChild(
        (state) => {
          // 孩子列表没取到时同样给出可读提示与重试入口，不再静默失败（F-BASE-08）
          if (state.offline) {
            netError.value = state;
            retryAction.value = () => loadChildren();
          }
        },
      );

      const dailyColumns = [
        { key: 'date', title: '日期', width: '120px' },
        { key: 'eyeMinutes', title: '用眼(分钟)' },
        { key: 'restCount', title: '休息次数' },
        { key: 'errorCount', title: '错题数' },
        { key: 'writingCount', title: '作文数' },
      ];

      const statItems = computed(() => [
        { key: 'active', title: '有学习的天数', value: weekly.value?.activeDays ?? 0 },
        { key: 'eye', title: '用眼合计(分钟)', value: weekly.value?.totalEyeMinutes ?? 0 },
        { key: 'rest', title: '休息次数', value: weekly.value?.totalRestCount ?? 0 },
        { key: 'error', title: '错题数', value: weekly.value?.totalErrorCount ?? 0 },
        { key: 'writing', title: '作文数', value: weekly.value?.totalWritingCount ?? 0 },
      ]);

      const dailyRows = computed(() => weekly.value?.days || []);
      const rangeText = computed(() => {
        const from = weekly.value?.fromDate;
        const to = weekly.value?.toDate;
        return from && to ? `${from} 至 ${to}` : '';
      });
      const hasData = computed(() => Boolean(weekly.value));

      const load = async () => {
        if (!childCode.value) {
          return;
        }
        loading.value = true;
        forbidden.value = false;
        try {
          weekly.value = await fetchParentWeekly(childCode.value);
        } catch (e) {
          weekly.value = null;
          forbidden.value = isForbidden(e);
          // F-BASE-08：断网 → 可读提示 +「重试」入口；越权提示口径不变（§11.3）
          const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
          netError.value = state.offline ? state : null;
          if (state.offline) {
            retryAction.value = () => load();
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

      const onSwitch = (value: string) => {
        childCode.value = value;
        load();
      };

      watch(childCode, () => {
        load();
      });

      onMounted(async () => {
        await loadChildren();
        await load();
      });

      return {
        childCode,
        childName,
        childOptions,
        loading,
        forbidden,
        statItems,
        dailyColumns,
        dailyRows,
        rangeText,
        hasData,
        netError,
        onSwitch,
        onRetry,
      };
    },
  });
</script>

<style lang="less">
  .study-parent-weekly {
    &__spin {
      display: block;
      min-height: 240px;
    }

    &__section {
      margin-bottom: 16px;
    }

    &__section-title {
      display: flex;
      align-items: baseline;
      flex-wrap: wrap;
      gap: 8px;
      margin: 0 0 12px;
      font-size: 15px;
      font-weight: 600;
      line-height: 1.4;
      color: @text-color-base;
    }

    &__range {
      font-size: 13px;
      font-weight: 400;
      color: @text-color-call-out;
    }

    &__empty {
      padding: 24px 0;
    }

    /* 断网提示条（F-BASE-08） */
    &__net-error {
      margin-bottom: 16px;
    }

    &__forbidden {
      margin-top: 16px;
    }
  }
</style>
