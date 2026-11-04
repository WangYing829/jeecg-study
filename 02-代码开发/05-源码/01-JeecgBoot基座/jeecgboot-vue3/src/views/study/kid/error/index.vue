<template>
  <StudyLayout module-title="我的错题本">
    <div class="study-kid-error">
      <!-- 断网：可读提示 +「重试」入口（F-BASE-08：V0 不做离线补交，只重试） -->
      <a-alert
        v-if="netError"
        class="study-kid-error__net-error"
        type="warning"
        show-icon
        :message="netError.message"
      >
        <template #action>
          <a-button size="small" @click="onRetry">重试</a-button>
        </template>
      </a-alert>

      <a-spin :spinning="loading" class="study-kid-error__spin">
        <div class="study-kid-error__head">
          <Icon icon="ant-design:edit-outlined" class="study-kid-error__head-icon" />
          <h2 class="study-kid-error__title">我记下的错题</h2>
        </div>

        <p v-if="!loading && rows.length === 0" class="study-kid-error__hint">还没有记下的错题</p>
        <StudyDataList
          v-else
          mode="card"
          :columns="columns"
          :data-source="rows"
          row-key="errorCode"
        >
          <template #cell-action="{ row }">
            <a-button class="study-kid-error__open" @click="onOpen(row.errorCode)">看看</a-button>
          </template>
        </StudyDataList>
      </a-spin>
    </div>

    <template #footer>
      <a-button
        type="primary"
        class="study-kid-error__primary"
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
  import {
    fetchCurrentKid,
    fetchErrorPool,
    type StudyErrorPoolItemVO,
  } from '../../api';
  import { describeNetError, OFFLINE_HINT, RETRY_LABEL, type StudyNetError } from '../../net';
  import { kidCauseLabel, kidDateLabel, kidMasteryLabel, kidModuleLabel } from '../kidCommon';

  /**
   * 孩子端错题本列表页（设计规范 §9.7 / §10.2）。
   *
   * 红线要点：
   * 只列自己的错题——孩子的身份来自会话，错题范围由服务端按登录账号判定（R-04 / ADR-018）；
   * 列表里不出现答案与讲解，只给卡点与状态（R-01 / R-02）；
   * 学习端用卡片流，不用表格（§9.7）。
   *
   * ⚠ 注释里不要写 HTML 标签：红线扫描取的是尖括号之间的文本（R04-3）。
   */
  export default defineComponent({
    name: 'StudyKidErrorListPage',
    components: { Icon, StudyLayout, StudyDataList },
    setup() {
      const router = useRouter();
      const { createMessage } = useMessage();

      const items = ref<StudyErrorPoolItemVO[]>([]);
      const loading = ref(false);
      /** 断网提示与重试入口（F-BASE-08） */
      const netError = ref<StudyNetError | null>(null);
      /** 重试动作：断网后点「重试」重放上一次失败的服务端请求 */
      const retryAction = ref<null | (() => Promise<void>)>(null);

      /** 错题列表的列 */
      const columns = [
        { key: 'sourceModule', title: '来自' },
        { key: 'errorCause', title: '卡在哪' },
        { key: 'masteryState', title: '掌握度' },
        { key: 'nextReviewAt', title: '下次复习' },
        { key: 'action', title: '选择' },
      ];

      /** 列表行：展示用的中文标签在页面里定 */
      const rows = computed<Record<string, any>[]>(() =>
        items.value.map((item) => ({
          errorCode: item.errorCode,
          sourceModule: kidModuleLabel(item.sourceModule),
          errorCause: kidCauseLabel(item.errorCause),
          masteryState: kidMasteryLabel(item.masteryState),
          nextReviewAt: kidDateLabel(item.nextReviewAt),
        })),
      );

      const load = async () => {
        loading.value = true;
        try {
          // 孩子身份来自会话；childCode 只用于请求参数，服务端仍按登录账号做数据范围判定
          const kid = await fetchCurrentKid();
          const result = await fetchErrorPool(kid?.childCode);
          items.value = Array.isArray(result) ? result : result?.items ?? [];
          netError.value = null;
        } catch (e) {
          // F-BASE-08：断网 → 可读提示 +「重试」入口；业务错误 → 保留原来的提示语义
          const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
          netError.value = state.offline ? state : null;
          if (state.offline) {
            retryAction.value = () => load();
          } else {
            createMessage.warning('错题本没取到，稍后再看看');
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

      /** 看某一条错题的详情 */
      const onOpen = (code: string) => {
        router.push(`/study/kid/error/${code}`);
      };

      /** 底部主按钮：进入今日复习 */
      const onGoReview = () => {
        router.push('/study/kid/review');
      };

      onMounted(() => {
        load();
      });

      return {
        columns,
        rows,
        loading,
        netError,
        onRetry,
        onOpen,
        onGoReview,
      };
    },
  });
</script>

<style lang="less">
  .study-kid-error {
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

    /* 看详情：点击目标 ≥44px（§10.1） */
    &__open {
      min-height: 44px;
      font-size: 14px;
      border-radius: 8px;
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
