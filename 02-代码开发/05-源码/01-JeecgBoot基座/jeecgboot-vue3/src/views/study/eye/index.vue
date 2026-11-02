<template>
  <StudyLayout module-title="护眼设置">
    <div class="study-eye">
      <!-- 断网：可读提示 +「重试」入口（F-BASE-08：V0 不做离线补交，只重试） -->
      <a-alert
        v-if="netError"
        class="study-eye__net-error"
        type="warning"
        show-icon
        :message="netError.message"
      >
        <template #action>
          <a-button size="small" @click="onRetry">重试</a-button>
        </template>
      </a-alert>

      <a-spin :spinning="loading" class="study-eye__spin">
        <section class="study-eye__card">
          <h2 class="study-eye__title">蓝光过滤</h2>
          <p class="study-eye__desc">屏幕调暖一点，晚上看书舒服些</p>
          <StudyBlueLightSwitch :checked="blueLight" :auto-persist="false" @update:checked="onToggle" />
        </section>

        <section class="study-eye__card">
          <h2 class="study-eye__title">休息提醒</h2>
          <p class="study-eye__desc">用眼到时间会弹出遮罩，休息完点一下才能继续</p>
          <div class="study-eye__row">
            <span class="study-eye__row-label">提醒间隔</span>
            <span class="study-eye__row-value">{{ intervalLabel }}</span>
          </div>
          <div class="study-eye__row">
            <span class="study-eye__row-label">护眼状态</span>
            <span class="study-eye__row-value">
              <Icon icon="ant-design:eye" />
              <span class="study-eye__row-text">休息提醒已开启</span>
            </span>
          </div>
        </section>

        <section class="study-eye__card">
          <h2 class="study-eye__title">远眺一下</h2>
          <p class="study-eye__desc">看向 6 米外的地方，保持 20 秒，眼睛会舒服很多</p>
        </section>
      </a-spin>
    </div>

    <template #footer>
      <a-button type="primary" class="study-eye__submit" @click="onSave">保存设置</a-button>
    </template>
  </StudyLayout>
</template>

<script lang="ts">
  import { computed, defineComponent, onMounted, ref, watch } from 'vue';
  import { Icon } from '/@/components/Icon';
  import { useMessage } from '/@/hooks/web/useMessage';
  import StudyLayout from '/@/layouts/study/index.vue';
  import StudyBlueLightSwitch from '/@/components/study/StudyBlueLightSwitch.vue';
  import { writeBlueLight } from '/@/components/study/StudyBlueLightState';
  import { useSharedEyeState } from '/@/components/study/StudyEyeState';
  import { fetchContext } from '../api';
  import { describeNetError, OFFLINE_HINT, RETRY_LABEL, type StudyNetError } from '../net';

  /**
   * 学习端护眼设置页（设计规范 §10.5）。
   *
   * <p>约束：</p>
   * <ul>
   *   <li>孩子端<b>不显示</b>用眼时长数字，只显示状态（§10.5 / R03-5）；</li>
   *   <li>休息提醒的阻断由服务端 {@code restRequired} 决定，页面不自行计时（R-03）；</li>
   *   <li>提醒间隔是家长在家长端配的，本页只读展示。</li>
   * </ul>
   */
  export default defineComponent({
    name: 'StudyEyePage',
    components: { Icon, StudyLayout, StudyBlueLightSwitch },
    setup() {
      const { createMessage } = useMessage();

      /**
       * 护眼状态：学习端<b>全局共享的那一份</b>（与学习端布局是同一个实例）。
       *
       * <p>⚠ 这里<b>不能</b>用 inject，也<b>不能</b>靠 defineExpose + 模板 ref：</p>
       * <ul>
       *   <li>provide / inject 的方向反了——页面模板里写的是 {@code <StudyLayout>}，
       *       布局是页面的<b>子</b>组件，而 provide / inject 是「父 provide → 子 inject」，
       *       布局 provide 的东西页面（父级）永远 inject 不到，只会拿到 null；</li>
       *   <li>defineExpose + 模板 ref 也不可靠——模板 ref 拿到的是 Vue 的深层 reactive 代理，
       *       代理会把嵌套对象里的 ref <b>自动解包</b>
       *       （{@code eye.eyeIntervalMin} 拿到的是数字而不是 Ref），
       *       再写 {@code .value} 反而永远是 undefined。</li>
       * </ul>
       * <p>共享单例最直白：页面与布局拿到同一批 ref，不依赖任何跨组件传值技巧。</p>
       */
      const eye = useSharedEyeState();

      /** 提醒间隔：优先用护眼会话下发的值，没有才用家长端上下文里的值 */
      const contextIntervalMin = ref<number | undefined>(undefined);
      const intervalMin = computed(() => eye.eyeIntervalMin.value ?? contextIntervalMin.value);
      const intervalLabel = computed(() =>
        intervalMin.value ? `每 ${intervalMin.value} 分钟提醒一次` : '还没有设置提醒间隔',
      );

      const blueLight = ref(false);
      const loading = ref(false);
      /** 断网提示与重试入口（F-BASE-08） */
      const netError = ref<StudyNetError | null>(null);
      /** 重试动作：断网后点「重试」重放上一次失败的服务端请求（V0 不做断网补交） */
      const retryAction = ref<null | (() => Promise<void>)>(null);

      // 蓝光开关：会话下发后同步到页面
      watch(
        () => eye.blueLightOn.value,
        (value) => {
          if (typeof value === 'boolean') {
            blueLight.value = value;
          }
        },
        { immediate: true },
      );

      const load = async () => {
        loading.value = true;
        try {
          const context = await fetchContext();
          // 孩子身份没有 study:parent:context:query 权限，这一路可能拿不到间隔；
          // 拿不到就保持 undefined，交给护眼会话下发的值（见上面的 intervalMin）。
          if (context?.eyeIntervalMin) {
            contextIntervalMin.value = context.eyeIntervalMin;
          }
          netError.value = null;
        } catch (e) {
          // F-BASE-08：断网 → 可读提示 +「重试」入口；不再静默失败
          const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
          netError.value = state.offline ? state : null;
          if (state.offline) {
            retryAction.value = () => load();
          }
          // 非断网（例如上下文接口要家长权限、孩子身份拿不到）不再弹提示：
          // 提醒间隔已由护眼会话随响应下发，不必打扰孩子。
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

      const onToggle = (value: boolean) => {
        blueLight.value = value;
      };

      const onSave = async () => {
        writeBlueLight(blueLight.value);
        await eye.setBlueLight(blueLight.value);
        createMessage.success('护眼设置已保存');
      };

      onMounted(() => {
        load();
      });

      return { intervalMin, intervalLabel, blueLight, loading, netError, onToggle, onSave, onRetry };
    },
  });
</script>

<style lang="less">
  .study-eye {
    padding: 16px;

    &__spin {
      display: block;
      min-height: 240px;
    }

    &__card {
      margin-bottom: 16px;
      padding: 16px;
      border: 1px solid @border-color-light;
      border-radius: 12px;
      background-color: @white;
    }

    &__title {
      margin: 0;
      font-size: 18px;
      font-weight: 600;
      line-height: 1.4;
      color: @text-color-base;
    }

    &__desc {
      margin: 4px 0 12px;
      font-size: 14px;
      line-height: 1.5715;
      color: @text-color-call-out;
    }

    &__row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 16px;
      padding: 8px 0;
    }

    &__row-label {
      font-size: 14px;
      color: @text-color-call-out;
    }

    &__row-value {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      font-size: 16px;
      color: @text-color-base;
    }

    &__row-text {
      font-size: 14px;
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
