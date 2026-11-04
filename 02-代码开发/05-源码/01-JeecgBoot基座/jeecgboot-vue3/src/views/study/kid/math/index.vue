<template>
  <StudyLayout module-title="数学闯关">
    <div class="study-kid-math">
      <!-- 孩子上下文条：只关联了一个孩子时不渲染 -->
      <ChildSwitcher class="study-kid-math__child" />

      <!-- 断网：可读提示 + 重试入口（F-BASE-08） -->
      <a-alert
        v-if="netError"
        class="study-kid-math__net-error"
        type="warning"
        show-icon
        :message="netError.message"
      >
        <template #action>
          <a-button size="small" @click="onRetry">重试</a-button>
        </template>
      </a-alert>

      <a-spin :spinning="loading" class="study-kid-math__spin">
        <!-- 进度：单位是题型（母题类），只给当前孩子自己的客观状态（R-04） -->
        <StudyStatCards v-if="statItems.length > 0" :items="statItems" :columns-per-row="3" />

        <h2 class="study-kid-math__section-title">关卡</h2>

        <!-- 闯关卡片：圆角 12px、间距 12px、未解锁降低透明度并给锁图标（§10.4） -->
        <ul v-if="levels.length > 0" class="study-kid-math__levels">
          <li
            v-for="item in levels"
            :key="item.motherCode"
            class="study-kid-math__level"
            :class="{ 'is-locked': item.locked }"
            @click="onOpen(item)"
          >
            <span class="study-kid-math__level-index">{{ item.orderNo }}</span>
            <span class="study-kid-math__level-main">
              <span class="study-kid-math__level-title">{{ item.title }}</span>
              <span class="study-kid-math__level-meta">{{ item.meta }}</span>
            </span>
            <span class="study-kid-math__level-state">
              <Icon v-if="item.locked" icon="ant-design:lock" />
              <Icon v-else-if="item.passed" icon="ant-design:check-circle" />
              <Icon v-else icon="ant-design:arrow-right" />
            </span>
          </li>
        </ul>

        <a-empty v-if="!loading && levels.length === 0" class="study-kid-math__empty" description="暂时没有关卡" />
      </a-spin>
    </div>

    <template #footer>
      <a-button
        type="primary"
        class="study-kid-math__primary"
        :disabled="levels.length === 0"
        @click="onStart"
      >
        去闯关
      </a-button>
    </template>
  </StudyLayout>
</template>

