<template>
  <div class="study-parent-shell">
    <!-- 顶部条：孩子切换 + 页面导航 + 登出（≤48px，§9.6） -->
    <header class="study-parent-shell__bar">
      <span class="study-parent-shell__child">{{ childName || '还没有关联的孩子' }}</span>
      <a-select
        v-if="childOptions.length > 0"
        class="study-parent-shell__select"
        :value="childCode"
        :options="childOptions"
        placeholder="切换孩子"
        @change="onSwitch"
      />

      <!--
        家长端自己的页面导航。
        家长端**没有侧栏**（§9.6 / 自检清单 #27），顶部条就成了唯一的页面入口，
        必须自带导航，否则家长端 3 个页面之间无处可去。
      -->
      <nav class="study-parent-shell__nav">
        <router-link
          v-for="item in navItems"
          :key="item.path"
          class="study-parent-shell__nav-item"
          :class="{ 'is-active': isActive(item.path) }"
          :to="item.path"
        >
          {{ item.label }}
        </router-link>
      </nav>

      <span class="study-parent-shell__home">
        <Icon icon="ant-design:home" />
      </span>
      <!-- 登出：顶部条遮住了管理端顶栏后，家长端必须自带退出入口 -->
      <a-button class="study-parent-shell__logout" size="small" @click="onLogout">退出</a-button>
    </header>

    <!-- 内容区：全宽 + 16px 留白，卡片式分区（§9.6） -->
    <main class="study-parent-shell__body">
      <slot />
    </main>
  </div>
</template>

<script lang="ts">
  import { defineComponent } from 'vue';
  import { useRoute } from 'vue-router';
  import { Icon } from '/@/components/Icon';
  import { useUserStore } from '/@/store/modules/user';

  /**
   * 家长端外壳（设计规范 §9.6）。
   *
   * <p>与学习端的区别：</p>
   * <ul>
   *   <li><b>不加载</b>管理端顶栏与侧栏——本外壳是<b>全屏固定层</b>（position: fixed），
   *       盖在管理端布局之上。原因：上游管理端布局在 {@code < 992px} 时会把侧栏渲染成
   *       <b>常开的抽屉 + 遮罩</b>（{@code layouts/default/sider/index.vue:2-12}，
   *       {@code :open="!getCollapsed"}），内容区会被抽屉和遮罩整片盖住、
   *       表格被裁切——家长端在手机上会直接「白屏 + 一块灰」。</li>
   *   <li>主题<b>跟随全局暗色开关</b>（学习端才锁定浅色）；</li>
   *   <li>只读展示，不提供新增 / 编辑 / 删除入口（§9.6 写操作约束）；</li>
   *   <li>无侧栏后，顶部条自带页面导航与登出入口。</li>
   * </ul>
   */
  export default defineComponent({
    name: 'StudyParentShell',
    components: { Icon },
    props: {
      /** 当前孩子的昵称 */
      childName: { type: String, default: '' },
      /** 当前孩子的业务ID */
      childCode: { type: String, default: '' },
      /** 已关联的孩子（只能切到已关联的孩子） */
      childOptions: { type: Array as () => { label: string; value: string }[], default: () => [] },
    },
    emits: ['switch-child'],
    setup() {
      const route = useRoute();
      const userStore = useUserStore();

      /** 家长端的页面导航（家长端没有侧栏，这里是唯一入口） */
      const navItems = [
        { path: '/study-parent/report', label: '学习报告' },
        { path: '/study-parent/error', label: '错题分析' },
        { path: '/study-parent/feedback', label: '作文反馈' },
      ];

      /** 当前页是否命中某个导航项 */
      const isActive = (path: string) => route.path === path;

      /** 登出：确认后清 token 回登录页 */
      const onLogout = () => {
        userStore.confirmLoginOut();
      };

      return { navItems, isActive, onLogout };
    },
    methods: {
      onSwitch(value: string) {
        this.$emit('switch-child', value);
      },
    },
  });
</script>

<style lang="less">
  .study-parent-shell {
    /* 全屏固定层：盖住管理端布局（抽屉 + 遮罩 + 顶栏 + 侧栏）。
       z-index 必须高于上游 antd 抽屉遮罩（1000）与学习端布局（1100 同级不冲突）。 */
    position: fixed;
    inset: 0;
    z-index: 1100;
    display: flex;
    flex-direction: column;
    width: 100%;
    min-width: 0;
    background-color: @app-content-background;
    color: @text-color-base;
    font-size: 14px;
    line-height: 1.5715;

    /* 家长端 / 学习端的可点击元素自补焦点环（§11.4①） */
    :focus-visible {
      outline: 2px solid @primary-color !important;
      outline-offset: 2px;
    }

    &__bar {
      display: flex;
      align-items: center;
      gap: 16px;
      flex: 0 0 auto;
      height: 48px;
      padding: 0 16px;
      border-bottom: 1px solid @border-color-light;
      background-color: @app-content-background;
    }

    &__child {
      font-size: 15px;
      font-weight: 600;
      line-height: 1.4;
      color: @text-color-base;
    }

    &__select {
      min-width: 160px;
    }

    /* 页面导航（家长端无侧栏时的唯一入口）
       不用横向滚动条：F-16 明确禁止窄屏横向滚动，导航项一律换行处理。 */
    &__nav {
      display: flex;
      align-items: center;
      gap: 4px;
      min-width: 0;
      flex-wrap: wrap;
    }

    &__nav-item {
      flex: 0 0 auto;
      padding: 4px 10px;
      border-radius: 6px;
      font-size: 14px;
      line-height: 1.5715;
      color: @text-color-base;
      text-decoration: none;

      &:hover {
        background-color: fade(@primary-color, 8%);
      }

      &.is-active {
        color: @primary-color;
        font-weight: 600;
      }
    }

    &__home {
      margin-left: auto;
      font-size: 16px;
      color: @text-color-call-out;
    }

    &__logout {
      flex: 0 0 auto;
    }

    &__body {
      flex: 1 1 auto;
      min-width: 0;
      overflow-y: auto;
      padding: 16px;
      padding-bottom: calc(16px + env(safe-area-inset-bottom));
    }

    /* 窄屏：顶部条换成两行——第一行「孩子名 + 切换 + 家庭 + 退出」，
       第二行放页面导航。窄屏不做横向滚动（F-16），导航项换行而不是左右拖。 */
    @media (max-width: 992px) {
      &__bar {
        gap: 8px;
        row-gap: 4px;
        flex-wrap: wrap;
        height: auto;
        min-height: 48px;
        padding: 4px 12px;
      }

      &__nav {
        order: 3;
        width: 100%;
      }

      &__body {
        padding: 12px;
        padding-bottom: calc(12px + env(safe-area-inset-bottom));
      }
    }
  }
</style>
