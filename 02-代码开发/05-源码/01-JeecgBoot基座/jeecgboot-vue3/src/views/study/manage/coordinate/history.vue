<template>
  <ManageContentPage
    title="考点版本历史"
    mode="list"
    :columns="columns"
    row-key="version"
    :loader="loader"
    note="只读页：展示考点的历史版本，不提供修改入口。这一页只展示内容元信息，不涉及任何孩子的作答数据（R-04）。"
  />
</template>

<script lang="ts">
  import { defineComponent } from 'vue';
  import { useRoute } from 'vue-router';
  import ManageContentPage, { type StudyManageColumn } from '../ManageContentPage.vue';
  import { fetchKnowledgeHistory } from '../manageApi';

  /**
   * 管理端「考点版本历史」页（页面清单 #4，`/study/manage/coordinate/:code/history`）。
   *
   * <p>只读页：展示考点的历史版本，不提供修改入口。
   * 这一页<b>只展示内容元信息</b>，不涉及任何孩子的作答数据（R-04）。</p>
   */
  export default defineComponent({
    name: 'StudyManageCoordinateHistoryPage',
    components: { ManageContentPage },
    setup() {
      const route = useRoute();

      const columns: StudyManageColumn[] = [
        { key: 'version', title: '版本', width: '120px' },
        { key: 'name', title: '考点名称', width: '240px' },
        { key: 'questionType', title: '题型', width: '140px' },
        { key: 'scoreWeight', title: '分值权重', width: '120px' },
        { key: 'effectiveFrom', title: '生效时间', width: '200px' },
        { key: 'invalidAt', title: '失效时间', width: '200px' },
      ];

      const loader = async () => {
        const list = await fetchKnowledgeHistory(String(route.params.code || ''));
        // 考点 VO 里带 children，antd 会以为这行可以展开 ⇒ 凭空多出一个「展开行」按钮。
        // 历史版本是**平铺的版本列表**，没有子节点可言，这里把 children 摘掉。
        return (list || []).map((item: any) => ({ ...item, children: undefined }));
      };

      return { columns, loader };
    },
  });
</script>
