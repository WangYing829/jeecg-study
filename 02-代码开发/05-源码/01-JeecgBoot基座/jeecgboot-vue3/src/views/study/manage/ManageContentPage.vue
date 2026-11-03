<template>
  <PageWrapper class="study-manage">
    <a-card :title="title" :bordered="true">
      <template #extra>
        <!-- 页面自定义控件（如词表页的线型切换）：由薄配置页通过具名插槽提供 -->
        <slot name="extra" />
        <a-input
          v-if="searchable"
          v-model:value="keyword"
          class="study-manage__search"
          placeholder="搜索"
          allow-clear
          @press-enter="reload"
        />
        <a-button type="primary" size="small" :loading="loading" @click="reload">刷新</a-button>
      </template>

      <!-- 错误态：可读提示 + 重试入口（F-BASE-08：断网时提示明确、无静默失败） -->
      <a-alert v-if="errorText" class="study-manage__error" type="warning" show-icon :message="errorText">
        <template #action>
          <a-button size="small" @click="reload">重试</a-button>
        </template>
      </a-alert>

      <!-- 口径提示：如「词表接口不下发拼写」这类必须让使用者知道的边界 -->
      <a-alert v-if="note" class="study-manage__note" type="info" show-icon :message="note" />

      <a-spin :spinning="loading">
        <!-- 树形模式：坐标树浏览器 -->
        <a-tree
          v-if="mode === 'tree'"
          class="study-manage__tree"
          :tree-data="treeData"
          :field-names="{ title: 'name', key: 'code', children: 'children' }"
          default-expand-all
          @select="onTreeSelect"
        />

        <!-- 表单模式：上下文配置 / 考点维护（管理端表单用基座默认 horizontal，§8.4①） -->
        <a-form v-else-if="mode === 'form'" class="study-manage__form">
          <a-row :gutter="16">
            <a-col v-for="field in fields" :key="field.key" :span="field.span || 12">
              <a-form-item :label="field.label">
                <a-select
                  v-if="field.type === 'select'"
                  v-model:value="form[field.key]"
                  :options="field.options || []"
                  allow-clear
                />
                <a-input-number
                  v-else-if="field.type === 'number'"
                  v-model:value="form[field.key]"
                  :min="field.min"
                  :max="field.max"
                  style="width: 100%"
                />
                <a-date-picker
                  v-else-if="field.type === 'date'"
                  v-model:value="form[field.key]"
                  value-format="YYYY-MM-DD"
                  style="width: 100%"
                />
                <a-textarea
                  v-else-if="field.type === 'textarea'"
                  v-model:value="form[field.key]"
                  :rows="field.rows || 4"
                  :maxlength="field.maxLength"
                />
                <a-input
                  v-else
                  v-model:value="form[field.key]"
                  :maxlength="field.maxLength"
                  :placeholder="field.placeholder || ''"
                />
              </a-form-item>
            </a-col>
          </a-row>

          <a-button type="primary" :loading="submitting" @click="onSubmit">保存</a-button>
        </a-form>

        <!-- 列表模式：题目 / 母题 / 词表 / 语法点 / 拆解库
             管理端列表必须走 BasicTable（§9.1：禁止手写 a-table） -->
        <BasicTable
          v-else
          class="study-manage__table"
          :columns="tableColumns"
          :data-source="rows"
          :pagination="pagination"
          :row-key="rowKey"
          :action-column="actionColumn"
          :show-action-column="hasActionColumn"
          :show-index-column="false"
          :show-table-setting="false"
          :loading="loading"
          :scroll="{ x: 720 }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'action'">
              <StudyActionColumn :actions="detailActions(record)" />
            </template>
          </template>
        </BasicTable>

        <a-empty v-if="mode === 'list' && !loading && rows.length === 0" class="study-manage__empty" description="暂时没有内容" />
      </a-spin>
    </a-card>

    <!-- 详情弹窗 -->
    <a-modal
      v-model:open="detailOpen"
      :title="detailTitle"
      :footer="null"
      :body-style="STUDY_MODAL_BODY_STYLE"
    >
      <a-descriptions :column="1" bordered size="small">
        <a-descriptions-item v-for="item in detailItems" :key="item.label" :label="item.label">
          {{ item.value }}
        </a-descriptions-item>
      </a-descriptions>
    </a-modal>
  </PageWrapper>
</template>

