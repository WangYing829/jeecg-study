<template>
  <StudyLayout module-title="课内单元闯关">
    <div class="study-kid-word-unit">
      <!-- 断网：可读提示 +「重试」入口（F-BASE-08：V0 不做离线补交，只重试） -->
      <a-alert
        v-if="netError"
        class="study-kid-word-unit__net-error"
        type="warning"
        show-icon
        :message="netError.message"
      >
        <template #action>
          <a-button size="small" @click="onRetry">重试</a-button>
        </template>
      </a-alert>

      <a-spin :spinning="loading" class="study-kid-word-unit__spin">
        <div class="study-kid-word-unit__head">
          <Icon icon="ant-design:trophy-outlined" class="study-kid-word-unit__head-icon" />
          <h2 class="study-kid-word-unit__title">{{ currentUnit ? '这个单元的词' : '选一个单元' }}</h2>
        </div>

        <!-- 没选单元：列单元；选了单元：列这一单元的词 -->
        <template v-if="!currentUnit">
          <p v-if="!loading && unitRows.length === 0" class="study-kid-word-unit__hint">
            暂时没有可练的单元
          </p>
          <StudyDataList
            v-else
            mode="card"
            :columns="unitColumns"
            :data-source="unitRows"
            row-key="unitCode"
          >
            <template #cell-action="{ row }">
              <a-button class="study-kid-word-unit__pick" @click="onPickUnit(row)">进入</a-button>
            </template>
          </StudyDataList>
        </template>

        <template v-else>
          <section class="study-kid-word-unit__section">
            <p class="study-kid-word-unit__meta">
              这个单元一共 {{ wordRows.length }} 个词
            </p>
            <p v-if="wordRows.length === 0" class="study-kid-word-unit__hint">这个单元还没有词</p>
            <StudyDataList
              v-else
              mode="card"
              :columns="wordColumns"
              :data-source="wordRows"
              row-key="wordCode"
            />
          </section>

          <p v-if="doneMessage" class="study-kid-word-unit__done">{{ doneMessage }}</p>

          <a-button class="study-kid-word-unit__back" type="link" @click="onBack">
            换一个单元
          </a-button>
        </template>
      </a-spin>
    </div>

    <template #footer>
      <a-button
        type="primary"
        class="study-kid-word-unit__primary"
        :loading="submitting"
        :disabled="!currentUnit"
        @click="onComplete"
      >
        完成这一单元
      </a-button>
    </template>
  </StudyLayout>
</template>

