<template>
  <a-tag class="study-enum-tag" :color="tagColor">{{ value }}</a-tag>
</template>

<script lang="ts">
  import { computed, defineComponent } from 'vue';

  /**
   * 枚举标签的<b>候选色板</b>（设计规范 §8.6）。
   *
   * <p>⚠ 这里<b>只能用 antd 预设名</b>：预设名走的是 antd 的语义映射，
   * 能自动适配暗色模式；裸 hex 不能。</p>
   *
   * <p>刻意<b>不放 {@code error}</b>：关系 / 角色这类标签不是负面信息，
   * 红色会误导家长（R-04 的语义：不给孩子贴负面标签）。</p>
   */
  const PALETTES: Record<string, string[]> = {
    relation: ['blue', 'success', 'warning', 'processing', 'info', 'default'],
    role: ['processing', 'blue', 'default'],
    status: ['success', 'default', 'warning'],
  };

  /** 默认色板 */
  const DEFAULT_PALETTE = 'relation';

  /**
   * 枚举彩色中文标签（设计规范 §8.6 / §9.3）。
   *
   * <p><b>同名同色</b>：同一个中文标签在任何页面都是同一个颜色——
   * 颜色由字符串的字符码和取模决定，是<b>纯函数</b>，不依赖加载顺序，
   * 因此「妈妈」在家庭成员页是蓝色、在关联管理页也是蓝色。</p>
   *
   * <p>颜色只能来自 antd 预设名（本组件内部集中收口），
   * 页面里不允许自己写动态色值。</p>
   */
  export default defineComponent({
    name: 'StudyEnumTag',
    props: {
      /** 标签类别（决定用哪套色板） */
      kind: { type: String, default: DEFAULT_PALETTE },
      /** 枚举文本（中文标签） */
      value: { type: String, default: '' },
    },
    setup(props) {
      /** 同一文本永远得到同一个颜色（同名同色） */
      const tagColor = computed(() => {
        const palette = PALETTES[props.kind] || PALETTES[DEFAULT_PALETTE];
        const text = String(props.value || '');
        let sum = 0;
        for (let i = 0; i < text.length; i++) {
          sum += text.charCodeAt(i);
        }
        return palette[sum % palette.length];
      });

      return { tagColor };
    },
  });
</script>

<style lang="less">
  .study-enum-tag {
    margin-right: 0;
  }
</style>
