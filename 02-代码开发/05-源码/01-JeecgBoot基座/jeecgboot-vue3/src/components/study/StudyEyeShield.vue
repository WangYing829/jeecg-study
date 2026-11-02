<template>
  <!-- 护眼休息遮罩：阻断式展示，只能由用户显式确认清除（R-03 / 自检清单 #38） -->
  <div v-if="restRequired" class="study-eye-shield" role="alertdialog" aria-modal="true">
    <div class="study-eye-shield__panel">
      <div class="study-eye-shield__icon">
        <Icon icon="ant-design:eye" />
      </div>
      <h2 class="study-eye-shield__title">该休息一下啦</h2>
      <p class="study-eye-shield__action">{{ restPrompt || defaultPrompt }}</p>
      <p class="study-eye-shield__note">用眼时间到了，休息完再回来继续做题</p>
      <a-button type="primary" class="study-eye-shield__confirm" @click="onConfirm">我休息好了</a-button>
    </div>
  </div>
</template>

<script lang="ts">
  import { defineComponent } from 'vue';
  import { Icon } from '/@/components/Icon';

  /**
   * 护眼休息遮罩。
   *
   * <p>关键约束（红线 R-03 / 设计规范 §10.5）：</p>
   * <ul>
   *   <li>遮罩<b>不可自动消失</b>：本组件内没有任何定时器，
   *       清除只能由用户点击「我休息好了」触发 {@code confirm} 事件；</li>
   *   <li>是否显示由服务端下发的 {@code restRequired} 决定，前端不自行计时；</li>
   *   <li>遮罩 z-index 取 2000，落在设计规范 §7.4 规定的 1000–3000 区间内。</li>
   * </ul>
   */
  export default defineComponent({
    name: 'StudyEyeShield',
    components: { Icon },
    props: {
      /** 服务端下发的休息阻断标志 */
      restRequired: { type: Boolean, default: false },
      /** 服务端下发的休息提醒文案（有可执行的具体动作才显示） */
      restPrompt: { type: String, default: '' },
    },
    emits: ['confirm'],
    data() {
      return {
        /** 前端兜底的远眺提示（服务端没给文案时用） */
        defaultPrompt: '看向 6 米外的地方，保持 20 秒',
      };
    },
    methods: {
      onConfirm() {
        this.$emit('confirm');
      },
    },
  });
</script>

<style lang="less">
  .study-eye-shield {
    position: fixed;
    inset: 0;
    z-index: 2000;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 24px;
    background-color: @app-content-background;

    &__panel {
      width: 100%;
      max-width: 480px;
      padding: 24px;
      border: 1px solid @border-color-light;
      border-radius: 12px;
      background-color: @white;
      text-align: center;
    }

    &__icon {
      font-size: 20px;
      color: @text-color-call-out;
    }

    &__title {
      margin: 12px 0 0;
      font-size: 20px;
      font-weight: 600;
      line-height: 1.4;
      color: @text-color-base;
    }

    &__action {
      margin: 12px 0 0;
      font-size: 16px;
      line-height: 1.5715;
      color: @text-color-base;
    }

    &__note {
      margin: 8px 0 0;
      font-size: 14px;
      line-height: 1.5715;
      color: @text-color-call-out;
    }

    &__confirm {
      margin-top: 16px;
      width: 100%;
      min-height: 44px;
      font-size: 18px;
      border-radius: 8px;
    }
  }
</style>
