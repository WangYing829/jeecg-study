<template>
  <StudyLayout module-title="今日复习">
    <div class="study-kid-review">
      <!-- 断网：可读提示 +「重试」入口（F-BASE-08：V0 不做离线补交，只重试） -->
      <a-alert
        v-if="netError"
        class="study-kid-review__net-error"
        type="warning"
        show-icon
        :message="netError.message"
      >
        <template #action>
          <a-button size="small" @click="onRetry">重试</a-button>
        </template>
      </a-alert>

      <a-spin :spinning="loading" class="study-kid-review__spin">
        <section class="study-kid-review__section">
          <div class="study-kid-review__head">
            <Icon icon="ant-design:schedule-outlined" class="study-kid-review__head-icon" />
            <h2 class="study-kid-review__title">今天要复习的</h2>
          </div>
          <p v-if="!loading && queueRows.length === 0" class="study-kid-review__hint">今天没有要复习的内容</p>
          <StudyDataList
            v-else
            mode="card"
            :columns="queueColumns"
            :data-source="queueRows"
            row-key="errorCode"
          >
            <template #cell-action="{ row }">
              <a-button
                class="study-kid-review__pick"
                :class="{ 'is-active': row.errorCode === selectedCode }"
                @click="onSelect(row.errorCode)"
              >
                {{ row.errorCode === selectedCode ? '正在复习' : '选它' }}
              </a-button>
            </template>
          </StudyDataList>
        </section>

        <!-- 已选中的那一条：先做，再标记结果（R-01：判定由服务端给，这里只提交孩子的自评） -->
        <section v-if="selectedRow" class="study-kid-review__section">
          <div class="study-kid-review__head">
            <Icon icon="ant-design:edit-outlined" class="study-kid-review__head-icon" />
            <h2 class="study-kid-review__title">这一项做得怎么样</h2>
          </div>
          <p class="study-kid-review__meta">
            {{ selectedRow.sourceModule }} · {{ selectedRow.contentId }}
          </p>

          <div class="study-kid-review__choices">
            <a-button
              class="study-kid-review__choice"
              :class="{ 'is-active': outcomeSelected && selfSolved }"
              @click="onPickOutcome(true)"
            >
              做对了
            </a-button>
            <a-button
              class="study-kid-review__choice"
              :class="{ 'is-active': outcomeSelected && !selfSolved }"
              @click="onPickOutcome(false)"
            >
              还要再看
            </a-button>
          </div>

          <!-- 还需要再看时要补错因（服务端缺错因会返回 4001，这里先收齐） -->
          <div v-if="needCause" class="study-kid-review__cause">
            <p class="study-kid-review__cause-title">先看看是哪一步卡住了</p>
            <div class="study-kid-review__cause-list">
              <a-button
                v-for="item in KID_CAUSE_OPTIONS"
                :key="item"
                class="study-kid-review__cause-item"
                :class="{ 'is-active': cause === item }"
                @click="onPickCause(item)"
              >
                {{ item }}
              </a-button>
            </div>
          </div>
        </section>

        <section v-if="doneMessage" class="study-kid-review__section">
          <p class="study-kid-review__done">{{ doneMessage }}</p>
        </section>

        <section class="study-kid-review__section">
          <h2 class="study-kid-review__title">接下来 7 天</h2>
          <p v-if="!loading && upcomingRows.length === 0" class="study-kid-review__hint">接下来没有排期</p>
          <StudyDataList
            v-else
            mode="card"
            :columns="upcomingColumns"
            :data-source="upcomingRows"
            row-key="errorCode"
          />
        </section>
      </a-spin>
    </div>

    <template #footer>
      <a-button
        type="primary"
        class="study-kid-review__primary"
        :loading="submitting"
        :disabled="!canSubmit"
        @click="onSubmit"
      >
        完成这一项
      </a-button>
    </template>
  </StudyLayout>
</template>

