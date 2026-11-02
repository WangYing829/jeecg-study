<template>
  <span class="study-action-column">
    <!-- 行内动作：≤3 个全部行内；>3 个只留前 2 个行内（第 3 个起进「更多」） -->
    <template v-for="action in inlineActions" :key="action.key">
      <a-popconfirm
        v-if="action.confirm"
        :title="action.confirm.title"
        :ok-text="action.confirm.okText || '确定'"
        :cancel-text="action.confirm.cancelText || '取消'"
        @confirm="runAction(action)"
      >
        <a class="study-action-column__item" :class="toneClass(action)">{{ action.label }}</a>
      </a-popconfirm>
      <a
        v-else
        class="study-action-column__item"
        :class="toneClass(action)"
        @click="runAction(action)"
      >
        {{ action.label }}
      </a>
    </template>

    <!-- 更多：溢出动作收进下拉，危险操作走 Modal.confirm（§8.7） -->
    <a-dropdown v-if="overflowActions.length > 0" trigger="click">
      <a class="study-action-column__item study-action-column__more">更多<Icon icon="ant-design:down" /></a>
      <template #overlay>
        <a-menu>
          <a-menu-item v-for="action in overflowActions" :key="action.key" @click="runOverflow(action)">
            <span :class="toneClass(action)">{{ action.label }}</span>
          </a-menu-item>
        </a-menu>
      </template>
    </a-dropdown>
  </span>
</template>

<script lang="ts">
  import { computed, defineComponent } from 'vue';
  import { Modal } from 'ant-design-vue';
  import { Icon } from '/@/components/Icon';
  import { usePermission } from '/@/hooks/web/usePermission';

  /** 二次确认配置 */
  export interface StudyActionConfirm {
    /** 确认框标题 */
    title: string;
    /** 确定按钮文案 */
    okText?: string;
    /** 取消按钮文案 */
    cancelText?: string;
  }

  /** 单个动作项 */
  export interface StudyActionColumnAction {
    /** 唯一键 */
    key: string;
    /** 文案 */
    label: string;
    /** 图标（ant-design:* 前缀，§8.8） */
    icon?: string;
    /** 显式危险动作 */
    danger?: boolean;
    /** 显式成功动作 */
    success?: boolean;
    /** 权限码，走 hasPermission（§8.7）；不填表示所有人可见 */
    auth?: string | string[];
    /** 二次确认配置 */
    confirm?: StudyActionConfirm;
    /** 点击回调 */
    onClick?: () => void;
  }

  /**
   * 统一操作列（设计规范 §8.7 / §9.3）。
   *
   * <p>规则全部收在这里，页面不再手写 {@code a-space} + 散落链接：</p>
   * <ul>
   *   <li><b>数量</b>：≤3 个动作全部行内；>3 个时前 2 个行内，第 3 个起进「更多」下拉；</li>
   *   <li><b>元素</b>：用 {@code a} 链接（不是按钮），图标 + 文案，间距 4px，整列不换行；</li>
   *   <li><b>二次确认</b>：行内用 {@code a-popconfirm}；下拉内用 {@code Modal.confirm}
   *       （危险操作 {@code okType: 'danger'}）；</li>
   *   <li><b>权限</b>：动作项的 {@code auth} 字段走 {@code hasPermission}；</li>
   *   <li><b>危险判定</b>：显式 {@code danger} / {@code success}，或文案含「删除」「另存」
   *       （危险优先）——文案里带「删除」的动作一律按危险处理。</li>
   * </ul>
   */
  export default defineComponent({
    name: 'StudyActionColumn',
    components: { Icon },
    props: {
      /** 动作列表 */
      actions: { type: Array as () => StudyActionColumnAction[], default: () => [] },
      /** 行内最多显示几个（不给则按 §8.7 自动判定） */
      maxInline: { type: Number, default: 0 },
    },
    setup(props) {
      const { hasPermission } = usePermission();

      /** 有权限的动作 */
      const permitted = computed(() =>
        (props.actions || []).filter((action) => !action.auth || hasPermission(action.auth)),
      );

      /** 行内动作：≤3 个全行内；>3 个只留前 2 个 */
      const inlineActions = computed(() => {
        const list = permitted.value;
        const limit = props.maxInline > 0 ? props.maxInline : list.length <= 3 ? list.length : 2;
        return list.slice(0, limit);
      });

      /** 溢出到「更多」的动作 */
      const overflowActions = computed(() => permitted.value.slice(inlineActions.value.length));

      /** 危险判定：显式标记优先，否则按文案里的「删除」「另存」兜底 */
      const isDanger = (action: StudyActionColumnAction) => {
        if (action.danger) {
          return true;
        }
        if (action.success) {
          return false;
        }
        return action.label.includes('删除') || action.label.includes('另存');
      };

      /** 动作的语义色 class */
      const toneClass = (action: StudyActionColumnAction) => {
        if (isDanger(action)) {
          return 'is-danger';
        }
        if (action.success) {
          return 'is-success';
        }
        return '';
      };

      /** 行内动作直接执行 */
      const runAction = (action: StudyActionColumnAction) => {
        action.onClick?.();
      };

      /** 下拉动作：需要二次确认的走 Modal.confirm */
      const runOverflow = (action: StudyActionColumnAction) => {
        if (!action.confirm) {
          action.onClick?.();
          return;
        }
        Modal.confirm({
          title: action.confirm.title,
          okText: action.confirm.okText || '确定',
          cancelText: action.confirm.cancelText || '取消',
          okType: isDanger(action) ? 'danger' : 'default',
          onOk: () => action.onClick?.(),
        });
      };

      return { inlineActions, overflowActions, toneClass, runAction, runOverflow };
    },
  });
</script>

<style lang="less">
  .study-action-column {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    white-space: nowrap;

    &__item {
      color: @primary-color;
      cursor: pointer;

      &.is-danger {
        color: @error-color;
      }

      &.is-success {
        color: @success-color;
      }
    }

    &__more {
      display: inline-flex;
      align-items: center;
      gap: 4px;
    }
  }
</style>
