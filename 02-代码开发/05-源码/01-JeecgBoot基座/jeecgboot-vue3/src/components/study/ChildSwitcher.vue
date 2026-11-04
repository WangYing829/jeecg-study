<template>
  <!-- 没有可切换的孩子时不渲染任何东西：学习端只有一个孩子时不该出现空壳 -->
  <div v-if="selectOptions.length > 0" class="study-child-switcher">
    <span class="study-child-switcher__label">{{ label }}</span>
    <a-select
      class="study-child-switcher__select"
      :value="childCode"
      :options="selectOptions"
      :loading="loadingState"
      :disabled="disabled"
      placeholder="切换孩子"
      @change="onChange"
    />
  </div>
</template>

<script lang="ts">
  import { computed, defineComponent, onMounted, ref, watch } from 'vue';
  import { fetchChildren, type StudyChildVO } from '/@/views/study/api';

  /**
   * 孩子档案切换器（设计规范 §7.3 的可复用上下文条）。
   *
   * 家长端顶部条用它切换当前孩子；学习端页面也可以挂一条，
   * 账号下只关联了一个孩子时组件不渲染任何内容。
   *
   * 数据口径：
   *   · 孩子清单只来自服务端下发的已关联孩子，前端不自行拼装（R-04）；
   *   · 只展示昵称，不展示登录账号，也不展示任何其他孩子的信息（R-04）；
   *   · 切换动作由父组件决定是否重新取数，组件本身不改数据范围。
   *
   * 接口封装统一走 views/study/api.ts，组件不直接调 defHttp。
   */
  export default defineComponent({
    name: 'StudyChildSwitcher',
    props: {
      /** 当前孩子的业务ID */
      childCode: { type: String, default: '' },
      /** 外部已取好的选项（给了就不再自行取数） */
      childOptions: { type: Array as () => { label: string; value: string }[], default: () => [] },
      /** 外部已取好的孩子清单（给了就不再自行取数） */
      children: { type: Array as () => StudyChildVO[], default: () => [] },
      /** 加载状态（外部受控时传 true） */
      loading: { type: Boolean, default: false },
      /** 禁用切换 */
      disabled: { type: Boolean, default: false },
      /** 是否自行取数 */
      autoLoad: { type: Boolean, default: true },
      /** 标签文案 */
      label: { type: String, default: '当前孩子' },
    },
    emits: ['switch-child', 'update:childCode'],
    setup(props, { emit }) {
      /** 组件自行取到的孩子清单 */
      const loadedChildren = ref<StudyChildVO[]>([]);
      /** 组件自行取数时的加载状态 */
      const loadingState = ref(false);

      /** 生效的孩子清单：外部给了就用外部的 */
      const effectiveChildren = computed<StudyChildVO[]>(() =>
        props.children && props.children.length > 0 ? props.children : loadedChildren.value,
      );

      /** 下拉选项：优先用外部给的选项，否则由孩子清单映射 */
      const selectOptions = computed(() => {
        if (props.childOptions && props.childOptions.length > 0) {
          return props.childOptions;
        }
        return effectiveChildren.value
          .filter((item) => item?.childCode)
          .map((item) => ({ label: item.nickname || item.childCode, value: item.childCode }));
      });

      /** 自行取数：只取一次，失败就保持空清单（不打断页面） */
      const loadChildren = async () => {
        loadingState.value = true;
        try {
          const result = await fetchChildren();
          loadedChildren.value = Array.isArray(result) ? result : [];
        } catch (e) {
          loadedChildren.value = [];
        } finally {
          loadingState.value = false;
        }
      };

      /** 切换孩子：把选中的业务ID交给父组件 */
      const onChange = (value: string) => {
        emit('switch-child', value);
        emit('update:childCode', value);
      };

      onMounted(() => {
        if (props.autoLoad && props.childOptions.length === 0 && props.children.length === 0) {
          loadChildren();
        }
      });

      // 外部给了孩子清单时，把内部缓存清掉，避免两份数据不一致
      watch(
        () => props.children,
        (value) => {
          if (value && value.length > 0) {
            loadedChildren.value = [];
          }
        },
      );

      return { loadingState, selectOptions, effectiveChildren, onChange };
    },
  });
</script>

<style lang="less">
  .study-child-switcher {
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 0;

    &__label {
      flex: 0 0 auto;
      font-size: 14px;
      line-height: 1.4;
      color: @text-color-call-out;
    }

    /* 点击目标 ≥44px（§10.1） */
    &__select {
      flex: 1 1 auto;
      min-width: 0;
      max-width: 220px;
      min-height: 44px;
      font-size: 16px;
    }

    @media (max-width: 992px) {
      &__select {
        max-width: 160px;
      }
    }
  }
</style>
