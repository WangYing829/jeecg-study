<template>
  <StudyLayout module-title="课外词线">
    <div class="study-kid-word-out">
      <!-- 断网：可读提示 +「重试」入口（F-BASE-08：V0 不做离线补交，只重试） -->
      <a-alert
        v-if="netError"
        class="study-kid-word-out__net-error"
        type="warning"
        show-icon
        :message="netError.message"
      >
        <template #action>
          <a-button size="small" @click="onRetry">重试</a-button>
        </template>
      </a-alert>

      <a-spin :spinning="loading" class="study-kid-word-out__spin">
        <div class="study-kid-word-out__head">
          <Icon icon="ant-design:global-outlined" class="study-kid-word-out__head-icon" />
          <h2 class="study-kid-word-out__title">该复习的课外词</h2>
        </div>

        <section class="study-kid-word-out__section">
          <StudyStatCards :items="summaryItems" :columns-per-row="2" />
        </section>

        <section class="study-kid-word-out__section">
          <h3 class="study-kid-word-out__subtitle">到期的词</h3>
          <p v-if="!loading && dueRows.length === 0" class="study-kid-word-out__hint">
            现在没有到期的词
          </p>
          <StudyDataList
            v-else
            mode="card"
            :columns="wordColumns"
            :data-source="dueRows"
            row-key="wordCode"
          />
        </section>

        <section v-if="upgradeRows.length > 0" class="study-kid-word-out__section">
          <h3 class="study-kid-word-out__subtitle">快要记牢的词</h3>
          <StudyDataList
            mode="card"
            :columns="wordColumns"
            :data-source="upgradeRows"
            row-key="wordCode"
          />
        </section>
      </a-spin>
    </div>

    <template #footer>
      <a-button
        type="primary"
        class="study-kid-word-out__primary"
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
  import StudyDataList from '/@/components/study/StudyDataList.vue';
  import StudyStatCards from '/@/components/study/StudyStatCards.vue';
  import { fetchWordDue, type StudyWordItemVO } from '../../../api';
  import { describeNetError, OFFLINE_HINT, RETRY_LABEL, type StudyNetError } from '../../../net';

  /** 线别：课外（OUT） */
  const LINE_TYPE = 'OUT';

  /**
   * 孩子端课外词线页（设计规范 §10.2）。
   *
   * 红线要点：
   * 到期词接口不下发拼写，页面只展示释义与音标（R-01）；
   * 只显示自己的背诵队列，不出现任何他人标识（R-04）；
   * 统计只给客观计数，不与任何人横向比较（R-04）。
   *
   * ⚠ 注释里不要写 HTML 标签：红线扫描取的是尖括号之间的文本（R04-3）。
   */
  export default defineComponent({
    name: 'StudyKidWordOutdoorPage',
    components: { Icon, StudyLayout, StudyDataList, StudyStatCards },
    setup() {
      const router = useRouter();
      const { createMessage } = useMessage();

      const dueWords = ref<StudyWordItemVO[]>([]);
      const upgradeWords = ref<StudyWordItemVO[]>([]);
      const totalDue = ref(0);
      const filteredKnownCount = ref(0);
      const loading = ref(false);
      /** 断网提示与重试入口（F-BASE-08） */
      const netError = ref<StudyNetError | null>(null);
      /** 重试动作：断网后点「重试」重放上一次失败的服务端请求 */
      const retryAction = ref<null | (() => Promise<void>)>(null);

      /** 词表的列（服务端不下发拼写，只给释义与音标，R-01） */
      const wordColumns = [
        { key: 'meaning', title: '词义' },
        { key: 'phonetic', title: '音标' },
      ];

      /** 到期词行 */
      const dueRows = computed<Record<string, any>[]>(() =>
        dueWords.value.map((word) => ({
          wordCode: word.wordCode,
          meaning: word.meaning,
          phonetic: word.phonetic,
        })),
      );

      /** 快记牢的词行 */
      const upgradeRows = computed<Record<string, any>[]>(() =>
        upgradeWords.value.map((word) => ({
          wordCode: word.wordCode,
          meaning: word.meaning,
          phonetic: word.phonetic,
        })),
      );

      /** 概览：只给客观计数 */
      const summaryItems = computed(() => [
        { key: 'due', title: '到期的词', value: totalDue.value },
        { key: 'known', title: '已经记熟的', value: filteredKnownCount.value },
      ]);

      const load = async () => {
        loading.value = true;
        try {
          const due = await fetchWordDue(LINE_TYPE);
          dueWords.value = due?.items ?? [];
          upgradeWords.value = due?.upgradeItems ?? [];
          totalDue.value = due?.totalDue ?? 0;
          filteredKnownCount.value = due?.filteredKnownCount ?? 0;
          netError.value = null;
        } catch (e) {
          // F-BASE-08：断网 → 可读提示 +「重试」入口；业务错误 → 保留原来的提示语义
          const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
          netError.value = state.offline ? state : null;
          if (state.offline) {
            retryAction.value = () => load();
          } else {
            createMessage.warning('词表没取到，稍后再看看');
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
        wordColumns,
        dueRows,
        upgradeRows,
        summaryItems,
        loading,
        netError,
        onRetry,
        onGoWord,
      };
    },
  });
</script>

<style lang="less">
  .study-kid-word-out {
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
