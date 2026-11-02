<template>
  <ManageContentPage
    title="拆解库维护"
    mode="list"
    :columns="columns"
    searchable
    :loader="loader"
    :detail-builder="detailBuilder"
    detail-title="拆解详情"
    note="拆解库是语文写作的句子拆解内容源，全局共享。列表只展示内容本身，不展示任何孩子的作文或批改情况（R-04）。"
  />
</template>

<script lang="ts">
  import { defineComponent } from 'vue';
  import ManageContentPage, { type StudyManageColumn } from '../ManageContentPage.vue';
  import { fetchDissectionList } from '../manageApi';

  /**
   * 管理端「拆解库维护」页（页面清单 #9，`/study/manage/writing`）。
   *
   * <p>拆解库是语文写作的句子拆解内容源，<b>全局共享</b>。
   * 列表只展示内容本身，不展示任何孩子的作文或批改情况（R-04）。</p>
   */
  export default defineComponent({
    name: 'StudyManageWritingPage',
    components: { ManageContentPage },
    setup() {
      const columns: StudyManageColumn[] = [
        { key: 'title', title: '标题', width: '260px' },
        { key: 'genre', title: '体裁', width: '140px' },
        { key: 'stageCode', title: '学段', width: '120px' },
        { key: 'dissectionCode', title: '业务 id', width: '200px' },
      ];

      const loader = async (keyword?: string) => {
        const result = await fetchDissectionList();
        const rows = result?.items || [];
        const kw = String(keyword || '').trim();
        if (!kw) {
          return rows;
        }
        return rows.filter((item: any) => JSON.stringify(item).includes(kw));
      };

      /** 拆解是分层的，把层级逐条摊平给弹窗 */
      const detailBuilder = (record: any) => {
        const items: { label: string; value: any }[] = [
          { label: '标题', value: record?.title || '—' },
          { label: '业务 id', value: record?.dissectionCode || '—' },
          { label: '体裁', value: record?.genre || '—' },
          { label: '学段', value: record?.stageCode || '—' },
        ];
        (record?.layers || []).forEach((layer: any, index: number) => {
          items.push({
            label: `第 ${index + 1} 层 · ${layer?.layerName || layer?.layer || '—'}`,
            value: [layer?.title, layer?.content, layer?.teachingPoint].filter(Boolean).join('\n') || '—',
          });
        });
        return items;
      };

      return { columns, loader, detailBuilder };
    },
  });
</script>
