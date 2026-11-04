<template>
  <StudyLayout module-title="收藏馆">
    <div class="study-kid-grammar-collection">
      <ChildSwitcher class="study-kid-grammar-collection__child" />

      <!-- 断网：可读提示 + 重试入口（F-BASE-08） -->
      <a-alert
        v-if="netError"
        class="study-kid-grammar-collection__net-error"
        type="warning"
        show-icon
        :message="netError.message"
      >
        <template #action>
          <a-button size="small" @click="onRetry">重试</a-button>
        </template>
      </a-alert>

      <!-- 错因筛选 -->
      <div class="study-kid-grammar-collection__filters">
        <a-button
          class="study-kid-grammar-collection__filter"
          :class="{ 'is-selected': !errorCause }"
          @click="onFilter('')"
        >
          全部
        </a-button>
        <a-button
          v-for="item in CAUSE_OPTIONS"
          :key="item"
          class="study-kid-grammar-collection__filter"
          :class="{ 'is-selected': errorCause === item }"
          @click="onFilter(item)"
        >
          {{ item }}
        </a-button>
      </div>

      <a-spin :spinning="loading" class="study-kid-grammar-collection__spin">
        <!-- 学习端用卡片流，不用表格（§9.7） -->
        <StudyDataList
          v-if="rows.length > 0"
          mode="card"
          :columns="columns"
          :data-source="rows"
          row-key="errorCode"
        >
          <template #cell-errorCause="{ row }">
            <StudyEnumTag kind="status" :value="row.errorCause" />
          </template>
          <template #cell-masteryState="{ row }">
            {{ row.masteryState }}
          </template>
        </StudyDataList>

        <p v-else-if="!loading" class="study-kid-grammar-collection__hint">收藏馆里还没有句子</p>
      </a-spin>
    </div>

    <template #footer>
      <a-button
        type="primary"
        class="study-kid-grammar-collection__primary"
        @click="onGoTransform"
      >
        去变形器
      </a-button>
    </template>
  </StudyLayout>
</template>

<script lang="ts">
  import { computed, defineComponent, onMounted, ref } from 'vue';
  import { useRouter } from 'vue-router';
  import ChildSwitcher from '/@/components/study/ChildSwitcher.vue';
  import StudyDataList from '/@/components/study/StudyDataList.vue';
  import StudyEnumTag from '/@/components/study/StudyEnumTag.vue';
  import { useMessage } from '/@/hooks/web/useMessage';
  import StudyLayout from '/@/layouts/study/index.vue';
  import { fetchGrammarCollection, type StudyGrammarCollectionItemVO } from '../../../api';
  import { describeNetError, OFFLINE_HINT, RETRY_LABEL, type StudyNetError } from '../../../net';
  import { KID_CAUSE_OPTIONS, kidCauseLabel, kidDateLabel, kidMasteryLabel } from '../../kidCommon';

  /**
   * 学习端语法收藏馆页（设计规范 §10.2）。
   *
   * 一屏一任务：看自己收藏的句子，按错因筛。
   * 列表是学习端卡片流，不做表格（§9.7）；只给当前孩子自己的句子（R-04）。
   */
  export default defineComponent({
    name: 'StudyKidGrammarCollectionPage',
    components: { ChildSwitcher, StudyDataList, StudyEnumTag, StudyLayout },
    setup() {
      const router = useRouter();
      const { createMessage } = useMessage();

      const items = ref<StudyGrammarCollectionItemVO[]>([]);
      const errorCause = ref('');
      const loading = ref(false);
      /** 断网提示与重试入口（F-BASE-08） */
      const netError = ref<StudyNetError | null>(null);
      /** 重试动作：断网后点重试重放上一次失败的服务端请求 */
      const retryAction = ref<null | (() => Promise<void>)>(null);

      /** 错因候选（中性表述，与后端错因五维一致） */
      const CAUSE_OPTIONS = KID_CAUSE_OPTIONS;

      /** 列表的列 */
      const columns = [
        { key: 'errorCause', title: '卡在哪' },
        { key: 'originalSentence', title: '原来的句子' },
        { key: 'correctedSentence', title: '改好的句子' },
        { key: 'masteryState', title: '掌握度' },
        { key: 'nextReviewAt', title: '下次复习' },
      ];

      /** 列表行：展示用的中文标签在页面里定 */
      const rows = computed<Record<string, any>[]>(() =>
        items.value.map((item) => ({
          errorCode: item.errorCode,
          errorCause: kidCauseLabel(item.errorCause),
          originalSentence: item.originalSentence || '',
          correctedSentence: item.correctedSentence || '',
          masteryState: kidMasteryLabel(item.masteryState) || '刚认识',
          nextReviewAt: kidDateLabel(item.nextReviewAt),
        })),
      );

      const load = async () => {
        loading.value = true;
        try {
          const data = await fetchGrammarCollection(errorCause.value || undefined);
          items.value = data?.items || [];
          netError.value = null;
        } catch (e) {
          items.value = [];
          // F-BASE-08：断网给可读提示与重试入口，业务错误保留原来的提示语义
          const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
          netError.value = state.offline ? state : null;
          if (state.offline) {
            retryAction.value = () => load();
          } else {
            createMessage.warning('收藏馆没取到，稍后再看看');
          }
        } finally {
          loading.value = false;
        }
      };

      /** 断网后点重试：重放上一次失败的服务端请求 */
      const onRetry = async () => {
        const action = retryAction.value;
        retryAction.value = null;
        netError.value = null;
        if (action) {
          await action();
        }
      };

      /** 按错因筛选 */
      const onFilter = async (value: string) => {
        errorCause.value = value;
        await load();
      };

      /** 去变形器 */
      const onGoTransform = () => {
        router.push('/study/kid/grammar/transform');
      };

      onMounted(() => {
        load();
      });

      return {
        CAUSE_OPTIONS,
        columns,
        rows,
        errorCause,
        loading,
        netError,
        onRetry,
        onFilter,
        onGoTransform,
      };
    },
  });
</script>

<style lang="less">
  .study-kid-grammar-collection {
    padding: 16px;

    &__child {
      margin-bottom: 12px;
    }

    &__filters {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      margin-bottom: 12px;
    }

    /* 点击目标 ≥44px（§10.1） */
    &__filter {
      min-height: 44px;
      font-size: 14px;
      border-radius: 8px;

      &.is-selected {
        border: 1px solid @primary-color;
        color: @primary-color;
      }
    }

    &__spin {
      display: block;
      min-height: 240px;
    }

    &__hint {
      margin: 0;
      padding: 12px 16px;
      border: 1px solid @border-color-light;
      border-radius: 12px;
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