<script lang="ts">
  import { computed, defineComponent, onMounted, ref, watch } from 'vue';
  import { message } from 'ant-design-vue';
  import { PageWrapper } from '/@/components/Page';
  import { BasicTable } from '/@/components/Table';
  import StudyActionColumn, { type StudyActionColumnAction } from '/@/components/study/StudyActionColumn.vue';
  import { describeNetError, OFFLINE_HINT, RETRY_LABEL } from '../net';

  /** 弹窗正文样式（设计规范 §8.5） */
  const STUDY_MODAL_BODY_STYLE = { paddingTop: '12px' };

  /** 字段描述 */
  export interface StudyManageField {
    /** 字段名 */
    key: string;
    /** 表单标签 */
    label: string;
    /** 控件类型 */
    type?: 'text' | 'textarea' | 'number' | 'select' | 'date';
    /** 栅格宽度 */
    span?: number;
    /** 下拉选项 */
    options?: { label: string; value: any }[];
    /** 数值范围 */
    min?: number;
    /** 最大值 */
    max?: number;
    /** 最大长度 */
    maxLength?: number;
    /** 占位文案 */
    placeholder?: string;
    /** 文本域行数 */
    rows?: number;
  }

  /** 表格列描述 */
  export interface StudyManageColumn {
    key: string;
    title: string;
    width?: string;
    /** antd 取值字段；不给时由内核用 key 兜底 */
    dataIndex?: string;
  }

  /**
   * 管理端内容维护页的**通用内核**。
   *
   * <p>《前端页面清单》里的 9 个管理端内容维护页（上下文配置 / 坐标树 / 考点维护 /
   * 考点版本历史 / 题目维护 / 母题维护 / 词表维护 / 语法点维护 / 拆解库维护）
   * 结构高度同构：都是「取一份内容 → 列表或表单展示 → 管理员维护」，
   * 因此抽成一个内核，各页只提供<b>配置 + 数据源</b>，
   * 避免 9 份重复代码各自漂移（写页面时统一照《产品设计规范》）。</p>
   *
   * <p><b>红线口径</b>：</p>
   * <ul>
   *   <li>管理端维护的是<b>全局共享内容</b>，页面里不出现任何孩子的作答数据
   *       （内容表全局共享 / 行为表按孩子隔离）；</li>
   *   <li>表格操作列<b>只保留「查看」</b>——这一层是内容浏览与维护，
   *       不提供批量处置入口（R-04：不做公开比较，也不做批量处置）；</li>
   *   <li>失败时给<b>可读提示 + 重试入口</b>，不静默失败（F-BASE-08）。</li>
   * </ul>
   */
  export default defineComponent({
    name: 'StudyManageContentPage',
    components: { PageWrapper, BasicTable, StudyActionColumn },
    props: {
      /** 页面标题 */
      title: { type: String, required: true },
      /** 展示模式：tree 树 / form 表单 / list 列表 */
      mode: { type: String as () => 'tree' | 'form' | 'list', default: 'list' },
      /** 表格列 */
      columns: { type: Array as () => StudyManageColumn[], default: () => [] },
      /** 表单字段 */
      fields: { type: Array as () => StudyManageField[], default: () => [] },
      /** 是否有搜索框 */
      searchable: { type: Boolean, default: false },
      /** 行主键字段名 */
      rowKey: { type: String, default: 'code' },
      /** 数据源：返回列表（list/tree）或表单初值（form）；接收当前搜索关键字 */
      loader: { type: Function as () => (keyword?: string) => Promise<any>, required: true },
      /** 外部触发重载的信号（如切换线别 / 切换孩子）；值变化即重新加载 */
      refreshKey: { type: [String, Number], default: '' },
      /** 提交表单（form 模式） */
      submitter: { type: Function as () => ((form: any) => Promise<void>) | undefined, default: undefined },
      /** 树节点选中回调（tree 模式） */
      onSelect: { type: Function as () => ((node: any) => void) | undefined, default: undefined },
      /** 详情弹窗字段装配（list 模式）；可以是 async */
      detailBuilder: {
        type: Function as () => ((record: any) => { label: string; value: any }[] | Promise<{ label: string; value: any }[]>) | undefined,
        default: undefined,
      },
      /** 详情弹窗标题 */
      detailTitle: { type: String, default: '详情' },
      /** 口径提示（页面级说明，渲染在标题下方） */
      note: { type: String, default: '' },
    },
    setup(props) {
      const keyword = ref('');
      const loading = ref(false);
      const submitting = ref(false);
      const errorText = ref('');
      const treeData = ref<any[]>([]);
      const rows = ref<any[]>([]);
      const form = ref<Record<string, any>>({});
      const detailOpen = ref(false);
      const detailItems = ref<{ label: string; value: any }[]>([]);
      const pagination = { pageSize: 20, showSizeChanger: true };

      /**
       * 实际渲染的表格列。
       *
       * <p>薄配置页只描述「内容列」，<b>操作列由内核统一补齐</b>——
       * 否则每页都要手写一遍 `key: 'action'` 的列定义，漏写一处
       * 「查看」按钮就整列消失（内容还在，但点不开详情）。</p>
       *
       * <p><b>为什么必须补 dataIndex</b>：antd 的表格是按 `record[dataIndex]`
       * 取值渲染的，底座 `useColumns` 只处理 `key`→`dataIndex` 的**显示名**，
       * 并不会把 `key` 当成取值字段。只写 `key` 的话每一格都是
       * `record[undefined]` ⇒ <b>整列渲染为空</b>（表头在、数据一片空白）。</p>
       */
      const tableColumns = computed(() => {
        const list = props.columns || [];
        return list.map((item) => ({ ...item, dataIndex: item.dataIndex || item.key }));
      });

      /**
       * 是否渲染操作列（§8.7）。
       *
       * <p>没有详情弹窗的**只读页**（如考点版本历史）不要留一列空操作——
       * 表头写着「操作」而格子里什么都没有，比没有这一列更让人以为功能坏了。</p>
       */
      const hasActionColumn = computed(() => Boolean(props.detailBuilder));

      /**
       * 操作列定义（§8.7）。
       *
       * <p>操作列的<b>渲染</b>统一走 {@link StudyActionColumn}，
       * 这里只声明列本身（标题 / 宽度 / 键）。</p>
       */
      const actionColumn = computed(() => ({
        key: 'action',
        title: '操作',
        width: '100px',
        fixed: false,
      }));

      /** 行内操作项：内容维护只保留「查看」（R-04：不提供批量处置入口） */
      const detailActions = (record: any): StudyActionColumnAction[] => {
        if (!props.detailBuilder) {
          return [];
        }
        return [
          {
            key: 'view',
            label: '查看',
            onClick: () => onOpenDetail(record),
          },
        ];
      };

      /**
       * 加载数据：按模式分派。
       *
       * <p><b>为什么要用序号丢弃过期响应</b>：页面挂载时内核先 `onMounted(reload)`，
       * 薄配置页自己的 `onMounted`（取孩子下拉、取教材版本…）在**之后**才跑，
       * 于是「依赖尚未就绪的入参」的第一次请求会先发出、后失败，
       * 而第二次请求已经成功了 —— 过期的那次 `catch` 若照样写 errorText，
       * 就会出现<em>表格有数据、下面却挂着一行错误提示</em>的怪相
       * （浏览器实测在「母题维护」页抓到过）。
       * 序号保证<strong>只有最新一次请求能写状态</strong>。</p>
       */
      let reloadSeq = 0;
      const reload = async () => {
        const seq = ++reloadSeq;
        loading.value = true;
        try {
          const data = await props.loader(keyword.value);
          if (seq !== reloadSeq) {
            return;
          }
          if (props.mode === 'tree') {
            treeData.value = Array.isArray(data) ? data : data?.items || [];
            rows.value = [];
          } else if (props.mode === 'form') {
            const source = Array.isArray(data) ? data[0] : data;
            form.value = { ...(source || {}) };
            rows.value = [];
          } else {
            const list = Array.isArray(data) ? data : data?.items || [];
            const kw = keyword.value.trim();
            rows.value = kw
              ? list.filter((item: any) => JSON.stringify(item).includes(kw))
              : list;
          }
          errorText.value = '';
        } catch (e) {
          if (seq !== reloadSeq) {
            return;
          }
          treeData.value = [];
          rows.value = [];
          const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
          errorText.value = state.offline ? state.message : String(e?.message || '加载失败');
        } finally {
          if (seq === reloadSeq) {
            loading.value = false;
          }
        }
      };

      /** 提交表单 */
      const onSubmit = async () => {
        const submit = props.submitter;
        if (!submit) {
          return;
        }
        submitting.value = true;
        try {
          await submit(form.value);
          message.success('已保存');
          await reload();
        } catch (e) {
          message.error(String(e?.message || '保存失败'));
        } finally {
          submitting.value = false;
        }
      };

      /** 树节点选中 */
      const onTreeSelect = (node: any) => {
        props.onSelect?.(node);
      };

      /**
       * 打开详情弹窗。
       *
       * <p>装配函数<b>可以是 async</b>：详情要再调一次接口时（如母题的五段结构），
       * 先装配完再开窗，不让用户对着空弹窗等。</p>
       */
      const onOpenDetail = async (record: any) => {
        const builder = props.detailBuilder;
        try {
          detailItems.value = builder ? await builder(record) : [{ label: '内容', value: JSON.stringify(record) }];
        } catch (e) {
          detailItems.value = [{ label: '加载失败', value: String(e?.message || '详情加载失败') }];
        }
        detailOpen.value = true;
      };

      watch(keyword, () => {
        if (props.mode === 'list') {
          reload();
        }
      });

      // 外部信号变化（切线别 / 切孩子）时重载：数据源变了，表格必须跟着变
      watch(
        () => props.refreshKey,
        () => {
          reload();
        },
      );

      onMounted(reload);

      return {
        keyword,
        loading,
        submitting,
        errorText,
        treeData,
        rows,
        form,
        detailOpen,
        detailItems,
        pagination,
        tableColumns,
        hasActionColumn,
        actionColumn,
        detailActions,
        STUDY_MODAL_BODY_STYLE,
        reload,
        onSubmit,
        onTreeSelect,
        onOpenDetail,
      };
    },
  });
</script>

<style lang="less">
  .study-manage {
    &__search {
      width: 200px;
      margin-right: 8px;
    }

    &__error {
      margin-bottom: 16px;
    }

    &__note {
      margin-bottom: 16px;
    }

    /* 页面自定义控件（选孩子 / 切线别） */
    &__picker {
      width: 200px;
      margin-right: 8px;
    }

    &__tree {
      padding: 8px 0;
    }

    &__form {
      max-width: 760px;
    }

    &__table {
      width: 100%;
    }

    &__empty {
      padding: 24px 0;
    }
  }
</style>
