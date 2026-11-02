<template>
  <StudyLayout module-title="语法玩法">
    <div class="study-grammar-game">
      <!-- 断网：可读提示 +「重试」入口（F-BASE-08：V0 不做离线补交，只重试） -->
      <a-alert
        v-if="netError"
        class="study-grammar-game__net-error"
        type="warning"
        show-icon
        :message="netError.message"
      >
        <template #action>
          <a-button size="small" @click="onRetry">重试</a-button>
        </template>
      </a-alert>

      <!-- 五个玩法的分段切换：一次只展开一个（§10.2 一屏一任务） -->
      <nav class="study-grammar-game__modes">
        <a-button
          class="study-grammar-game__mode"
          :class="{ 'is-active': mode === 'context' }"
          @click="switchMode('context')"
        >
          <Icon icon="ant-design:book-outlined" />
          <span class="study-grammar-game__mode-text">语境例句</span>
        </a-button>
        <a-button
          class="study-grammar-game__mode"
          :class="{ 'is-active': mode === 'detective' }"
          @click="switchMode('detective')"
        >
          <Icon icon="ant-design:search-outlined" />
          <span class="study-grammar-game__mode-text">句子侦探</span>
        </a-button>
        <a-button
          class="study-grammar-game__mode"
          :class="{ 'is-active': mode === 'transform' }"
          @click="switchMode('transform')"
        >
          <Icon icon="ant-design:edit-outlined" />
          <span class="study-grammar-game__mode-text">变形闯关</span>
        </a-button>
        <a-button
          class="study-grammar-game__mode"
          :class="{ 'is-active': mode === 'collection' }"
          @click="switchMode('collection')"
        >
          <Icon icon="ant-design:inbox-outlined" />
          <span class="study-grammar-game__mode-text">错句收藏</span>
        </a-button>
        <a-button
          class="study-grammar-game__mode"
          :class="{ 'is-active': mode === 'progress' }"
          @click="switchMode('progress')"
        >
          <Icon icon="ant-design:bar-chart-outlined" />
          <span class="study-grammar-game__mode-text">闯关进度</span>
        </a-button>
      </nav>

      <a-spin :spinning="loading" class="study-grammar-game__spin">
        <!-- ① 语境例句：与背单词共用同一批例句（课文 / 真题 / 原声素材） -->
        <section v-if="mode === 'context'" class="study-grammar-game__section">
          <h2 class="study-grammar-game__title">看同一个词在不同语境里的用法</h2>
          <p class="study-grammar-game__hint">
            输入词条编号或语法点编号，就能看到这个词在课文、真题和原声素材里的例句
          </p>

          <div class="study-grammar-game__row">
            <a-button
              class="study-grammar-game__chip"
              :class="{ 'is-active': queryKind === 'word' }"
              @click="queryKind = 'word'"
            >
              按词条查
            </a-button>
            <a-button
              class="study-grammar-game__chip"
              :class="{ 'is-active': queryKind === 'grammar' }"
              @click="queryKind = 'grammar'"
            >
              按语法点查
            </a-button>
          </div>

          <a-input
            v-model:value="codeInput"
            class="study-grammar-game__input"
            :placeholder="queryKind === 'word' ? '词条编号，例如 WRD-000001' : '语法点编号，例如 GRM-000001'"
          />

          <div class="study-grammar-game__row">
            <a-button
              class="study-grammar-game__chip"
              :class="{ 'is-active': contextType === '' }"
              @click="contextType = ''"
            >
              全部语境
            </a-button>
            <a-button
              v-for="option in CONTEXT_TYPE_OPTIONS"
              :key="option.value"
              class="study-grammar-game__chip"
              :class="{ 'is-active': contextType === option.value }"
              @click="contextType = option.value"
            >
              {{ option.label }}
            </a-button>
          </div>

          <ul v-if="contextItems.length > 0" class="study-grammar-game__list">
            <li v-for="item in contextItems" :key="item.sentenceCode" class="study-grammar-game__card">
              <div class="study-grammar-game__card-head">
                <a-tag v-if="item.contextType === 'TEXTBOOK'" color="blue">{{ item.contextTypeName }}</a-tag>
                <a-tag v-else-if="item.contextType === 'EXAM'" color="processing">{{ item.contextTypeName }}</a-tag>
                <a-tag v-else-if="item.contextType === 'ORIGINAL'" color="success">{{ item.contextTypeName }}</a-tag>
                <a-tag v-else color="default">{{ item.contextTypeName }}</a-tag>
                <span class="study-grammar-game__meta">{{ item.relationType === 'MAIN' ? '主例句' : '拓展用法' }}</span>
              </div>
              <p class="study-grammar-game__sentence">{{ item.sentence }}</p>
              <p class="study-grammar-game__meaning">{{ item.zhMeaning }}</p>
              <p v-if="item.usageNote" class="study-grammar-game__note">{{ item.usageNote }}</p>
              <p v-if="item.sourceRef" class="study-grammar-game__note">{{ item.sourceRef }}</p>
            </li>
          </ul>
          <a-empty
            v-else-if="!loading && searchedContext"
            class="study-grammar-game__empty"
            description="这个编号还没有例句，换一个试试"
          />
        </section>

        <!-- ② 句子侦探：四句话里有一句语法不对，孩子指出它（讲解等提交后才出现） -->
        <section v-else-if="mode === 'detective'" class="study-grammar-game__section">
          <h2 class="study-grammar-game__title">四句话里有一句不对，把它找出来</h2>
          <p class="study-grammar-game__hint">先自己看，看好了再选，选完再提交</p>

          <ul v-if="detectiveRound" class="study-grammar-game__list">
            <li v-for="sentence in detectiveRound.sentences" :key="sentence.position" class="study-grammar-game__item">
              <a-button
                class="study-grammar-game__sentence-btn"
                :class="{ 'is-active': pickedIndex === sentence.position }"
                @click="pickedIndex = sentence.position"
              >
                <span class="study-grammar-game__position">第 {{ sentence.position }} 句</span>
                <span class="study-grammar-game__sentence">{{ sentence.text }}</span>
              </a-button>
            </li>
          </ul>

          <!-- 讲解区：提交判定之后才渲染，未提交时这段内容不在页面上（R-01） -->
          <section
            v-if="detectiveJudge"
            class="study-grammar-game__feedback"
            :class="detectiveJudge.correct ? 'is-right' : 'is-wrong'"
          >
            <div class="study-grammar-game__verdict">
              <Icon v-if="detectiveJudge.correct" icon="ant-design:check-circle" />
              <Icon v-else icon="ant-design:smile" />
              <span class="study-grammar-game__verdict-text">
                {{ detectiveJudge.correct ? '找对了' : '再看看' }}
              </span>
              <span class="study-grammar-game__source">按规则判定</span>
            </div>
            <p class="study-grammar-game__note">错的是第 {{ detectiveJudge.wrongPosition }} 句</p>
            <p class="study-grammar-game__sentence">{{ detectiveJudge.wrongSentence }}</p>
            <div class="study-grammar-game__explain">
              <p class="study-grammar-game__explain-label">规则</p>
              <p class="study-grammar-game__explain-text">{{ detectiveJudge.rule }}</p>
            </div>
            <div class="study-grammar-game__explain">
              <p class="study-grammar-game__explain-label">这样写</p>
              <p class="study-grammar-game__explain-text">{{ detectiveJudge.example }}</p>
            </div>
            <div class="study-grammar-game__explain">
              <p class="study-grammar-game__explain-label">不要这样写</p>
              <p class="study-grammar-game__explain-text">{{ detectiveJudge.counterExample }}</p>
            </div>
            <div class="study-grammar-game__explain">
              <p class="study-grammar-game__explain-label">改好之后</p>
              <p class="study-grammar-game__explain-text">{{ detectiveJudge.fix }}</p>
            </div>
            <p v-if="detectiveJudge.errorCode" class="study-grammar-game__note">
              这句已经收进你的错句收藏馆了
            </p>
          </section>
        </section>

        <!-- ③ 变形闯关：只给中文意思，孩子自己写英文（参考表达等过关后才出现） -->
        <section v-else-if="mode === 'transform'" class="study-grammar-game__section">
          <h2 class="study-grammar-game__title">把这句话写成英文</h2>
          <p v-if="transformRound" class="study-grammar-game__hint">{{ transformRound.prompt }}</p>

          <p v-if="transformRound" class="study-grammar-game__meaning">{{ transformRound.meaning }}</p>

          <a-input
            v-model:value="typedSentence"
            class="study-grammar-game__textarea"
            type="textarea"
            :rows="4"
            placeholder="在这里写下你的英文句子"
          />

          <!-- 反馈区：提交判定之后才渲染（R-01） -->
          <section
            v-if="transformJudge"
            class="study-grammar-game__feedback"
            :class="transformJudge.correct ? 'is-right' : 'is-wrong'"
          >
            <div class="study-grammar-game__verdict">
              <Icon v-if="transformJudge.correct" icon="ant-design:check-circle" />
              <Icon v-else icon="ant-design:smile" />
              <span class="study-grammar-game__verdict-text">
                {{ transformJudge.correct ? '过关了' : '再改改' }}
              </span>
              <span class="study-grammar-game__source">按规则判定</span>
            </div>

            <div v-if="transformJudge.correct" class="study-grammar-game__explain">
              <p class="study-grammar-game__explain-label">参考表达</p>
              <p class="study-grammar-game__explain-text">{{ transformJudge.referenceText }}</p>
            </div>

            <template v-else>
              <div v-if="transformJudge.hint" class="study-grammar-game__explain">
                <p class="study-grammar-game__explain-label">提示</p>
                <p class="study-grammar-game__explain-text">{{ transformJudge.hint }}</p>
              </div>
              <div v-if="transformJudge.rule" class="study-grammar-game__explain">
                <p class="study-grammar-game__explain-label">规则</p>
                <p class="study-grammar-game__explain-text">{{ transformJudge.rule }}</p>
              </div>
              <div v-if="transformJudge.example" class="study-grammar-game__explain">
                <p class="study-grammar-game__explain-label">这样写</p>
                <p class="study-grammar-game__explain-text">{{ transformJudge.example }}</p>
              </div>
              <div v-if="transformJudge.counterExample" class="study-grammar-game__explain">
                <p class="study-grammar-game__explain-label">不要这样写</p>
                <p class="study-grammar-game__explain-text">{{ transformJudge.counterExample }}</p>
              </div>
              <div v-if="transformJudge.fix" class="study-grammar-game__explain">
                <p class="study-grammar-game__explain-label">改好之后</p>
                <p class="study-grammar-game__explain-text">{{ transformJudge.fix }}</p>
              </div>
            </template>

            <p v-if="transformJudge.errorCode" class="study-grammar-game__note">
              这句已经收进你的错句收藏馆了
            </p>
          </section>
        </section>

        <!-- ④ 我的错句收藏馆：读统一错题池，按错因过滤 -->
        <section v-else-if="mode === 'collection'" class="study-grammar-game__section">
          <h2 class="study-grammar-game__title">我的错句收藏馆</h2>
          <p class="study-grammar-game__hint">这里只放你自己写错的句子，可以按错因挑一挑</p>

          <div class="study-grammar-game__row">
            <a-button
              class="study-grammar-game__chip"
              :class="{ 'is-active': collectionCause === '' }"
              @click="pickCause('')"
            >
              全部
            </a-button>
            <a-button
              v-for="cause in CAUSE_OPTIONS"
              :key="cause"
              class="study-grammar-game__chip"
              :class="{ 'is-active': collectionCause === cause }"
              @click="pickCause(cause)"
            >
              {{ cause }}
            </a-button>
          </div>

          <ul v-if="collectionItems.length > 0" class="study-grammar-game__list">
            <li v-for="item in collectionItems" :key="item.errorCode" class="study-grammar-game__card">
              <div class="study-grammar-game__card-head">
                <a-tag color="warning">{{ item.errorCause }}</a-tag>
                <span v-if="item.archived" class="study-grammar-game__meta">已移出复习队列</span>
                <span v-else-if="item.nextReviewAt" class="study-grammar-game__meta">
                  下次复习：{{ item.nextReviewAt }}
                </span>
              </div>
              <p class="study-grammar-game__sentence">{{ item.originalSentence }}</p>
              <p v-if="item.correctedSentence" class="study-grammar-game__meaning">
                改好之后：{{ item.correctedSentence }}
              </p>
            </li>
          </ul>
          <a-empty
            v-else-if="!loading"
            class="study-grammar-game__empty"
            description="还没有收藏的错句，先去玩一关试试"
          />
        </section>

        <!-- ⑤ 闯关进度：只跟自己比（答过几关 / 过了几关 / 上次玩是什么时候） -->
        <section v-else class="study-grammar-game__section">
          <h2 class="study-grammar-game__title">我的闯关进度</h2>
          <p class="study-grammar-game__hint">这里只有你自己的记录</p>

          <ul v-if="progressItems.length > 0" class="study-grammar-game__list">
            <li v-for="item in progressItems" :key="item.gameType" class="study-grammar-game__card">
              <p class="study-grammar-game__card-title">{{ item.gameTypeName }}</p>
              <p class="study-grammar-game__meta">答过 {{ item.answeredCount }} 关</p>
              <p class="study-grammar-game__meta">过了 {{ item.passedCount }} 关</p>
              <p class="study-grammar-game__meta">
                上次玩：{{ item.lastPlayedAt || '还没有玩过' }}
              </p>
            </li>
          </ul>
          <a-empty
            v-else-if="!loading"
            class="study-grammar-game__empty"
            description="还没有闯关记录，先去玩一关"
          />
        </section>
      </a-spin>
    </div>

    <template #footer>
      <a-button
        type="primary"
        class="study-grammar-game__submit"
        :loading="submitting"
        :disabled="!canSubmit"
        @click="onFooterAction"
      >
        {{ footerLabel }}
      </a-button>
    </template>
  </StudyLayout>
