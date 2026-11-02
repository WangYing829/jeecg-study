<template>
  <StudyParentShell
    :child-name="childName"
    :child-code="childCode"
    :child-options="childOptions"
    @switch-child="onSwitch"
  >
    <a-spin :spinning="loading" class="study-parent-error__spin">
      <!-- 断网：可读提示 +「重试」入口（F-BASE-08：V0 不做离线补交，只重试） -->
      <a-alert
        v-if="netError"
        class="study-parent-error__net-error"
        type="warning"
        show-icon
        :message="netError.message"
      >
        <template #action>
          <a-button size="small" @click="onRetry">重试</a-button>
        </template>
      </a-alert>

      <section class="study-parent-error__section">
        <h2 class="study-parent-error__section-title">错因分布</h2>
        <p class="study-parent-error__section-desc">这里只显示卡点的类型和出现次数，不展示题目内容</p>
        <StudyDataList :columns="causeColumns" :data-source="causeRows" row-key="cause" />
      </section>

      <section class="study-parent-error__section">
        <h2 class="study-parent-error__section-title">还需要多练的考点</h2>
        <StudyDataList :columns="weakColumns" :data-source="weakRows" row-key="pointCode" />
      </section>

      <a-empty v-if="!loading && causeRows.length === 0 && weakRows.length === 0" class="study-parent-error__empty" description="暂时没有错题记录" />
    </a-spin>

    <a-alert
      v-if="forbidden"
      class="study-parent-error__forbidden"
      type="warning"
      show-icon
      message="无权查看该孩子的数据"
    />
  </StudyParentShell>
</template>

<script lang="ts">
  import { computed, defineComponent, onMounted, ref, watch } from 'vue';
  import StudyParentShell from '/@/components/study/StudyParentShell.vue';
  import StudyDataList from '/@/components/study/StudyDataList.vue';
  import {
    fetchParentErrors,
    type StudyParentErrorCauseVO,
    type StudyParentErrorReportVO,
    type StudyParentWeakPointVO,
  } from '../../api';
  import { isForbidden, useParentChild } from '../useParentChild';
  import { describeNetError, OFFLINE_HINT, RETRY_LABEL, type StudyNetError } from '../../net';

  /**
   * 家长端错因分布页（设计规范 §9.6）。
   *
   * <p>只读页面：只给出<b>维度名与条数</b>，不展示题目、作答与讲解
   * （红线 R-02 / R-04）。窄屏下表格自动转卡片（TC-08-19）。</p>
   */
  export default defineComponent({
    name: 'StudyParentErrorPage',
    components: { StudyParentShell, StudyDataList },
    setup() {
      const report = ref<StudyParentErrorReportVO | null>(null);
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

      const causeColumns = [
        { key: 'causeName', title: '卡在哪里', width: '160px' },
        { key: 'count', title: '出现次数' },
      ];
      const weakColumns = [
        { key: 'pointName', title: '考点', width: '160px' },
        { key: 'errorCount', title: '错题数' },
      ];

      const causeRows = computed<StudyParentErrorCauseVO[]>(() => report.value?.causes || []);
      const weakRows = computed<StudyParentWeakPointVO[]>(() => report.value?.weakPoints || []);

      const load = async () => {
        if (!childCode.value) {
          return;
        }
        loading.value = true;
        forbidden.value = false;
        try {
          report.value = await fetchParentErrors(childCode.value);
        } catch (e) {
          report.value = null;
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
        causeColumns,
        causeRows,
        weakColumns,
        weakRows,
        netError,
        onSwitch,
        onRetry,
      };
    },
  });
</script>

<style lang="less">
  .study-parent-error {
    &__spin {
      display: block;
      min-height: 240px;
    }

    &__section {
      margin-bottom: 16px;
    }

    &__section-title {
      margin: 0 0 4px;
      font-size: 15px;
      font-weight: 600;
      line-height: 1.4;
      color: @text-color-base;
    }

    &__section-desc {
      margin: 0 0 12px;
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

    &__forbidden {
      margin-top: 16px;
    }
  }
</style>
