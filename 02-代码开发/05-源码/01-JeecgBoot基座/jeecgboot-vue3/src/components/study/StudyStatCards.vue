<template>
  <div class="study-stat-cards" :style="{ '--study-stat-columns': columnsPerRow }">
    <div v-for="item in items" :key="item.key" class="study-stat-card">
      <div class="study-stat-card__value">{{ item.value }}</div>
      <div class="study-stat-card__title">{{ item.title }}</div>
      <div v-if="item.extra" class="study-stat-card__extra">{{ item.extra }}</div>
    </div>
  </div>
</template>

<script lang="ts">
  import { defineComponent } from 'vue';

  export interface StudyStatItem {
    /** 指标名 */
    title: string;
    /** 指标值 */
    value: string | number;
    /** 补充说明 */
    extra?: string;
    /** 唯一键 */
    key: string;
  }

  /**
   * 统计卡（设计规范 §8.10）。
   *
   * <p>家长端用它展示客观指标（用眼时长、休息次数、错题数等）。
   * 只展示<b>当前孩子</b>的客观状态与趋势，不涉及任何其他人（红线 R-04）。</p>
   */
  export default defineComponent({
    name: 'StudyStatCards',
    props: {
      /** 指标列表 */
      items: { type: Array as () => StudyStatItem[], default: () => [] },
      /** 每行列数 */
      columnsPerRow: { type: Number, default: 4 },
    },
  });
</script>

<style lang="less">
  .study-stat-cards {
    display: grid;
    grid-template-columns: repeat(var(--study-stat-columns, 4), minmax(0, 1fr));
    gap: 16px;
    width: 100%;
    min-width: 0;

    @media (max-width: 992px) {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }

    @media (max-width: 480px) {
      grid-template-columns: minmax(0, 1fr);
    }
  }

  .study-stat-card {
    min-width: 0;
    padding: 10px 12px;
    border: 1px solid @border-color-light;
    border-radius: 6px;
    background-color: @app-content-background;

    &__value {
      font-size: 20px;
      font-weight: 600;
      line-height: 1.2;
      color: @text-color-base;
    }

    &__title {
      margin-top: 4px;
      font-size: 12px;
      line-height: 1.4;
      color: @text-color-call-out;
    }

    &__extra {
      margin-top: 4px;
      font-size: 12px;
      line-height: 1.4;
      color: @text-color-help-dark;
    }
  }
</style>