</template>

<script lang="ts">
  import { computed, defineComponent, onMounted, ref } from 'vue';
  import { Icon } from '/@/components/Icon';
  import { useMessage } from '/@/hooks/web/useMessage';
  import StudyLayout from '/@/layouts/study/index.vue';
  import {
    fetchGrammarContext,
    fetchGrammarDetectiveNext,
    fetchGrammarGameCollection,
    fetchGrammarGameProgress,
    fetchGrammarTransformNext,
    judgeGrammarDetective,
    judgeGrammarTransform,
    type StudyGrammarContextItemVO,
    type StudyGrammarDetectiveJudgeVO,
    type StudyGrammarDetectiveRoundVO,
    type StudyGrammarGameCollectionItemVO,
    type StudyGrammarGameTypeProgressVO,
    type StudyGrammarTransformJudgeVO,
    type StudyGrammarTransformRoundVO,
  } from './api';
  import { describeNetError, OFFLINE_HINT, RETRY_LABEL, type StudyNetError } from '../../net';

  /** 页面上的五个玩法 */
  type GrammarGameMode = 'context' | 'detective' | 'transform' | 'collection' | 'progress';

  /** 语境类型（与服务端 GrammarContextType 一致） */
  const CONTEXT_TYPE_OPTIONS = [
    { value: 'TEXTBOOK', label: '课文' },
    { value: 'EXAM', label: '真题' },
    { value: 'ORIGINAL', label: '原声素材' },
  ];

  /** 错因五维（与服务端 ErrorCause 一致，取值必须一字不差） */
  const CAUSE_OPTIONS = ['概念不清', '审题偏差', '计算失误', '方法不会', '时间不够'];

  /**
   * 学习端英语语法 · 趣味玩法页（F-GRM-04 / F-GRM-05）。
   *
   * <p>红线要点：</p>
   * <ul>
   *   <li>取题只给句子（句子侦探）与中文意思（变形闯关），
   *       <b>讲解区整块由「已提交判定」驱动渲染</b>，未提交时不在页面上（R-01）；</li>
   *   <li>对错与讲解全部来自服务端规则判定，页面不自己判断，也不发任何外部请求（R-02）；</li>
   *   <li>进度只呈现「答过几关 / 过了几关 / 上次玩是什么时候」，
   *       不出现任何横向衡量（R-04）。</li>
   * </ul>
   *
   * <p>页面用卡片流而不是表格（§9.7），底部只保留一个主操作（§10.2）。</p>
   */
  export default defineComponent({
    name: 'StudyGrammarGamePage',
    components: { Icon, StudyLayout },
    setup() {
      const { createMessage } = useMessage();

      const mode = ref<GrammarGameMode>('detective');
      const loading = ref(false);
      const submitting = ref(false);
      /** 断网提示与重试入口（F-BASE-08） */
      const netError = ref<StudyNetError | null>(null);
      /** 重试动作：断网后点「重试」重放上一次失败的服务端请求（V0 不做断网补交） */
      const retryAction = ref<null | (() => Promise<void>)>(null);

      // ---- 语境例句 ----
      const queryKind = ref<'word' | 'grammar'>('word');
      const codeInput = ref('');
      const contextType = ref('');
      const contextItems = ref<StudyGrammarContextItemVO[]>([]);
      const searchedContext = ref(false);

      // ---- 句子侦探 ----
      const detectiveRound = ref<StudyGrammarDetectiveRoundVO | null>(null);
      const pickedIndex = ref<number | null>(null);
      const detectiveJudge = ref<StudyGrammarDetectiveJudgeVO | null>(null);

      // ---- 变形闯关 ----
      const transformRound = ref<StudyGrammarTransformRoundVO | null>(null);
      const typedSentence = ref('');
      const transformJudge = ref<StudyGrammarTransformJudgeVO | null>(null);

      // ---- 错句收藏馆 ----
      const collectionCause = ref('');
      const collectionItems = ref<StudyGrammarGameCollectionItemVO[]>([]);

      // ---- 闯关进度 ----
      const progressItems = ref<StudyGrammarGameTypeProgressVO[]>([]);

      /** 底部主按钮文案：随当前玩法变化，一屏只有一个主操作 */
      const footerLabel = computed(() => {
        if (mode.value === 'context') {
          return '查例句';
        }
        if (mode.value === 'detective') {
          return detectiveJudge.value ? '再来一关' : '提交这一关';
        }
        if (mode.value === 'transform') {
          return transformJudge.value ? '再写一关' : '提交作答';
        }
        if (mode.value === 'collection') {
          return '刷新收藏馆';
        }
        return '刷新进度';
      });

      /** 底部主按钮是否可点 */
      const canSubmit = computed(() => {
        if (mode.value === 'context') {
          return codeInput.value.trim().length > 0;
        }
        if (mode.value === 'detective') {
          return Boolean(detectiveRound.value) && pickedIndex.value !== null && !detectiveJudge.value;
        }
        if (mode.value === 'transform') {
          return Boolean(transformRound.value) && typedSentence.value.trim().length > 0 && !transformJudge.value;
        }
        return true;
      });

      /** 查语境例句 */
      const loadContext = async () => {
        const code = codeInput.value.trim();
        if (!code) {
          return;
        }
        loading.value = true;
        searchedContext.value = true;
        try {
          const result = await fetchGrammarContext({
            ...(queryKind.value === 'word' ? { wordCode: code } : { grammarCode: code }),
            contextType: contextType.value || undefined,
          });
          contextItems.value = result?.items ?? [];
          netError.value = null;
        } catch (e) {
          contextItems.value = [];
          // F-BASE-08：断网 → 可读提示 +「重试」入口；业务错误 → 保留原来的提示语义
          const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
          netError.value = state.offline ? state : null;
          if (state.offline) {
            retryAction.value = () => loadContext();
          } else {
            createMessage.warning('例句没取到，稍后再看看');
          }
        } finally {
          loading.value = false;
        }
      };

      /** 取一关句子侦探 */
      const loadDetective = async () => {
        loading.value = true;
        pickedIndex.value = null;
        detectiveJudge.value = null;
        try {
          detectiveRound.value = await fetchGrammarDetectiveNext();
          netError.value = null;
        } catch (e) {
          detectiveRound.value = null;
          // F-BASE-08：断网 → 可读提示 +「重试」入口；业务错误 → 保留原来的提示语义
          const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
          netError.value = state.offline ? state : null;
          if (state.offline) {
            retryAction.value = () => loadDetective();
          } else {
            createMessage.warning('这一关没取到，稍后再看看');
          }
        } finally {
          loading.value = false;
        }
      };

      /** 取一关变形闯关 */
      const loadTransform = async () => {
        loading.value = true;
        typedSentence.value = '';
        transformJudge.value = null;
        try {
          transformRound.value = await fetchGrammarTransformNext();
          netError.value = null;
        } catch (e) {
          transformRound.value = null;
          // F-BASE-08：断网 → 可读提示 +「重试」入口；业务错误 → 保留原来的提示语义
          const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
          netError.value = state.offline ? state : null;
          if (state.offline) {
            retryAction.value = () => loadTransform();
          } else {
            createMessage.warning('这一关没取到，稍后再看看');
          }
        } finally {
          loading.value = false;
        }
      };

      /** 读错句收藏馆 */
      const loadCollection = async () => {
        loading.value = true;
        try {
          const result = await fetchGrammarGameCollection(collectionCause.value || undefined);
          collectionItems.value = result?.items ?? [];
          netError.value = null;
        } catch (e) {
          collectionItems.value = [];
          // F-BASE-08：断网 → 可读提示 +「重试」入口；业务错误 → 保留原来的提示语义
          const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
          netError.value = state.offline ? state : null;
          if (state.offline) {
            retryAction.value = () => loadCollection();
          } else {
            createMessage.warning('收藏馆没取到，稍后再看看');
          }
        } finally {
          loading.value = false;
        }
      };

      /** 读闯关进度 */
      const loadProgress = async () => {
        loading.value = true;
        try {
          const result = await fetchGrammarGameProgress();
          progressItems.value = result?.items ?? [];
          netError.value = null;
        } catch (e) {
          progressItems.value = [];
          // F-BASE-08：断网 → 可读提示 +「重试」入口；业务错误 → 保留原来的提示语义
          const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
          netError.value = state.offline ? state : null;
          if (state.offline) {
            retryAction.value = () => loadProgress();
          } else {
            createMessage.warning('进度没取到，稍后再看看');
          }
        } finally {
          loading.value = false;
        }
      };

      /** 切换玩法：顺带把这一玩法需要的数据取回来 */
      const switchMode = async (next: GrammarGameMode) => {
        if (mode.value === next) {
          return;
        }
        mode.value = next;
        if (next === 'detective') {
          await loadDetective();
          return;
        }
        if (next === 'transform') {
          await loadTransform();
          return;
        }
        if (next === 'collection') {
          await loadCollection();
          return;
        }
        if (next === 'progress') {
          await loadProgress();
        }
      };

      /** 按错因过滤收藏馆 */
      const pickCause = async (cause: string) => {
        collectionCause.value = cause;
        await loadCollection();
      };

      // 进页面先给一关句子侦探：孩子落地就有事可做，不用先挑玩法
      onMounted(() => {
        loadDetective();
      });

      /** 断网后点「重试」：重放上一次失败的服务端请求 */
      const onRetry = async () => {
        const action = retryAction.value;
        retryAction.value = null;
        netError.value = null;
        if (action) {
          await action();
        }
      };

      /** 底部主按钮：按当前玩法做对应的事 */
      const onFooterAction = async () => {
        if (mode.value === 'context') {
          await loadContext();
          return;
        }
        if (mode.value === 'detective') {
          if (detectiveJudge.value) {
            await loadDetective();
            return;
          }
          if (!detectiveRound.value || pickedIndex.value === null) {
            return;
          }
          submitting.value = true;
          try {
            detectiveJudge.value = await judgeGrammarDetective({
              roundId: detectiveRound.value.roundId,
              pickedIndex: pickedIndex.value,
            });
            netError.value = null;
          } catch (e) {
            // F-BASE-08：断网 → 可读提示 +「重试」入口；已选的内容保留，不做本地判分
            const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
            netError.value = state.offline ? state : null;
            if (state.offline) {
              retryAction.value = () => onFooterAction();
            } else {
              createMessage.warning('这次提交没成功，再试一次');
            }
          } finally {
            submitting.value = false;
          }
          return;
        }
        if (mode.value === 'transform') {
          if (transformJudge.value) {
            await loadTransform();
            return;
          }
          if (!transformRound.value) {
            return;
          }
          submitting.value = true;
          try {
            transformJudge.value = await judgeGrammarTransform({
              roundId: transformRound.value.roundId,
              sentence: typedSentence.value.trim(),
            });
            netError.value = null;
          } catch (e) {
            // F-BASE-08：断网 → 可读提示 +「重试」入口；已写的句子保留，不做本地判分
            const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
            netError.value = state.offline ? state : null;
            if (state.offline) {
              retryAction.value = () => onFooterAction();
            } else {
              createMessage.warning('这次提交没成功，再试一次');
            }
          } finally {
            submitting.value = false;
          }
          return;
        }
        if (mode.value === 'collection') {
          await loadCollection();
          return;
        }
        await loadProgress();
      };

      return {
        CONTEXT_TYPE_OPTIONS,
        CAUSE_OPTIONS,
        mode,
        loading,
        submitting,
        queryKind,
        codeInput,
        contextType,
        contextItems,
        searchedContext,
        detectiveRound,
        pickedIndex,
        detectiveJudge,
        transformRound,
        typedSentence,
        transformJudge,
        collectionCause,
        collectionItems,
        progressItems,
        footerLabel,
        canSubmit,
        netError,
        switchMode,
        pickCause,
        onFooterAction,
        onRetry,
      };
    },
  });
