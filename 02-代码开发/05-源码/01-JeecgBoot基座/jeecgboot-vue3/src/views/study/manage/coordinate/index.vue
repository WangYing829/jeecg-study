<template>
  <ManageContentPage
    title="坐标树浏览器"
    mode="tree"
    :loader="loader"
    :on-select="onSelect"
    note="坐标树是全局共享的内容：所有孩子的练习都从这一棵树取题。点击节点进入该考点的维护页。"
  />
</template>

<script lang="ts">
  import { defineComponent } from 'vue';
  import { useRouter } from 'vue-router';
  import ManageContentPage from '../ManageContentPage.vue';
  import { fetchKnowledgeTree } from '../manageApi';

  /**
   * 管理端「坐标树浏览器」页（页面清单 #2，`/study/manage/coordinate`）。
   *
   * <p>展示内容的坐标树（考点层级）。点击节点跳到该节点的考点维护页。</p>
   */
  export default defineComponent({
    name: 'StudyManageCoordinatePage',
    components: { ManageContentPage },
    setup() {
      const router = useRouter();

      /** 后端返回 { version, updatedAt, nodes }，树数据取 nodes */
      const loader = async () => {
        const tree = await fetchKnowledgeTree();
        return tree?.nodes || [];
      };

      /** 选中节点 → 进入考点维护 */
      const onSelect = (node: any) => {
        const code = node?.code;
        if (code) {
          router.push(`/study/manage/coordinate/${code}`);
        }
      };

      return { loader, onSelect };
    },
  });
</script>
