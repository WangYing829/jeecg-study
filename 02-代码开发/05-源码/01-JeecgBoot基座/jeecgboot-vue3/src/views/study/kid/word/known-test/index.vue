<template>
  <StudyLayout module-title="熟词测试">
    <div class="study-kid-word-known-test">
      <!-- 断网：可读提示 +「重试」入口（F-BASE-08：V0 不做离线补交，只重试） -->
      <a-alert
        v-if="netError"
        class="study-kid-word-known-test__net-error"
        type="warning"
        show-icon
        :message="netError.message"
      >
        <template #action>
          <a-button size="small" @click="onRetry">重试</a-button>
        </template>
      </a-alert>

      <a-spin :spinning="loading" class="study-kid-word-known-test__spin">
        <div class="study-kid-word-known-test__head">
          <Icon icon="ant-design:check-square-outlined" class="study-kid-word-known-test__head-icon" />
          <h2 class="study-kid-word-known-test__title">哪些词已经会了</h2>
        </div>
        <p class="study-kid-word-known-test__hint">
          会了的词标一下，之后就少背一些
        </p>

        <!-- 先选单元 -->
        <template v-if="!currentUnit">
          <p v-if="!loading && unitRows.length === 0" class="study-kid-word-known-test__hint">
            暂时没有可测的单元
          </p>
          <StudyDataList
            v-else
            mode="card"
            :columns="unitColumns"
            :data-source="unitRows"
            row-key="unitCode"
          >
            <template #cell-action="{ row }">
              <a-button class="study-kid-word-known-test__pick" @click="onPickUnit(row)">选它</a-button>
            </template>
          </StudyDataList>
        </template>

        <!-- 再逐词标记 -->
        <template v-else>
          <p class="study-kid-word-known-test__meta">
            已经标了 {{ knownCodes.length }} 个会了的词
          </p>
          <p v-if="wordRows.length === 0" class="study-kid-word-known-test__hint">
            这个单元还没有词
          </p>
          <ul v-else class="study-kid-word-known-test__words">
            <li v-for="row in wordRows" :key="row.wordCode" class="study-kid-word-known-test__word">
              <div class="study-kid-word-known-test__word-info">
                <span class="study-kid-word-known-test__word-meaning">{{ row.meaning }}</span>
                <span class="study-kid-word-known-test__word-phonetic">{{ row.phonetic }}</span>
              </div>
              <div class="study-kid-word-known-test__word-choices">
                <a-button
                  class="study-kid-word-known-test__word-btn"
                  :class="{ 'is-active': row.known }"
                  @click="onToggleWord(row.wordCode, true)"
                >
                  认识
                </a-button>
                <a-button
                  class="study-kid-word-known-test__word-btn"
                  :class="{ 'is-active': row.known === false }"
                  @click="onToggleWord(row.wordCode, false)"
                >
                  不认识
                </a-button>
              </div>
            </li>
          </ul>

          <a-button class="study-kid-word-known-test__back" type="link" @click="onBack">
            换一个单元
          </a-button>
        </template>

        <p v-if="doneMessage" class="study-kid-word-known-test__done">{{ doneMessage }}</p>
      </a-spin>
    </div>

    <template #footer>
      <a-button
        type="primary"
        class="study-kid-word-known-test__primary"
        :loading="submitting"
        :disabled="!canSubmit"
        @click="onSubmit"
      >
        提交熟词测试
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
    fetchWordList,
    fetchWordUnits,
    submitKnownTest,
    type StudyWordItemVO,
    type StudyWordUnitVO,
  } from '../../../api';
  import { describeNetError, OFFLINE_HINT, RETRY_LABEL, type StudyNetError } from '../../../net';

  /** 线别：课内（IN）。V1 范围是课内线优先 */
  const LINE_TYPE = 'IN';

  /**
   * 孩子端熟词测试页（设计规范 §10.2）。
   *
   * 红线要点：
   * 词表接口不下发拼写，页面只展示释义与音标（R-01）；
   * 认识的词由孩子自己标记，筛选与队列由服务端处理，前端不本地判分（R-02）；
   * 只显示自己的词表，不出现任何他人标识（R-04）。
   *
   * ⚠ 注释里不要写 HTML 标签：红线扫描取的是尖括号之间的文本（R04-3）。
   */
  export default defineComponent({
    name: 'StudyKidWordKnownTestPage',
    components: { Icon, StudyLayout, StudyDataList },
    setup() {
      const { createMessage } = useMessage();

      const units = ref<StudyWordUnitVO[]>([]);
      const words = ref<StudyWordItemVO[]>([]);
      /** 当前测的单元 */
      const currentUnit = ref<StudyWordUnitVO | null>(null);
      /** 逐词的标记：true = 认识，false = 不认识 */
      const marks = ref<Record<string, boolean>>({});
      /** 已经标成「认识」的词 */
      const knownCodes = computed(() => Object.keys(marks.value).filter((code) => marks.value[code]));
      const loading = ref(false);
      const submitting = ref(false);
      /** 测试结果提示 */
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

      /** 单元行 */
      const unitRows = computed<Record<string, any>[]>(() =>
        units.value.map((unit) => ({
          unitCode: unit.unitCode,
          wordCount: unit.wordCount ?? 0,
        })),
      );

      /** 词表行：known 是孩子的标记状态（true = 认识，false = 不认识，没标则为空） */
      const wordRows = computed<Record<string, any>[]>(() =>
        words.value.map((word) => ({
          wordCode: word.wordCode,
          meaning: word.meaning,
          phonetic: word.phonetic,
          known: marks.value[word.wordCode || ''],
        })),
      );

      /** 能不能提交：选好了单元，而且至少标了一个词 */
      const canSubmit = computed(() => Boolean(currentUnit.value) && knownCodes.value.length > 0);

      const load = async () => {
        loading.value = true;
        try {
          const result = await fetchWordUnits(LINE_TYPE);
          units.value = Array.isArray(result) ? result : result?.items ?? [];
          netError.value = null;
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

      /** 选一个单元并把它的词表取出来 */
      const onPickUnit = async (row: Record<string, any>) => {
        const unit = units.value.find((item) => item.unitCode === row.unitCode);
        if (!unit) {
          return;
        }
        loading.value = true;
        try {
          const list = await fetchWordList(LINE_TYPE, {
            textbookVersionId: unit.textbookVersionId,
            unitCode: unit.unitCode,
          });
          currentUnit.value = unit;
          words.value = list?.items ?? [];
          marks.value = {};
          doneMessage.value = '';
          netError.value = null;
        } catch (e) {
          // F-BASE-08：断网 → 可读提示 +「重试」入口；业务错误 → 保留原来的提示语义
          const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
          netError.value = state.offline ? state : null;
          if (state.offline) {
            retryAction.value = () => onPickUnit(row);
          } else {
            createMessage.warning('词表没取到，稍后再看看');
          }
        } finally {
          loading.value = false;
        }
      };

      /** 标记一个词：认识 / 不认识 */
      const onToggleWord = (code: string, known: boolean) => {
        if (!code) {
          return;
        }
        marks.value = { ...marks.value, [code]: known };
      };

      /** 回到单元列表 */
      const onBack = () => {
        currentUnit.value = null;
        words.value = [];
        marks.value = {};
        doneMessage.value = '';
      };

      /** 提交熟词测试：筛选与队列由服务端处理 */
      const onSubmit = async () => {
        if (!canSubmit.value) {
          return;
        }
        submitting.value = true;
        doneMessage.value = '';
        try {
          const result = await submitKnownTest({
            lineType: LINE_TYPE,
            wordCodes: knownCodes.value,
          });
          const marked = result?.markedCount ?? 0;
          const filtered = result?.filteredKnownCount ?? 0;
          doneMessage.value = `标了 ${marked} 个会了的词，队列里少掉 ${filtered} 个`;
          netError.value = null;
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
        currentUnit,
        unitColumns,
        unitRows,
        wordRows,
        knownCodes,
        canSubmit,
        doneMessage,
        loading,
        submitting,
        netError,
        onRetry,
        onPickUnit,
        onToggleWord,
        onBack,
        onSubmit,
      };
    },
  });
</script>

<style lang="less">
  .study-kid-word-known-test {
    padding: 16px;

    &__spin {
      display: block;
      min-height: 240px;
    }

    &__head {
      display: flex;
      align-items: center;
      gap: 8px;
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

    &__hint {
      margin: 8px 0 16px;
      font-size: 14px;
      line-height: 1.5715;
      color: @text-color-call-out;
    }

    &__meta {
      margin: 0 0 12px;
      font-size: 14px;
      line-height: 1.5715;
      color: @text-color-call-out;
    }

    &__words {
      margin: 0 0 16px;
      padding: 0;
      list-style: none;
    }

    &__word {
      margin-bottom: 12px;
      padding: 12px 16px;
      border: 1px solid @border-color-light;
      border-radius: 8px;
      background-color: @app-content-background;
    }

    &__word-info {
      display: flex;
      align-items: baseline;
      gap: 8px;
      margin-bottom: 8px;
    }

    &__word-meaning {
      font-size: 16px;
      font-weight: 500;
      line-height: 1.4;
      color: @text-color-base;
    }

    &__word-phonetic {
      font-size: 14px;
      line-height: 1.4;
      color: @text-color-call-out;
    }

    &__word-choices {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }

    /* 每个标记按钮都是 ≥44px 的点击目标（§10.1） */
    &__word-btn {
      min-height: 44px;
      font-size: 14px;
      border-radius: 8px;

      &.is-active {
        border: 1px solid @primary-color;
        color: @primary-color;
      }
    }

    /* 选单元：点击目标 ≥44px（§10.1） */
    &__pick {
      min-height: 44px;
      font-size: 14px;
      border-radius: 8px;
    }

    &__back {
      margin-top: 12px;
      font-size: 14px;
    }

    &__done {
      margin: 16px 0 0;
      padding: 12px 16px;
      border: 1px solid @border-color-light;
      border-radius: 8px;
      background-color: @success-background-color;
      font-size: 16px;
      line-height: 1.5715;
      color: @text-color-base;
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
