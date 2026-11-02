<template>
  <ManageContentPage
    :title="title"
    mode="form"
    :fields="fields"
    :loader="loader"
    :submitter="submitter"
    note="可维护的是题型的分值权重、关联错因与母题绑定。考点名称与所属学科属于坐标树本身的结构，不在这一页改。"
  />
</template>

<script lang="ts">
  import { computed, defineComponent } from 'vue';
  import { useRoute } from 'vue-router';
  import ManageContentPage, { type StudyManageField } from '../ManageContentPage.vue';
  import { fetchKnowledgePoint, updateKnowledgePoint } from '../manageApi';

  /**
   * 管理端「考点维护」页（页面清单 #3，`/study/manage/coordinate/:code`）。
   *
   * <p>可编辑字段严格对齐后端 {@code KnowledgePointUpdateRequest}：
   * {@code questionType / scoreWeight / errorTypes / motherQuestionCode}。</p>
   *
   * <p><b>红线口径</b>：考点是<b>全局共享内容</b>，
   * 所有孩子的练习都从同一份坐标树取题（内容表全局共享）。</p>
   */
  export default defineComponent({
    name: 'StudyManageCoordinateDetailPage',
    components: { ManageContentPage },
    setup() {
      const route = useRoute();

      const fields: StudyManageField[] = [
        { key: 'questionType', label: '题型', type: 'text', span: 12, maxLength: 32 },
        { key: 'scoreWeight', label: '分值权重', type: 'number', span: 12, min: 0, max: 100 },
        { key: 'motherQuestionCode', label: '绑定母题', type: 'text', span: 24, maxLength: 64 },
        {
          key: 'errorTypes',
          label: '关联错因（每行一个）',
          type: 'textarea',
          span: 24,
          rows: 4,
          maxLength: 500,
        },
      ];

      const code = () => String(route.params.code || '');

      const title = computed(() => `考点维护 · ${code()}`);

      /** 取考点详情；错因是数组，摊平成多行文本交给文本域 */
      const loader = async () => {
        const vo = await fetchKnowledgePoint(code());
        return {
          ...(vo || {}),
          errorTypes: (vo?.errorTypes || []).join('\n'),
        };
      };

      const submitter = async (form: Record<string, any>) => {
        const errorTypes = String(form.errorTypes || '')
          .split(/[\n,，、]/)
          .map((item) => item.trim())
          .filter(Boolean);
        await updateKnowledgePoint(code(), {
          questionType: form.questionType || undefined,
          scoreWeight: form.scoreWeight,
          errorTypes,
          motherQuestionCode: form.motherQuestionCode || undefined,
        });
      };

      return { title, fields, loader, submitter };
    },
  });
</script>
