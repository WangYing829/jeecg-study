<template>
  <div class="study-data-list">
    <!-- 桌面形态：仅 dual 模式渲染（≤992px 由卡片形态接管，见 TC-08-19） -->
    <table v-if="showTable" class="study-data-list__table">
      <thead>
        <tr>
          <th v-for="col in columns" :key="col.key" :style="col.width ? { width: col.width } : undefined">
            {{ col.title }}
          </th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(row, index) in dataSource" :key="rowKeyOf(row, index)">
          <td
            v-for="col in columns"
            :key="col.key"
            :class="{ 'study-data-list__cell--action': col.key === 'action' }"
          >
            <slot :name="cellSlotName(col)" :row="row" :value="row[col.key]">{{ row[col.key] }}</slot>
          </td>
        </tr>
      </tbody>
    </table>

    <!-- 卡片形态：窄屏唯一形态（学习端只用这一种，§9.7） -->
    <ul class="study-data-list__cards">
      <li v-for="(row, index) in dataSource" :key="rowKeyOf(row, index)" class="study-data-list__card">
        <div v-for="col in columns" :key="col.key" class="study-data-list__field">
          <span class="study-data-list__label">{{ col.title }}</span>
          <span class="study-data-list__value">
            <slot :name="cellSlotName(col)" :row="row" :value="row[col.key]">{{ row[col.key] }}</slot>
          </span>
        </div>
      </li>
    </ul>

    <a-empty v-if="!dataSource || dataSource.length === 0" class="study-data-list__empty" description="暂无数据" />
  </div>
</template>

<script lang="ts">
  import { defineComponent } from 'vue';

  export interface StudyDataListColumn {
    /** 字段名 */
    key: string;
    /** 表头 / 卡片标签 */
    title: string;
    /** 列宽（可选，长文本列必须给宽度或保持短文本） */
    width?: string;
  }

  /**
   * 列表双形态组件（设计规范 §9.3 的 StudyDataList）。
   *
   * <p>两种形态：</p>
   * <ul>
   *   <li>{@code dual}：桌面表格 + 窄屏卡片，≤992px 自动切换为卡片（TC-08-19）；</li>
   *   <li>{@code card}：只有卡片（学习端 §9.7「不用表格」）。</li>
   * </ul>
   *
   * <p>窄屏下<b>不做横向滚动</b>（F-16），而是转卡片列表。</p>
   *
   * <p><b>按列自定义内容</b>：每列都可以给一个具名插槽 {@code #cell-<列名>}，
   * 插槽拿到 {@code row} 与 {@code value}。家长端要显示彩色标签与操作按钮时，
   * 用它挂 {@code StudyEnumTag} 与 {@code StudyActionColumn}，页面就不必手写表格。</p>
   */
  export default defineComponent({
    name: 'StudyDataList',
    props: {
      /** 列定义 */
      columns: { type: Array as () => StudyDataListColumn[], default: () => [] },
      /** 数据行 */
      dataSource: { type: Array as () => Record<string, any>[], default: () => [] },
      /** 行主键字段名，或返回主键的函数 */
      rowKey: { type: [String, Function], default: 'id' },
      /** 形态：dual（桌面表格 + 窄屏卡片） / card（只有卡片） */
      mode: { type: String, default: 'dual' },
    },
    computed: {
      showTable(): boolean {
        return this.mode === 'dual';
      },
    },
    methods: {
      /** 单元格插槽名 */
      cellSlotName(col: StudyDataListColumn): string {
        return `cell-${col.key}`;
      },
      rowKeyOf(row: Record<string, any>, index: number): string {
        if (typeof this.rowKey === 'function') {
          return String((this.rowKey as (r: Record<string, any>, i: number) => string)(row, index));
        }
        return String(row[this.rowKey as string] ?? index);
      },
    },
  });
</script>

<style lang="less">
  .study-data-list {
    width: 100%;
    min-width: 0;

    &__table {
      width: 100%;
      border-collapse: collapse;
      font-size: 14px;
      line-height: 1.5715;
      color: @text-color-base;

      th,
      td {
        padding: 12px 10px;
        border-bottom: 1px solid @border-color-light;
        text-align: left;
        vertical-align: top;
      }

      th {
        font-weight: 500;
        color: @text-color-call-out;
      }

      /* 操作列不换行（§8.7） */
      & .study-data-list__cell--action {
        white-space: nowrap;
      }
    }

    &__cards {
      margin: 0;
      padding: 0;
      list-style: none;
    }

    &__card {
      padding: 12px 16px;
      border: 1px solid @border-color-light;
      border-radius: 8px;
      background-color: @app-content-background;
    }

    &__field {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 16px;
      padding: 4px 0;
    }

    &__label {
      flex: 0 0 auto;
      font-size: 14px;
      line-height: 1.5715;
      color: @text-color-call-out;
    }

    &__value {
      flex: 1 1 auto;
      min-width: 0;
      font-size: 14px;
      line-height: 1.5715;
      color: @text-color-base;
      text-align: right;
      word-break: break-word;
    }

    &__empty {
      padding: 24px 0;
    }

    /* 窄屏：表格转卡片，不做横向滚动（TC-08-19 / F-16） */
    @media (max-width: 992px) {
      &__table {
        display: none;
      }
    }
  }
</style>
