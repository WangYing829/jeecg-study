<template>
  <!-- 学习端独立全屏布局：根节点显式锁定浅色，不随系统 / 全局暗色开关切换（§4.2③） -->
  <div class="study-layout" data-theme="light">
    <!-- 轻量顶栏 48px：模块名 / 连续天数 / 护眼状态（不显示用眼时长数字，§10.5） -->
    <header class="study-layout__header">
      <div class="study-layout__module">{{ moduleTitle }}</div>
      <div class="study-layout__meta">
        <span v-if="streakDays > 0" class="study-layout__streak">
          <Icon icon="ant-design:fire" />
          <span class="study-layout__streak-text">{{ streakDays }} 天</span>
        </span>
        <span v-if="restRequired" class="study-layout__eye is-resting">
          <Icon icon="ant-design:pause-circle" />
        </span>
        <span v-else class="study-layout__eye">
          <Icon icon="ant-design:eye" />
        </span>
      </div>
    </header>

    <!-- 主任务区：全宽，padding 由页面自己给；一屏一任务（§9.7 / §10.2） -->
    <main class="study-layout__body">
      <slot>
        <router-view />
      </slot>
    </main>

    <!-- 底部主操作条 64px：一屏只允许一个主按钮（§9.7 / §10.2） -->
    <footer class="study-layout__footer">
      <slot name="footer" />
    </footer>

    <!-- 护眼护罩挂载点：由服务端 restRequired 决定，只能由用户显式确认清除（R-03） -->
    <StudyEyeShield
      :rest-required="restRequired"
      :rest-prompt="restPrompt"
      @confirm="eye.confirmRest"
    />
  </div>
</template>

<script lang="ts">
  import { defineComponent, onBeforeUnmount, onMounted } from 'vue';
  import { Icon } from '/@/components/Icon';
  import StudyEyeShield from '/@/components/study/StudyEyeShield.vue';
  import { useStudyThemeLock } from '/@/components/study/StudyThemeLock';
  import { useSharedEyeState } from '/@/components/study/StudyEyeState';

  /**
   * 学习端（孩子端）独立全屏布局。
   *
   * <p>与上游 {@code layouts/default} 的差异（设计规范 §9.5 / §9.7）：</p>
   * <ul>
   *   <li><b>不加载</b>管理端顶栏、侧栏、页签与面包屑；</li>
   *   <li>顶栏压到 48px，主操作条固定在底部 64px（含安全区适配）；</li>
   *   <li>主题<b>锁定浅色</b>（{@code data-theme="light"}），且<b>不提供</b>切换浅色 / 深色的入口；</li>
   *   <li>挂载护眼护罩（遮罩的清除只能由用户显式确认触发）。</li>
   * </ul>
   */
  export default defineComponent({
    name: 'StudyLayout',
    components: { Icon, StudyEyeShield },
    props: {
      /** 顶栏模块名 */
      moduleTitle: { type: String, default: '学习' },
      /** 连续学习天数 */
      streakDays: { type: Number, default: 0 },
      /**
       * 是否启动用眼会话。
       *
       * <p>孩子端登录页传 {@code false}：那时还没有登录态，
       * 启动会话只会打出一个必然 401 的请求，而且会把「未登录」也计进用眼时长。</p>
       */
      eyeSession: { type: Boolean, default: true },
    },
    setup(props) {
      // 锁定浅色主题：学习端不随系统暗色或全局开关变化
      useStudyThemeLock();

      // 护眼状态：学习端全局共享同一份（布局与护眼设置页拿到的是同一个实例）。
      //
      // ⚠ 这里不用 provide / inject：页面模板里写的是 <StudyLayout>，布局是页面的**子组件**，
      //   而 provide / inject 的方向是「父 provide → 子 inject」，
      //   布局 provide 的东西页面（父级）永远 inject 不到，只会拿到 null。
      const eye = useSharedEyeState();

      // 护眼会话的生命周期由**布局**持有：布局每个学习页都会挂载/卸载一次，
      // 会话随布局起停；护眼设置页只读状态，不负责开关会话。
      onMounted(() => {
        if (props.eyeSession) {
          eye.start();
        }
      });
      onBeforeUnmount(() => {
        if (props.eyeSession) {
          eye.stop();
        }
      });

      // ⚠ 模板里用到的 ref 必须挂在 setup 返回值的**顶层**，Vue 才会自动解包。
      //   写成 eye.restRequired 拿到的是 Ref **对象本身**（普通对象的属性不解包），
      //   恒为真 → 护眼遮罩永远盖住整个学习端，点「我休息好了」也解不开（R-03 反而成了死锁）。
      const { restRequired, restPrompt } = eye;

      return { eye, restRequired, restPrompt };
    },
  });
</script>

<style lang="less">
  .study-layout {
    position: fixed;
    inset: 0;
    z-index: 1100;
    display: flex;
    flex-direction: column;
    width: 100%;
    min-width: 0;
    background-color: @white;
    color: @text-color-base;
    font-size: 16px;
    line-height: 1.5715;

    /* 学习端可点击元素自补焦点环（§11.4①） */
    :focus-visible {
      outline: 2px solid @primary-color !important;
      outline-offset: 2px;
    }

    &__header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 16px;
      flex: 0 0 auto;
      height: 48px;
      padding: 0 16px;
      border-bottom: 1px solid @border-color-light;
      background-color: @app-content-background;
    }

    &__module {
      font-size: 16px;
      font-weight: 600;
      line-height: 1.4;
      color: @text-color-base;
    }

    &__meta {
      display: flex;
      align-items: center;
      gap: 16px;
    }

    &__streak {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      font-size: 14px;
      line-height: 1.4;
      color: @success-color;
    }

    &__eye {
      font-size: 16px;
      color: @text-color-call-out;

      &.is-resting {
        color: @warning-color;
      }
    }

    &__body {
      flex: 1 1 auto;
      min-width: 0;
      overflow-y: auto;
    }

    /* 底部主操作条 64px，一屏只允许一个主按钮 */
    &__footer {
      display: flex;
      align-items: center;
      flex: 0 0 auto;
      height: 64px;
      padding: 0 16px;
      padding-bottom: env(safe-area-inset-bottom);
      border-top: 1px solid @border-color-light;
      background-color: @white;
    }

    @media (max-width: 992px) {
      &__header {
        padding: 0 12px;
      }

      &__footer {
        padding: 0 12px;
        padding-bottom: env(safe-area-inset-bottom);
      }
    }
  }
</style>
