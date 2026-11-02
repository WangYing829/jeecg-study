<template>
  <ManageContentPage
    title="题目维护"
    mode="list"
    :columns="columns"
    searchable
    :loader="loader"
    :detail-builder="detailBuilder"
    note="这里展示的是全局题库的内容分布（各考点挂了多少题/词/母题/语法点/拆解）。错题池是按孩子隔离的行为数据，管理端不展示（R-04）。"
  />
</template>

<script lang="ts">
  import { defineComponent } from 'vue';
  import ManageContentPage, { type StudyManageColumn } from '../ManageContentPage.vue';
  import { fetchKnowledgeTree, searchKnowledgePoints, type StudyKnowledgePointVO } from '../manageApi';

  /**
   * 管理端「题目维护」页（页面清单 #5，`/study/manage/question`）。
   *
   * <p><b>为什么不是错题池</b>：错题池（{@code study_error_pool}）是<b>按孩子隔离的行为表</b>，
   * {@code GET /error-pool} 必须带 childCode，返回的是「这个孩子错了哪些题」。
   * 管理端把它列出来等于在内容维护台里暴露单个孩子的行为数据（R-04），
   * 因此这一页改读<b>全局内容</b>：坐标树上每个考点挂了哪些内容、各多少条。</p>
   *
   * <p>搜索框走服务端检索（{@code /knowledge-points/search}），
   * 空关键字时返回整棵树的全量考点。</p>
   */
  export default defineComponent({
    name: 'StudyManageQuestionPage',
    components: { ManageContentPage },
    setup() {
      const columns: StudyManageColumn[] = [
        { key: 'name', title: '考点', width: '260px' },
        { key: 'code', title: '考点编码', width: '180px' },
        { key: 'subjectCode', title: '学科', width: '100px' },
        { key: 'question', title: '题量', width: '90px' },
        { key: 'word', title: '词量', width: '90px' },
        { key: 'mother', title: '母题量', width: '100px' },
        { key: 'grammar', title: '语法点', width: '100px' },
        { key: 'writing', title: '拆解条数', width: '110px' },
      ];

      /** 把树摊平，并把内容条数统计提到顶层（表格按 record 字段取值） */
      const flatten = (nodes: StudyKnowledgePointVO[]) => {
        const out: any[] = [];
        const walk = (list: StudyKnowledgePointVO[]) => {
          (list || []).forEach((node) => {
            out.push({
              code: node.code,
              name: node.name,
              subjectCode: node.subjectCode,
              path: node.path,
              question: node.childCount?.question ?? 0,
              word: node.childCount?.word ?? 0,
              mother: node.childCount?.mother ?? 0,
              grammar: node.childCount?.grammar ?? 0,
              writing: node.childCount?.writing ?? 0,
            });
            if (node.children && node.children.length > 0) {
              walk(node.children);
            }
          });
        };
        walk(nodes);
        return out;
      };

      const loader = async (keyword?: string) => {
        const kw = String(keyword || '').trim();
        if (kw) {
          const matched = await searchKnowledgePoints(kw);
          return flatten(matched || []);
        }
        const tree = await fetchKnowledgeTree();
        return flatten(tree?.nodes || []);
      };

      const detailBuilder = (record: any) => [
        { label: '考点', value: record?.name || '—' },
        { label: '考点编码', value: record?.code || '—' },
        { label: '学科', value: record?.subjectCode || '—' },
        { label: '路径', value: record?.path || '—' },
        { label: '题量', value: record?.question ?? 0 },
        { label: '词量', value: record?.word ?? 0 },
        { label: '母题量', value: record?.mother ?? 0 },
        { label: '语法点数', value: record?.grammar ?? 0 },
        { label: '拆解条数', value: record?.writing ?? 0 },
      ];

      return { columns, loader, detailBuilder };
    },
  });
</script>