<script lang="ts">
  import { computed, defineComponent, onMounted, ref } from 'vue';
  import { useRouter } from 'vue-router';
  import { Icon } from '/@/components/Icon';
  import ChildSwitcher from '/@/components/study/ChildSwitcher.vue';
  import StudyStatCards from '/@/components/study/StudyStatCards.vue';
  import { useMessage } from '/@/hooks/web/useMessage';
  import StudyLayout from '/@/layouts/study/index.vue';
  import {
    fetchMathMotherList,
    fetchMathProgress,
    type StudyMathMotherListItemVO,
    type StudyMathProgressVO,
  } from '../../api';
  import { describeNetError, OFFLINE_HINT, RETRY_LABEL, type StudyNetError } from '../../net';

  /** 关卡卡片 */
  interface StudyMathLevel {
    motherCode: string;
    title: string;
    orderNo: number;
    meta: string;
    passed: boolean;
    locked: boolean;
  }

  /**
   * 学习端数学首页（设计规范 §10.2 / §10.4）。
   *
   * 一屏一任务：顶部是进度，下面是闯关卡片列表。
   * 进度单位是题型（母题类），不给题量口径，也不做同类横向对照（R-04）。
   */
  export default defineComponent({
    name: 'StudyKidMathHomePage',
    components: { ChildSwitcher, Icon, StudyLayout, StudyStatCards },
    setup() {
      const router = useRouter();
      const { createMessage } = useMessage();

      const mothers = ref<StudyMathMotherListItemVO[]>([]);
      const progress = ref<StudyMathProgressVO | null>(null);
      const loading = ref(false);
      /** 断网提示与重试入口（F-BASE-08） */
      const netError = ref<StudyNetError | null>(null);
      /** 重试动作：断网后点重试重放上一次失败的服务端请求 */
      const retryAction = ref<null | (() => Promise<void>)>(null);

      /** 进度统计卡（单位是题型） */
      const statItems = computed(() => {
        const mastered = progress.value?.masteredTypeCount ?? 0;
        const total = progress.value?.totalTypeCount ?? 0;
        return [
          {
            key: 'mastered',
            title: '已会题型',
            value: mastered,
            extra: progress.value?.progressText || '',
          },
          { key: 'total', title: '题型总数', value: total },
          { key: 'todo', title: '还要练的题型', value: Math.max(total - mastered, 0) },
        ];
      });

      /**
       * 闯关卡片列表。
       *
       * 解锁口径：按 orderNo 从前往后走，前面还有没过关的关卡时，后面的关卡保持锁定。
       * 这是学习端的顺序闯关节奏，不涉及任何人的对照（R-04）。
       */
      const levels = computed<StudyMathLevel[]>(() => {
        const list = [...mothers.value].sort((a, b) => (a.orderNo ?? 0) - (b.orderNo ?? 0));
        let unlocked = true;
        return list.map((item, index) => {
          const locked = !unlocked;
          const passed = Boolean(item.passed);
          if (!passed) {
            unlocked = false;
          }
          return {
            motherCode: item.motherCode,
            title: item.title || `第 ${index + 1} 关`,
            orderNo: item.orderNo ?? index + 1,
            meta: passed ? '已过关' : `练过 ${item.attemptCount ?? 0} 次`,
            passed,
            locked,
          };
        });
      });

      /** 第一个能进的关卡 */
      const firstOpenLevel = computed(() => levels.value.find((item) => !item.locked) || levels.value[0]);

      const load = async () => {
        loading.value = true;
        try {
          const [list, progressData] = await Promise.all([fetchMathMotherList(), fetchMathProgress()]);
          mothers.value = Array.isArray(list) ? list : [];
          progress.value = progressData || null;
          netError.value = null;
        } catch (e) {
          mothers.value = [];
          progress.value = null;
          // F-BASE-08：断网给可读提示与重试入口，业务错误保留原来的提示语义
          const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
          netError.value = state.offline ? state : null;
          if (state.offline) {
            retryAction.value = () => load();
          } else {
            createMessage.warning('关卡没取到，稍后再看看');
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

      /** 进入某个关卡 */
      const onOpen = (item: StudyMathLevel) => {
        if (item.locked) {
          createMessage.info('先把前面的关卡过了');
          return;
        }
        router.push(`/study/kid/math/mother/${item.motherCode}`);
      };

      /** 底部主按钮：进入第一个能做的关卡 */
      const onStart = () => {
        const target = firstOpenLevel.value;
        if (target) {
          router.push(`/study/kid/math/mother/${target.motherCode}`);
        }
      };

      onMounted(() => {
        load();
      });

      return {
        statItems,
        levels,
        loading,
        netError,
        onRetry,
        onOpen,
        onStart,
      };
    },
  });
</script>

<style lang="less">
  .study-kid-math {
    padding: 16px;

    &__child {
      margin-bottom: 12px;
    }

    &__spin {
      display: block;
      min-height: 240px;
    }

    &__section-title {
      margin: 16px 0 0;
      font-size: 18px;
      font-weight: 600;
      line-height: 1.4;
      color: @text-color-base;
    }

    /* 闯关卡片：间距 12px、圆角 12px（§10.4） */
    &__levels {
      margin: 12px 0 0;
      padding: 0;
      list-style: none;
      display: flex;
      flex-direction: column;
      gap: 12px;
    }

    &__level {
      display: flex;
      align-items: center;
      gap: 12px;
      min-height: 44px;
      padding: 12px 16px;
      border: 1px solid @border-color-light;
      border-radius: 12px;
      background-color: @app-content-background;
      cursor: pointer;

      /* 未解锁态：降低透明度（§10.4） */
      &.is-locked {
        opacity: 0.55;
        cursor: default;
      }
    }

    &__level-index {
      flex: 0 0 auto;
      min-width: 28px;
      font-size: 16px;
      font-weight: 600;
      color: @primary-color;
    }

    &__level-main {
      flex: 1 1 auto;
      min-width: 0;
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    &__level-title {
      font-size: 16px;
      line-height: 1.4;
      color: @text-color-base;
      word-break: break-word;
    }

    &__level-meta {
      font-size: 14px;
      line-height: 1.4;
      color: @text-color-call-out;
    }

    &__level-state {
      flex: 0 0 auto;
      font-size: 18px;
      color: @primary-color;
    }

    &__empty {
      padding: 24px 0;
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
