<template>
  <ManageContentPage
    title="语法点维护"
    mode="list"
    :columns="columns"
    searchable
    :loader="loader"
    :detail-builder="detailBuilder"
    detail-title="语法点详情"
    note="语法点是全局共享内容，所有孩子的语法练习共用同一份。这一页不展示任何孩子的作答情况（R-04）。"
  />
</template>

<script lang="ts">
  import { defineComponent } from 'vue';
  import ManageContentPage, { type StudyManageColumn } from '../ManageContentPage.vue';
  import { fetchGrammarPointList } from '../manageApi';

  /**
   * 管理端「语法点维护」页（页面清单 #8，`/study/manage/grammar`）。
   *
   * <p>语法点是<b>全局共享内容</b>，所有孩子的语法练习共用同一份。
   * 列表不展示任何孩子的作答情况（R-04）。</p>
   */
  export default defineComponent({
    name: 'StudyManageGrammarPage',
    components: { ManageContentPage },
    setup() {
      const columns: StudyManageColumn[] = [
        { key: 'title', title: '语法点', width: '260px' },
        { key: 'examType', title: '考试类型', width: '140px' },
        { key: 'grammarCode', title: '业务 id', width: '180px' },
        { key: 'knowledgePointCode', title: '所属考点', width: '180px' },
      ];

      const loader = async (keyword?: string) => {
        const result = await fetchGrammarPointList();
        const rows = result?.items || [];
        const kw = String(keyword || '').trim();
        if (!kw) {
          return rows;
        }
        return rows.filter((item: any) => JSON.stringify(item).includes(kw));
      };

      const detailBuilder = (record: any) => [
        { label: '语法点', value: record?.title || '—' },
        { label: '业务 id', value: record?.grammarCode || '—' },
        { label: '考试类型', value: record?.examType || '—' },
        { label: '所属考点', value: record?.knowledgePointCode || '—' },
        { label: '规则', value: record?.rule || '—' },
        { label: '例句', value: record?.example || '—' },
        { label: '反例', value: record?.counterExample || '—' },
      ];

      return { columns, loader, detailBuilder };
    },
  });
</script>
