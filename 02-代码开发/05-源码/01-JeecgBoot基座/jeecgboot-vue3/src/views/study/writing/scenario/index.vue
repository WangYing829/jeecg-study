<template>
  <StudyLayout module-title="语文写作">
    <div class="study-scenario">
      <!-- 断网：可读提示 +「重试」入口（F-BASE-08：V0 不做离线补交，只重试） -->
      <a-alert
        v-if="netError"
        class="study-scenario__net-error"
        type="warning"
        show-icon
        :message="netError.message"
      >
        <template #action>
          <a-button size="small" @click="onRetry">重试</a-button>
        </template>
      </a-alert>

      <a-spin :spinning="loading" class="study-scenario__spin">
        <!-- 第一步：挑一个主题（同一个主题下有很多种处境） -->
        <section v-if="step === 'theme'" class="study-scenario__step">
          <h2 class="study-scenario__title">先挑一个主题</h2>
          <p class="study-scenario__hint">同一个主题下有很多种处境，挑一个你有话说的</p>

          <ul class="study-scenario__list">
            <li v-for="theme in themes" :key="theme.themeCode" class="study-scenario__card">
              <div class="study-scenario__card-head">
                <span class="study-scenario__card-title">{{ theme.themeName }}</span>
                <span class="study-scenario__card-meta">{{ theme.scenarioCount }} 种处境</span>
              </div>
              <a-button class="study-scenario__pick" @click="pickTheme(theme.themeCode)">选这个主题</a-button>
            </li>
          </ul>

          <a-empty v-if="!loading && themes.length === 0" class="study-scenario__empty" description="暂时没有可选的主题" />
        </section>

        <!-- 第二步：挑一种处境（同一主题下年龄与细节都不同） -->
        <section v-else-if="step === 'scenario'" class="study-scenario__step">
          <a-button class="study-scenario__back" @click="goBack">上一步</a-button>
          <h2 class="study-scenario__title">挑一种处境</h2>
          <p class="study-scenario__hint">每种处境的年龄和细节都不一样，选一个你能写下去的</p>

          <ul class="study-scenario__list">
            <li v-for="item in scenarios" :key="item.scenarioCode" class="study-scenario__card">
              <div class="study-scenario__card-head">
                <span class="study-scenario__card-title">{{ item.title }}</span>
                <span class="study-scenario__card-meta">{{ item.ageStage }}</span>
              </div>
              <p class="study-scenario__desc">{{ item.situationDesc }}</p>
              <ul class="study-scenario__clues">
                <li v-for="(clue, index) in item.detailClues" :key="index" class="study-scenario__clue">{{ clue }}</li>
              </ul>
              <a-button class="study-scenario__pick" @click="pickScenario(item)">用这个处境</a-button>
            </li>
          </ul>

          <a-empty v-if="!loading && scenarios.length === 0" class="study-scenario__empty" description="暂时没有可选的处境" />
        </section>

        <!-- 第三步：写自己的经历（不给答案，只给处境） -->
        <section v-else class="study-scenario__step">
          <a-button class="study-scenario__back" @click="goBack">上一步</a-button>
          <h2 class="study-scenario__title">写你自己的经历</h2>

          <div v-if="selected" class="study-scenario__brief">
            <p class="study-scenario__desc">{{ selected.situationDesc }}</p>
            <p class="study-scenario__question">{{ selected.promptQuestion }}</p>
            <p class="study-scenario__limit">{{ selected.restrictionNote }}</p>
            <p class="study-scenario__limit">{{ selected.forbiddenWords }}</p>
          </div>

          <a-input
            v-model:value="experience"
            class="study-scenario__input"
            type="textarea"
            :rows="8"
            placeholder="写你身上真实发生过的事"
          />
        </section>
      </a-spin>
    </div>

    <template #footer>
      <a-button type="primary" class="study-scenario__submit" :loading="submitting" :disabled="!canGo" @click="onNext">
        {{ footerLabel }}
      </a-button>
    </template>
  </StudyLayout>
</template>

