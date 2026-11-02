<template>
  <StudyParentShell
    :child-name="childName"
    :child-code="childCode"
    :child-options="childOptions"
    @switch-child="onSwitch"
  >
    <a-spin :spinning="loading" class="study-parent-report__spin">
      <!-- 断网：可读提示 +「重试」入口（F-BASE-08：V0 不做离线补交，只重试） -->
      <a-alert
        v-if="netError"
        class="study-parent-report__net-error"
        type="warning"
        show-icon
        :message="netError.message"
      >
        <template #action>
          <a-button size="small" @click="onRetry">重试</a-button>
        </template>
      </a-alert>

      <!-- 客观指标：只展示当前孩子自己的用眼、休息与学习情况 -->
      <section class="study-parent-report__section">
        <h2 class="study-parent-report__section-title">这周的情况</h2>
        <StudyStatCards :items="statItems" />
      </section>

      <section class="study-parent-report__section">
        <h2 class="study-parent-report__section-title">近 7 天</h2>
        <StudyDataList :columns="dailyColumns" :data-source="dailyRows" row-key="date" />
      </section>

      <section class="study-parent-report__section">
        <h2 class="study-parent-report__section-title">掌握情况</h2>
        <StudyDataList :columns="masteryColumns" :data-source="masteryRows" row-key="bucket" />
      </section>

      <section class="study-parent-report__section">
        <h2 class="study-parent-report__section-title">常见的卡点</h2>
        <StudyDataList :columns="causeColumns" :data-source="causeRows" row-key="cause" />
      </section>

      <section class="study-parent-report__section">
        <h2 class="study-parent-report__section-title">还需要多练的考点</h2>
        <StudyDataList :columns="weakColumns" :data-source="weakRows" row-key="pointCode" />
      </section>

      <a-empty v-if="!loading && !hasData" class="study-parent-report__empty" description="暂时还没有学习记录" />
    </a-spin>

    <a-alert
      v-if="forbidden"
      class="study-parent-report__forbidden"
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
  import {
    fetchParentOverview,
    fetchParentWeekly,
    type StudyParentDailyItemVO,
    type StudyParentErrorCauseVO,
    type StudyParentOverviewVO,
    type StudyParentWeakPointVO,
  } from '../../api';
  import { isForbidden, useParentChild } from '../useParentChild';
  import { describeNetError, OFFLINE_HINT, RETRY_LABEL, type StudyNetError } from '../../net';

  /**
   * 家长端周报 / 总览页（设计规范 §9.6）。
   *
   * <p>只读页面：只展示<b>当前孩子自己</b>的客观趋势与状态，
   * 不涉及其他任何人（红线 R-04 / 自检清单 #30）。</p>
   */
  export default defineComponent({
    name: 'StudyParentReportPage',
    components: { StudyParentShell, StudyStatCards, StudyDataList },
    setup() {
      const overview = ref<StudyParentOverviewVO | null>(null);
      const weekly = ref<{ days?: StudyParentDailyItemVO[] } | null>(null);
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
      const masteryColumns = [
        { key: 'bucket', title: '状态', width: '120px' },
        { key: 'count', title: '题目数' },
      ];
      const causeColumns = [
        { key: 'causeName', title: '卡在哪里', width: '160px' },
        { key: 'count', title: '出现次数' },
      ];
      const weakColumns = [
        { key: 'pointName', title: '考点', width: '160px' },
        { key: 'errorCount', title: '错题数' },
      ];

      const statItems = computed(() => {
        const eye = overview.value?.eyeUsage;
        const mastery = overview.value?.mastery;
        return [
          { key: 'today', title: '今天用眼(分钟)', value: eye?.todayMinutes ?? 0 },
          { key: 'week', title: '本周用眼(分钟)', value: eye?.weekMinutes ?? 0 },
          { key: 'rest', title: '休息次数', value: eye?.restCount ?? 0 },
          { key: 'streak', title: '连续学习(天)', value: overview.value?.streak?.days ?? 0 },
          { key: 'mastered', title: '已掌握', value: mastery?.mastered ?? 0 },
          { key: 'learning', title: '学习中', value: mastery?.learning ?? 0 },
          { key: 'familiar', title: '比较熟', value: mastery?.familiar ?? 0 },
          { key: 'unknown', title: '还没学', value: mastery?.unknown ?? 0 },
        ];
      });

      const dailyRows = computed(() => weekly.value?.days || []);
      const masteryRows = computed(() => {
        const mastery = overview.value?.mastery;
        return [
          { bucket: '已掌握', count: mastery?.mastered ?? 0 },
          { bucket: '比较熟', count: mastery?.familiar ?? 0 },
          { bucket: '学习中', count: mastery?.learning ?? 0 },
          { bucket: '还没学', count: mastery?.unknown ?? 0 },
        ];
      });
      const causeRows = computed(() => overview.value?.errorTop || []);
      const weakRows = computed(() => overview.value?.weakPoints || []);
      const hasData = computed(() => Boolean(overview.value || weekly.value));

      const load = async () => {
        if (!childCode.value) {
          return;
        }
        loading.value = true;
        forbidden.value = false;
        try {
          const [detail, week] = await Promise.all([
            fetchParentOverview(childCode.value),
            fetchParentWeekly(childCode.value),
          ]);
          overview.value = detail;
          weekly.value = week;
        } catch (e) {
          overview.value = null;
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
        masteryColumns,
        masteryRows,
        causeColumns,
        causeRows,
        weakColumns,
        weakRows,
        hasData,
        netError,
        onSwitch,
        onRetry,
      };
    },
  });
</script>

<style lang="less">
  .study-parent-report {
    &__spin {
      display: block;
      min-height: 240px;
    }

    &__section {
      margin-bottom: 16px;
    }

    &__section-title {
      margin: 0 0 12px;
      font-size: 15px;
      font-weight: 600;
      line-height: 1.4;
      color: @text-color-base;
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