</script>

<style lang="less">
  .study-grammar-game {
    padding: 16px;

    &__modes {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      margin-bottom: 16px;
    }

    &__mode {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      min-height: 44px;
      padding: 4px 12px;
      font-size: 14px;
      border-radius: 8px;

      &.is-active {
        border: 1px solid @primary-color;
        color: @primary-color;
      }
    }

    &__mode-text {
      font-size: 14px;
      line-height: 1.4;
    }

    &__spin {
      display: block;
      min-height: 240px;
    }

    &__section {
      display: block;
    }

    &__title {
      margin: 0;
      font-size: 18px;
      font-weight: 600;
      line-height: 1.4;
      color: @text-color-base;
    }

    &__hint {
      margin: 4px 0 12px;
      font-size: 14px;
      line-height: 1.5715;
      color: @text-color-call-out;
    }

    &__row {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      margin-bottom: 12px;
    }

    &__chip {
      min-height: 44px;
      padding: 4px 12px;
      font-size: 14px;
      border-radius: 8px;

      &.is-active {
        border: 1px solid @primary-color;
        color: @primary-color;
      }
    }

    &__input,
    &__select {
      width: 100%;
      margin-bottom: 12px;
      min-height: 44px;
      font-size: 16px;
    }

    &__textarea {
      width: 100%;
      margin-top: 12px;
      font-size: 16px;
      line-height: 1.5715;
    }

    &__list {
      margin: 0;
      padding: 0;
      list-style: none;
    }

    &__card {
      margin-bottom: 12px;
      padding: 12px 16px;
      border: 1px solid @border-color-light;
      border-radius: 12px;
      background-color: @app-content-background;
    }

    &__card-head {
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      gap: 8px;
      margin-bottom: 4px;
    }

    &__card-title {
      margin: 0 0 4px;
      font-size: 16px;
      font-weight: 600;
      line-height: 1.4;
      color: @text-color-base;
    }

    &__meta {
      font-size: 13px;
      line-height: 1.4;
      color: @text-color-call-out;
    }

    &__item {
      margin-bottom: 8px;
    }

    &__sentence-btn {
      display: flex;
      align-items: center;
      gap: 12px;
      width: 100%;
      min-height: 44px;
      padding: 12px 16px;
      font-size: 16px;
      text-align: left;
      border-radius: 12px;

      &.is-active {
        border: 1px solid @primary-color;
        color: @primary-color;
      }
    }

    &__position {
      flex: 0 0 auto;
      font-size: 13px;
      font-weight: 600;
      color: @text-color-call-out;
    }

    &__sentence {
      flex: 1 1 auto;
      min-width: 0;
      margin: 4px 0 0;
      font-size: 16px;
      line-height: 1.5715;
      color: @text-color-base;
      word-break: break-word;
    }

    &__meaning {
      margin: 8px 0 0;
      font-size: 16px;
      line-height: 1.5715;
      color: @text-color-base;
    }

    &__note {
      margin: 4px 0 0;
      font-size: 14px;
      line-height: 1.5715;
      color: @text-color-call-out;
    }

    &__feedback {
      margin-top: 16px;
      padding: 12px 16px;
      border: 1px solid @border-color-light;
      border-radius: 12px;
      background-color: @app-content-background;

      &.is-right {
        background-color: @success-background-color;
      }

      &.is-wrong {
        background-color: @danger-background-color;
      }
    }

    &__verdict {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 16px;
      font-weight: 600;
      line-height: 1.4;
      color: @text-color-base;
    }

    &__verdict-text {
      font-size: 16px;
      font-weight: 600;
    }

    &__source {
      padding: 4px 8px;
      border-radius: 4px;
      font-size: 12px;
      font-weight: 400;
      color: @text-color-call-out;
      background-color: @white;
    }

    &__explain {
      margin-top: 8px;
    }

    &__explain-label {
      margin: 0;
      font-size: 13px;
      line-height: 1.4;
      color: @text-color-call-out;
    }

    &__explain-text {
      margin: 4px 0 0;
      font-size: 16px;
      line-height: 1.5715;
      color: @text-color-base;
      word-break: break-word;
    }

    &__empty {
      padding: 24px 0;
    }

    /* 断网提示条（F-BASE-08） */
    &__net-error {
      margin-bottom: 16px;
    }

    &__submit {
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
