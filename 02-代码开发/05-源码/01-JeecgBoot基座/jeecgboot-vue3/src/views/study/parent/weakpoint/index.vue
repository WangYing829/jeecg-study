<template>
  <StudyParentShell
    :child-name="childName"
    :child-code="childCode"
    :child-options="childOptions"
    @switch-child="onSwitch"
  >
    <a-spin :spinning="loading" class="study-parent-weak__spin">
      <!-- 断网：可读提示 +「重试」入口（F-BASE-08：V0 不做离线补交，只重试） -->
      <a-alert
        v-if="netError"
        class="study-parent-weak__net-error"
        type="warning"
        show-icon
        :message="netError.message"
      >
        <template #action>
          <a-button size="small" @click="onRetry">重试</a-button>
        </template>
      </a-alert>

      <!--
        薄弱考点：只展示当前孩子自己的考点掌握情况。
        ⚠ 不出现任何同类比较的字段或措辞，也不与任何人比较（R-04）。
      -->
      <section class="study-parent-weak__section">
        <h2 class="study-parent-weak__section-title">还需要多练的考点</h2>
        <p class="study-parent-weak__hint">按这个孩子自己的练习记录整理，只看哪些考点还不太熟。</p>
        <StudyDataList :columns="weakColumns" :data-source="weakRows" row-key="pointCode" />
      </section>

      <a-empty
        v-if="!loading && !hasData"
        class="study-parent-weak__empty"
        description="暂时还没有练习记录，多练几次就能看到考点分布"
      />
    </a-spin>

    <a-alert
      v-if="forbidden"
      class="study-parent-weak__forbidden"
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
  import { fetchParentErrors, type StudyParentWeakPointVO } from '../../api';
  import { isForbidden, useParentChild } from '../useParentChild';
  import { describeNetError, OFFLINE_HINT, RETRY_LABEL, type StudyNetError } from '../../net';

  /** 掌握状态 → 中性文案（不出现「差」「落后」这类负向评价） */
  const MASTERY_TEXT: Record<number, string> = {
    1: '还没学',
    2: '学习中',
    3: '比较熟',
    4: '已掌握',
  };

  /**
   * 家长端薄弱考点页（设计规范 §9.6）。
   *
   * <p>只读页面：只展示<b>当前孩子自己</b>的考点掌握情况（R-04 / 自检清单 #30）。
   * 只给这个孩子自己的客观状态，不与任何人比较。</p>
   */
  export default defineComponent({
    name: 'StudyParentWeakPointPage',
    components: { StudyParentShell, StudyDataList },
    setup() {
      const weakPoints = ref<StudyParentWeakPointVO[]>([]);
      /** 断网提示与重试入口（F-BASE-08） */
      const netError = ref<StudyNetError | null>(null);
      /** 重试动作：断网后点「重试」重放上一次失败的服务端请求（V0 不做断网补交） */
      const retryAction = ref<null | (() => Promise<void>)>(null);

      const { childCode, childName, childOptions, loading, forbidden, loadChildren } = useParentChild(
        (state) => {
          if (state.offline) {
            netError.value = state;
            retryAction.value = () => loadChildren();
          }
        },
      );

      const weakColumns = [
        { key: 'pointName', title: '考点', width: '160px' },
        { key: 'masteryText', title: '掌握情况', width: '120px' },
        { key: 'errorCount', title: '错题数' },
      ];

      const weakRows = computed(() =>
        (weakPoints.value || []).map((item) => ({
          pointCode: item.pointCode,
          pointName: item.pointName || item.pointCode || '未命名考点',
          errorCount: item.errorCount ?? 0,
          masteryText: MASTERY_TEXT[item.masteryState as number] || '还在熟悉',
        })),
      );
      const hasData = computed(() => weakRows.value.length > 0);

      const load = async () => {
        if (!childCode.value) {
          return;
        }
        loading.value = true;
        forbidden.value = false;
        try {
          const detail = await fetchParentErrors(childCode.value);
          weakPoints.value = detail?.weakPoints || [];
        } catch (e) {
          weakPoints.value = [];
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
  .study-parent-weak {
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

    &__hint {
      margin: 0 0 12px;
      font-size: 13px;
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
