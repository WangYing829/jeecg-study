<template>
  <ManageContentPage
    :title="title"
    mode="form"
    :fields="fields"
    :loader="loader"
    :submitter="submitter"
    :refresh-key="childCode"
  >
    <template #extra>
      <a-select
        v-model:value="childCode"
        class="study-manage__picker"
        :options="childOptions"
        placeholder="选择孩子"
        show-search
        option-filter-prop="label"
      />
    </template>
  </ManageContentPage>
</template>

<script lang="ts">
  import { computed, defineComponent, onMounted, ref } from 'vue';
  import { message } from 'ant-design-vue';
  import ManageContentPage, { type StudyManageField } from '../ManageContentPage.vue';
  import { describeNetError, OFFLINE_HINT, RETRY_LABEL } from '../../net';
  import {
    fetchContexts,
    fetchManageChildren,
    fetchTextbookVersions,
    saveContext,
    type StudyContextVO,
    type StudyTextbookVersionVO,
  } from '../manageApi';

  /**
   * 管理端「上下文配置」页（页面清单 #1，`/study/manage/context`）。
   *
   * <p><b>上下文是按孩子维度存的</b>（后端 {@code study_context} 带 {@code child_id}，
   * {@code ContextVO.childId}），所以这一页必须先选孩子：
   * 不选时服务端按当前身份可见的第一个孩子兜底。</p>
   *
   * <p><b>红线口径</b>：这一页配的是**内容侧的护眼与教材参数**，
   * 不展示任何孩子的作答数据（R-04）。</p>
   */
  export default defineComponent({
    name: 'StudyManageContextPage',
    components: { ManageContentPage },
    setup() {
      const childCode = ref('');
      const children = ref<{ label: string; value: string }[]>([]);
      const versions = ref<StudyTextbookVersionVO[]>([]);

      const title = computed(() => (childCode.value ? '上下文配置' : '上下文配置（未选孩子）'));

      const childOptions = computed(() => children.value);

      /** 教材版本下拉：把 versionId 映射成「版本名（编码）」，管理员认名字不认 id */
      const versionOptions = computed(() =>
        versions.value.map((item) => ({
          label: item.versionName ? `${item.versionName}（${item.versionCode || ''}）` : item.versionId || '',
          value: item.versionId || '',
        })),
      );

      const fields = computed<StudyManageField[]>(() => [
        { key: 'textbookVersionId', label: '教材版本', type: 'select', span: 12, options: versionOptions.value },
        { key: 'gradeCode', label: '年级编码', type: 'text', span: 12, maxLength: 16 },
        { key: 'stageCode', label: '学段编码', type: 'text', span: 12, maxLength: 16 },
        { key: 'provinceCode', label: '省份编码', type: 'text', span: 12, maxLength: 16 },
        { key: 'eyeIntervalMin', label: '护眼提醒间隔(分钟)', type: 'number', span: 12, min: 5, max: 120 },
      ]);

      /** 取该孩子的上下文；后端返回列表，取第一条作为表单初值 */
      const loader = async (): Promise<StudyContextVO | null> => {
        const list = await fetchContexts(childCode.value || undefined);
        return (list && list[0]) || null;
      };

      const submitter = async (form: Record<string, any>) => {
        await saveContext({
          childCode: childCode.value || undefined,
          provinceCode: form.provinceCode || undefined,
          stageCode: form.stageCode || undefined,
          gradeCode: form.gradeCode || undefined,
          textbookVersionId: form.textbookVersionId || undefined,
          eyeIntervalMin: form.eyeIntervalMin,
        });
      };

      onMounted(async () => {
        try {
          // 孩子列表由服务端按关系表过滤，家长只看得到自己关联的孩子
          const list = await fetchManageChildren();
          children.value = (list || [])
            .filter((item) => item.childCode)
            .map((item) => ({ label: item.nickname || item.childCode || '', value: item.childCode as string }));
          if (children.value.length > 0) {
            childCode.value = children.value[0].value;
          }
          versions.value = await fetchTextbookVersions();
        } catch (e) {
          // 下拉数据取不到要说清楚，不静默失败（F-BASE-08）
          const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
          message.error(state.offline ? state.message : String(e?.message || '加载失败'));
        }
      });

      return { title, childCode, childOptions, fields, loader, submitter };
    },
  });
</script>