<script lang="ts">
  import { computed, defineComponent, onMounted, ref } from 'vue';
  import { Icon } from '/@/components/Icon';
  import { useMessage } from '/@/hooks/web/useMessage';
  import StudyLayout from '/@/layouts/study/index.vue';
  import StudyDataList from '/@/components/study/StudyDataList.vue';
  import {
    completeReviewItem,
    fetchReviewQueue,
    fetchReviewUpcoming,
    type StudyReviewQueueItemVO,
  } from '../../api';
  import { describeNetError, OFFLINE_HINT, RETRY_LABEL, type StudyNetError } from '../../net';
  import { KID_CAUSE_OPTIONS, kidDateLabel, kidModuleLabel } from '../kidCommon';

  /**
   * 孩子端今日复习页（设计规范 §10.2）。
   *
   * 红线要点：
   * 复习结果由服务端推进排期（完成接口返回连对次数与下次复习时间），前端不本地判分（R-02）；
   * 答错必须补错因（服务端缺错因返回 4001），错因五维是中性表述（R-02 / §10.3）；
   * 只显示自己的复习队列，不出现任何他人标识（R-04）。
   *
   * ⚠ 注释里不要写 HTML 标签：红线扫描取的是尖括号之间的文本（R04-3）。
   */
  export default defineComponent({
    name: 'StudyKidReviewPage',
    components: { Icon, StudyLayout, StudyDataList },
    setup() {
      const { createMessage } = useMessage();

      const queueRaw = ref<StudyReviewQueueItemVO[]>([]);
      const upcomingRaw = ref<StudyReviewQueueItemVO[]>([]);
      const loading = ref(false);
      const submitting = ref(false);
      /** 当前选中的复习项 */
      const selectedCode = ref('');
      /** 孩子对这一项的自评：做对了没有 */
      const selfSolved = ref(false);
      /** 是否已经做过选择（没选不能提交） */
      const outcomeSelected = ref(false);
      /** 还需要再看时补的错因 */
      const cause = ref('');
      /** 服务端返回的完成提示 */
      const doneMessage = ref('');
      /** 断网提示与重试入口（F-BASE-08） */
      const netError = ref<StudyNetError | null>(null);
      /** 重试动作：断网后点「重试」重放上一次失败的服务端请求 */
      const retryAction = ref<null | (() => Promise<void>)>(null);

      /** 今日队列的列 */
      const queueColumns = [
        { key: 'sourceModule', title: '来自' },
        { key: 'contentId', title: '题目编号' },
        { key: 'action', title: '选择' },
      ];

      /** 未来 7 天排期的列 */
      const upcomingColumns = [
        { key: 'sourceModule', title: '来自' },
        { key: 'contentId', title: '题目编号' },
        { key: 'queueDate', title: '排期' },
      ];

      /** 今日队列行（展示用的中文标签在页面里定） */
      const queueRows = computed<Record<string, any>[]>(() =>
        queueRaw.value.map((item) => ({
          errorCode: item.errorCode,
          sourceModule: kidModuleLabel(item.sourceModule),
          contentId: item.contentId,
        })),
      );

      /** 未来 7 天排期行 */
      const upcomingRows = computed<Record<string, any>[]>(() =>
        upcomingRaw.value.map((item) => ({
          errorCode: item.errorCode,
          sourceModule: kidModuleLabel(item.sourceModule),
          contentId: item.contentId,
          queueDate: kidDateLabel(item.queueDate),
        })),
      );

      /** 当前选中的那一项 */
      const selectedRow = computed(() =>
        queueRows.value.find((row) => row.errorCode === selectedCode.value),
      );

      /** 还需要补错因 */
      const needCause = computed(() => outcomeSelected.value && !selfSolved.value);

      /** 能不能提交：选中了 + 做了选择 + （需要的话）补了错因 */
      const canSubmit = computed(() => {
        if (!selectedCode.value || !outcomeSelected.value) {
          return false;
        }
        if (needCause.value && !cause.value) {
          return false;
        }
        return true;
      });

      const load = async () => {
        loading.value = true;
        try {
          const queue = await fetchReviewQueue();
          queueRaw.value = Array.isArray(queue) ? queue : queue?.items ?? [];
          const upcoming = await fetchReviewUpcoming();
          upcomingRaw.value = Array.isArray(upcoming) ? upcoming : upcoming?.items ?? [];
          netError.value = null;
        } catch (e) {
          // F-BASE-08：断网 → 可读提示 +「重试」入口；业务错误 → 保留原来的提示语义
          const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
          netError.value = state.offline ? state : null;
          if (state.offline) {
            retryAction.value = () => load();
          } else {
            createMessage.warning('复习清单没取到，稍后再看看');
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

      /** 选中一条复习项 */
      const onSelect = (code: string) => {
        selectedCode.value = code;
        selfSolved.value = false;
        outcomeSelected.value = false;
        cause.value = '';
        doneMessage.value = '';
      };

      /** 选择这一项的结果（孩子的自评，判定与排期仍由服务端给） */
      const onPickOutcome = (solved: boolean) => {
        selfSolved.value = solved;
        outcomeSelected.value = true;
        if (solved) {
          cause.value = '';
        }
      };

      /** 补错因 */
      const onPickCause = (value: string) => {
        cause.value = value;
      };

      /** 提交这一项：完成结果与排期都由服务端返回 */
      const onSubmit = async () => {
        if (!canSubmit.value) {
          return;
        }
        submitting.value = true;
        try {
          const payload: { correct: boolean; errorCause?: string } = { correct: selfSolved.value };
          if (cause.value) {
            payload.errorCause = cause.value;
          }
          const result = await completeReviewItem(selectedCode.value, payload);
          doneMessage.value = result?.message || '这一项已经记下来了';
          selectedCode.value = '';
          outcomeSelected.value = false;
          selfSolved.value = false;
          cause.value = '';
          await load();
        } catch (e) {
          // F-BASE-08：断网 → 可读提示 +「重试」入口；业务错误 → 保留原来的提示语义
          const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
          netError.value = state.offline ? state : null;
          if (state.offline) {
            retryAction.value = () => onSubmit();
          } else {
            createMessage.warning('这次没提交上，再试一次');
          }
        } finally {
          submitting.value = false;
        }
      };

      onMounted(() => {
        load();
      });

      return {
        KID_CAUSE_OPTIONS,
        queueColumns,
        queueRows,
        upcomingColumns,
        upcomingRows,
        selectedCode,
        selectedRow,
        selfSolved,
        outcomeSelected,
        cause,
        needCause,
        canSubmit,
        doneMessage,
        loading,
        submitting,
        netError,
        onRetry,
        onSelect,
        onPickOutcome,
        onPickCause,
        onSubmit,
      };
    },
  });
</script>

<style lang="less">
  .study-kid-review {
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
      margin: 0 0 12px;
      font-size: 16px;
      font-weight: 600;
      line-height: 1.4;
      color: @text-color-base;
    }

    &__meta {
      margin: 0 0 12px;
      font-size: 14px;
      line-height: 1.5715;
      color: @text-color-call-out;
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

    &__done {
      margin: 0;
      padding: 12px 16px;
      border: 1px solid @border-color-light;
      border-radius: 8px;
      background-color: @success-background-color;
      font-size: 16px;
      line-height: 1.5715;
      color: @text-color-base;
    }

    /* 选择这一项：点击目标 ≥44px（§10.1） */
    &__pick {
      min-height: 44px;
      font-size: 14px;
      border-radius: 8px;

      &.is-active {
        border: 1px solid @primary-color;
        color: @primary-color;
      }
    }

    /* 做对了 / 还要再看：两个都是 ≥44px 的点击目标 */
    &__choices {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
    }

    &__choice {
      min-width: 120px;
      min-height: 44px;
      font-size: 16px;
      border-radius: 8px;

      &.is-active {
        border: 1px solid @primary-color;
        color: @primary-color;
      }
    }

    &__cause {
      margin-top: 16px;
    }

    &__cause-title {
      margin: 0 0 8px;
      font-size: 14px;
      line-height: 1.5715;
      color: @text-color-call-out;
    }

    &__cause-list {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }

    &__cause-item {
      min-height: 44px;
      font-size: 14px;
      border-radius: 8px;

      &.is-active {
        border: 1px solid @primary-color;
        color: @primary-color;
      }
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