<script lang="ts">
  import { computed, defineComponent, onMounted, ref } from 'vue';
  import { useRouter } from 'vue-router';
  import { useMessage } from '/@/hooks/web/useMessage';
  import StudyLayout from '/@/layouts/study/index.vue';
  import {
    fetchScenarioDetail,
    fetchScenarioList,
    fetchScenarioThemes,
    type StudyScenarioThemeVO,
    type StudyScenarioVO,
  } from './api';
  import { describeNetError, OFFLINE_HINT, RETRY_LABEL, type StudyNetError } from '../../net';

  /**
   * 学习端语文写作 · 情境选择页（F-WRT-03）。
   *
   * 三步走：挑主题 → 挑处境 → 写自己的经历。
   * 页面只呈现处境、年龄、细节线索与提示题，不给任何可以直接抄走的句子，
   * 也不出现任何横向衡量一类的东西（R-04）。
   *
   * 一屏一任务（§10.2）：每一步只做一件事，底部主操作条只有一个主按钮。
   */
  export default defineComponent({
    name: 'StudyWritingScenarioPage',
    // ⚠ 必须显式注册 StudyLayout：用 defineComponent + setup() 写法时，
    //   局部 import 的组件**不会**自动注册。未注册时 Vue 会把 <StudyLayout>
    //   当成未知 HTML 元素渲染——页面内容照样出得来，但布局外壳整层丢失：
    //   顶栏、护眼护罩（R-03）、底部主操作条（第三步的「提交」按钮就在 footer slot 里）、
    //   以及学习端主题锁定（data-theme="light"）全部失效。
    //   这条是 2026-10-31 P2 浏览器取证（TC-08-22 暗色遍历）实测发现并修掉的。
    components: { StudyLayout },
    setup() {
      const { createMessage } = useMessage();
      const router = useRouter();

      /** 当前步骤：theme 挑主题 / scenario 挑处境 / write 写经历 */
      const step = ref<'theme' | 'scenario' | 'write'>('theme');
      const themes = ref<StudyScenarioThemeVO[]>([]);
      const scenarios = ref<StudyScenarioVO[]>([]);
      const selectedTheme = ref('');
      const selectedScenario = ref<StudyScenarioVO | null>(null);
      /** 孩子自己写的经历 */
      const experience = ref('');
      const loading = ref(false);
      const submitting = ref(false);
      /** 断网提示与重试入口（F-BASE-08） */
      const netError = ref<StudyNetError | null>(null);
      /** 重试动作：断网后点「重试」重放上一次失败的服务端请求（V0 不做断网补交） */
      const retryAction = ref<null | (() => Promise<void>)>(null);

      /** 当前选中的情境（第三步用） */
      const selected = computed(() => selectedScenario.value);

      /** 底部主按钮文案 */
      const footerLabel = computed(() => {
        if (step.value === 'theme') {
          return '下一步：看处境';
        }
        if (step.value === 'scenario') {
          return '下一步：写经历';
        }
        return '去列提纲';
      });

      /** 底部主按钮是否可点：每一步都必须先满足本步的条件 */
      const canGo = computed(() => {
        if (step.value === 'theme') {
          return Boolean(selectedTheme.value);
        }
        if (step.value === 'scenario') {
          return Boolean(selectedScenario.value);
        }
        return experience.value.trim().length > 0;
      });

      const loadThemes = async () => {
        loading.value = true;
        try {
          const result = await fetchScenarioThemes();
          themes.value = result?.items ?? [];
          netError.value = null;
        } catch (e) {
          themes.value = [];
          // F-BASE-08：断网 → 可读提示 +「重试」入口；业务错误 → 保留原来的提示语义
          const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
          netError.value = state.offline ? state : null;
          if (state.offline) {
            retryAction.value = () => loadThemes();
          } else {
            createMessage.warning('主题没取到，稍后再看看');
          }
        } finally {
          loading.value = false;
        }
      };

      const loadScenarios = async () => {
        loading.value = true;
        try {
          const result = await fetchScenarioList(selectedTheme.value);
          scenarios.value = result?.items ?? [];
          netError.value = null;
        } catch (e) {
          scenarios.value = [];
          // F-BASE-08：断网 → 可读提示 +「重试」入口；业务错误 → 保留原来的提示语义
          const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
          netError.value = state.offline ? state : null;
          if (state.offline) {
            retryAction.value = () => loadScenarios();
          } else {
            createMessage.warning('处境没取到，稍后再看看');
          }
        } finally {
          loading.value = false;
        }
      };

      /** 选中主题 */
      const pickTheme = (themeCode: string) => {
        selectedTheme.value = themeCode;
        selectedScenario.value = null;
        scenarios.value = [];
      };

      /** 选中情境：取训练入口（提示题 / 限制条件 / 禁用提示） */
      const pickScenario = async (item: StudyScenarioVO) => {
        loading.value = true;
        try {
          selectedScenario.value = await fetchScenarioDetail(item.scenarioCode);
          netError.value = null;
        } catch (e) {
          // F-BASE-08：断网 → 可读提示 +「重试」入口；业务错误 → 保留原来的提示语义
          const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
          netError.value = state.offline ? state : null;
          if (state.offline) {
            retryAction.value = () => pickScenario(item);
          } else {
            createMessage.warning('这个处境没取到，换一个试试');
          }
        } finally {
          loading.value = false;
        }
      };

      /** 回到上一步 */
      const goBack = () => {
        if (step.value === 'scenario') {
          step.value = 'theme';
          return;
        }
        step.value = 'scenario';
        experience.value = '';
      };

      const onNext = async () => {
        if (!canGo.value) {
          return;
        }
        if (step.value === 'theme') {
          step.value = 'scenario';
          await loadScenarios();
          return;
        }
        if (step.value === 'scenario') {
          step.value = 'write';
          return;
        }
        submitting.value = true;
        try {
          // 跳到「语文写作」页（/study/writing，菜单 st_menu_writing 注册的真实路由）。
          // 原来跳的是 /study/kid/writing/train —— 那条路由在菜单树里**不存在**，
          // 孩子点「去列提纲」会被路由守卫抛到 404，流程直接断掉。
          // 已写的经历走 history.state 带过去（不塞进 URL，避免正文进浏览器历史）。
          await router.push({
            path: '/study/writing',
            query: { scenarioCode: selectedScenario.value?.scenarioCode ?? '' },
            state: { experience: experience.value },
          });
        } catch (e) {
          // F-BASE-08：断网 → 可读提示 +「重试」入口；业务错误 → 保留原来的提示语义
          const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
          netError.value = state.offline ? state : null;
          if (state.offline) {
            retryAction.value = () => onNext();
          } else {
            createMessage.warning('训练页没打开，稍后再试一次');
          }
        } finally {
          submitting.value = false;
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

      onMounted(() => {
        loadThemes();
      });

      return {
        step,
        themes,
        scenarios,
        selected,
        experience,
        loading,
        submitting,
        footerLabel,
        canGo,
        netError,
        pickTheme,
        pickScenario,
        goBack,
        onNext,
        onRetry,
      };
    },
  });
</script>

<style lang="less">
  .study-scenario {
    padding: 16px;

    &__spin {
      display: block;
      min-height: 240px;
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

    &__list {
      margin: 0;
      padding: 0;
      list-style: none;
    }

    &__card {
      margin-bottom: 12px;
      padding: 12px 16px;
      border: 1px solid @border-color-light;
      border-radius: 8px;
      background-color: @app-content-background;
    }

    &__card-head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
    }

    &__card-title {
      font-size: 16px;
      font-weight: 600;
      line-height: 1.4;
      color: @text-color-base;
    }

    &__card-meta {
      font-size: 12px;
      line-height: 1.4;
      color: @text-color-call-out;
    }

    &__desc {
      margin: 8px 0 0;
      font-size: 14px;
      line-height: 1.5715;
      color: @text-color-base;
    }

    &__clues {
      margin: 8px 0 0;
      padding: 0;
      list-style: none;
    }

    &__clue {
      margin-bottom: 4px;
      font-size: 13px;
      line-height: 1.5715;
      color: @text-color-call-out;
    }

    &__brief {
      margin-top: 12px;
      padding: 12px 16px;
      border: 1px solid @border-color-light;
      border-radius: 8px;
      background-color: @app-content-background;
    }

    &__question {
      margin: 8px 0 0;
      font-size: 16px;
      font-weight: 500;
      line-height: 1.5715;
      color: @text-color-base;
    }

    &__limit {
      margin: 4px 0 0;
      font-size: 13px;
      line-height: 1.5715;
      color: @text-color-call-out;
    }

    &__input {
      margin-top: 12px;
      font-size: 16px;
      line-height: 1.5715;
    }

    &__pick {
      min-height: 44px;
      margin-top: 8px;
      font-size: 14px;
      border-radius: 8px;
    }

    &__back {
      min-height: 44px;
      margin-bottom: 8px;
      font-size: 14px;
      border-radius: 8px;
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
