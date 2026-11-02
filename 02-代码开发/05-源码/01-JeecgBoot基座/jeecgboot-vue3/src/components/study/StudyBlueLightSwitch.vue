<template>
  <div class="study-blue-light">
    <div class="study-blue-light__text">
      <span class="study-blue-light__title">蓝光过滤</span>
      <span class="study-blue-light__hint">屏幕偏暖一点，晚上看着舒服些</span>
    </div>
    <a-switch
      class="study-blue-light__switch"
      :checked="current"
      checked-children="开"
      un-checked-children="关"
      @change="onChange"
    />
  </div>
</template>

<script lang="ts">
  import { computed, defineComponent, onMounted, ref } from 'vue';
  import { readBlueLight, writeBlueLight } from './StudyBlueLightState';

  /**
   * 蓝光过滤开关。
   *
   * <p>设计规范 §10.5：蓝光过滤是<b>全局</b> filter 类切换，状态持久化。
   * 过滤值写在样式里（走 less 变量），不在此处拼接颜色字面量（F-01）。</p>
   *
   * <p>受控用法：传入 {@code checked} 时由父组件决定状态；
   * 非受控用法：组件自己读本地存储，并用 {@code autoPersist} 控制是否立即落盘。</p>
   */
  export default defineComponent({
    name: 'StudyBlueLightSwitch',
    props: {
      /** 受控状态；不传则由组件自己维护 */
      checked: { type: Boolean, default: undefined },
      /** 是否立即持久化；关闭时由调用方在「保存」时统一落盘 */
      autoPersist: { type: Boolean, default: true },
    },
    emits: ['update:checked'],
    setup(props, { emit }) {
      const internal = ref(false);

      const current = computed(() => (props.checked === undefined ? internal.value : props.checked));

      const onChange = (value: boolean) => {
        internal.value = value;
        if (props.autoPersist) {
          writeBlueLight(value);
        }
        emit('update:checked', value);
      };

      onMounted(() => {
        if (props.checked === undefined) {
          internal.value = readBlueLight();
        }
      });

      return { current, onChange };
    },
  });
</script>

<style lang="less">
  /* 全局过滤：暖色偏移，不使用高饱和颜色字面量（F-01） */
  html.study-blue-light-on {
    filter: sepia(0.32) saturate(1.05) brightness(0.97);
  }

  .study-blue-light {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    padding: 12px 16px;
    border: 1px solid @border-color-light;
    border-radius: 8px;
    background-color: @app-content-background;

    &__text {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    &__title {
      font-size: 16px;
      font-weight: 600;
      line-height: 1.4;
      color: @text-color-base;
    }

    &__hint {
      font-size: 14px;
      line-height: 1.5715;
      color: @text-color-call-out;
    }
  }
</style>