<script lang="ts">
  import { computed, defineComponent, onMounted, ref } from 'vue';
  import { useRoute } from 'vue-router';
  import { Icon } from '/@/components/Icon';
  import { useMessage } from '/@/hooks/web/useMessage';
  import StudyLayout from '/@/layouts/study/index.vue';
  import StudyDataList from '/@/components/study/StudyDataList.vue';
  import {
    completeWordUnit,
    enterWordUnit,
    fetchCurrentKid,
    fetchWordUnits,
    type StudyWordItemVO,
    type StudyWordUnitVO,
  } from '../../../api';
  import { describeNetError, OFFLINE_HINT, RETRY_LABEL, type StudyNetError } from '../../../net';

  /** 线别：课内（IN）。V1 范围是课内线优先 */
  const LINE_TYPE = 'IN';

  /**
   * 孩子端课内单元闯关页（设计规范 §10.2）。
   *
   * 红线要点：
   * 词表接口不下发拼写，页面只展示释义与音标（R-01）；
   * 是否算对由服务端判定，前端不做本地判分（R-02）；
   * 只显示自己的学习进度，不出现任何他人标识（R-04）。
   *
   * ⚠ 注释里不要写 HTML 标签：红线扫描取的是尖括号之间的文本（R04-3）。
   */
  export default defineComponent({
    name: 'StudyKidWordUnitPage',
    components: { Icon, StudyLayout, StudyDataList },
    setup() {
      const route = useRoute();
      const { createMessage } = useMessage();

      const units = ref<StudyWordUnitVO[]>([]);
      const words = ref<StudyWordItemVO[]>([]);
      /** 当前进入的单元 */
      const currentUnit = ref<StudyWordUnitVO | null>(null);
      /** 当前孩子（服务端按会话判定，前端只用来提交） */
      const childCode = ref('');
      const loading = ref(false);
      const submitting = ref(false);
      /** 完成单元后的反馈 */
      const doneMessage = ref('');
      /** 断网提示与重试入口（F-BASE-08） */
      const netError = ref<StudyNetError | null>(null);
      /** 重试动作：断网后点「重试」重放上一次失败的服务端请求 */
      const retryAction = ref<null | (() => Promise<void>)>(null);

      /** 单元列表的列 */
      const unitColumns = [
        { key: 'unitCode', title: '单元' },
        { key: 'wordCount', title: '词数' },
        { key: 'action', title: '选择' },
      ];

      /** 词表的列（服务端不下发拼写，只给释义与音标，R-01） */
      const wordColumns = [
        { key: 'meaning', title: '词义' },
        { key: 'phonetic', title: '音标' },
      ];

      /** 单元行 */
      const unitRows = computed<Record<string, any>[]>(() =>
        units.value.map((unit) => ({
          unitCode: unit.unitCode,
          wordCount: unit.wordCount ?? 0,
        })),
      );

      /** 词表行 */
      const wordRows = computed<Record<string, any>[]>(() =>
        words.value.map((word) => ({
          wordCode: word.wordCode,
          meaning: word.meaning,
          phonetic: word.phonetic,
        })),
      );

      /** 进入一个单元：向服务端要这一单元的词表 */
      const enterUnit = async (unit: StudyWordUnitVO) => {
        loading.value = true;
        try {
          const result = await enterWordUnit({
            childCode: childCode.value,
            textbookVersionId: unit.textbookVersionId,
            unitCode: unit.unitCode,
            pureMode: false,
          });
          currentUnit.value = unit;
          words.value = result?.items ?? [];
          doneMessage.value = '';
          netError.value = null;
        } catch (e) {
          // F-BASE-08：断网 → 可读提示 +「重试」入口；业务错误 → 保留原来的提示语义
          const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
          netError.value = state.offline ? state : null;
          if (state.offline) {
            retryAction.value = () => enterUnit(unit);
          } else {
            createMessage.warning('这个单元暂时进不去，稍后再看看');
          }
        } finally {
          loading.value = false;
        }
      };

      const load = async () => {
        loading.value = true;
        try {
          const kid = await fetchCurrentKid();
          childCode.value = kid?.childCode || '';
          const result = await fetchWordUnits(LINE_TYPE);
          units.value = Array.isArray(result) ? result : result?.items ?? [];
          netError.value = null;

          // 路由里带了单元号就直接进这个单元（菜单 url 带 :unitCode）
          const wanted = String(route.params.unitCode || '');
          if (wanted) {
            const unit = units.value.find((item) => item.unitCode === wanted);
            if (unit) {
              await enterUnit(unit);
            }
          }
        } catch (e) {
          // F-BASE-08：断网 → 可读提示 +「重试」入口；业务错误 → 保留原来的提示语义
          const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
          netError.value = state.offline ? state : null;
          if (state.offline) {
            retryAction.value = () => load();
          } else {
            createMessage.warning('单元清单没取到，稍后再看看');
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

      /** 选一个单元 */
      const onPickUnit = (row: Record<string, any>) => {
        const unit = units.value.find((item) => item.unitCode === row.unitCode);
        if (unit) {
          enterUnit(unit);
        }
      };

      /** 回到单元列表 */
      const onBack = () => {
        currentUnit.value = null;
        words.value = [];
        doneMessage.value = '';
      };

      /** 完成这一单元：奖励与连续天数由服务端下发 */
      const onComplete = async () => {
        if (!currentUnit.value) {
          return;
        }
        submitting.value = true;
        try {
          const result = await completeWordUnit({
            childCode: childCode.value,
            textbookVersionId: currentUnit.value.textbookVersionId,
            unitCode: currentUnit.value.unitCode,
            pureMode: false,
          });
          const reward = result?.rewardText;
          const learned = result?.learnedWordCount ?? 0;
          doneMessage.value = reward || `这一单元记住了 ${learned} 个词`;
          netError.value = null;
        } catch (e) {
          // F-BASE-08：断网 → 可读提示 +「重试」入口；业务错误 → 保留原来的提示语义
          const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
          netError.value = state.offline ? state : null;
          if (state.offline) {
            retryAction.value = () => onComplete();
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
        currentUnit,
        unitColumns,
        unitRows,
        wordColumns,
        wordRows,
        doneMessage,
        loading,
        submitting,
        netError,
        onRetry,
        onPickUnit,
        onBack,
        onComplete,
      };
    },
  });
</script>

<style lang="less">
  .study-kid-word-unit {
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

    &__section {
      margin-bottom: 16px;
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
      margin: 0 0 16px;
      padding: 12px 16px;
      border: 1px solid @border-color-light;
      border-radius: 8px;
      background-color: @success-background-color;
      font-size: 16px;
      line-height: 1.5715;
      color: @text-color-base;
    }

    /* 进入单元：点击目标 ≥44px（§10.1） */
    &__pick {
      min-height: 44px;
      font-size: 14px;
      border-radius: 8px;
    }

    &__back {
      margin-top: 12px;
      font-size: 14px;
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
