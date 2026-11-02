<template>
  <ManageContentPage
    title="母题维护"
    mode="list"
    :columns="columns"
    searchable
    :loader="loader"
    :detail-builder="detailBuilder"
    :refresh-key="childId"
    detail-title="母题详情"
    note="只展示母题内容本身。作答次数、错误次数、掌握度是按孩子统计的行为数据，这一页一律不显示（R-04）。"
  >
    <template #extra>
      <a-select
        v-model:value="childId"
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
  import ManageContentPage, { type StudyManageColumn } from '../ManageContentPage.vue';
  import { describeNetError, OFFLINE_HINT, RETRY_LABEL } from '../../net';
  import { fetchManageChildren, fetchMotherDetail, fetchMotherList } from '../manageApi';

  /**
   * 管理端「母题维护」页（页面清单 #6，`/study/manage/mother`）。
   *
   * <p>母题是数学闯关的内容源，<b>全局共享</b>。
   * 列表只展示内容元信息，不展示任何孩子的通关进度（R-04：不做公开比较）。</p>
   *
   * <p><b>为什么要有「选孩子」</b>：{@code GET /math/mother/list} 的行为列是按孩子算的，
   * 服务端要求带上孩子身份才肯返回列表。页面<b>只取内容列</b>，
   * 孩子选择只影响服务端能不能返回列表，不影响列表里显示的内容。</p>
   */
  export default defineComponent({
    name: 'StudyManageMotherPage',
    components: { ManageContentPage },
    setup() {
      const childId = ref('');
      const children = ref<{ label: string; value: string }[]>([]);

      const childOptions = computed(() => children.value);

      const columns: StudyManageColumn[] = [
        { key: 'title', title: '母题', width: '300px' },
        { key: 'motherCode', title: '母题编码', width: '180px' },
        { key: 'subjectCode', title: '学科', width: '100px' },
        { key: 'knowledgePointCode', title: '所属考点', width: '180px' },
        { key: 'outlineWeight', title: '考纲权重', width: '110px' },
        { key: 'variantCount', title: '变式数', width: '100px' },
      ];

      const loader = async (keyword?: string) => {
        const list = await fetchMotherList(childId.value || undefined);
        const rows = list || [];
        const kw = String(keyword || '').trim();
        if (!kw) {
          return rows;
        }
        return rows.filter((item) => JSON.stringify(item).includes(kw));
      };

      /** 详情要再调一次接口取五段结构（条件类型 / 关键关系 / 步骤 / 易错点） */
      const detailBuilder = async (record: any) => {
        const detail = await fetchMotherDetail(record?.motherCode);
        return [
          { label: '母题', value: detail?.title || '—' },
          { label: '母题编码', value: detail?.motherCode || '—' },
          { label: '学科', value: detail?.subjectCode || '—' },
          { label: '所属考点', value: detail?.knowledgePointCode || '—' },
          {
            label: '条件类型',
            value: (detail?.conditionTypes || []).map((item) => item?.label || item?.value).filter(Boolean).join('、') || '—',
          },
          { label: '关键关系', value: detail?.keyRelation || '—' },
          {
            label: '解题步骤',
            value:
              (detail?.steps || [])
                .map((step) => `${step?.title || ''}${step?.title && step?.content ? '：' : ''}${step?.content || ''}`)
                .join('\n') || '—',
          },
          { label: '易错点', value: (detail?.pitfalls || []).join('、') || '—' },
          { label: '需通过变式数', value: detail?.passVariantCount ?? '—' },
        ];
      };

      onMounted(async () => {
        try {
          const list = await fetchManageChildren();
          children.value = (list || [])
            .filter((item) => item.childId)
            .map((item) => ({ label: item.nickname || item.childCode || '', value: item.childId as string }));
          if (children.value.length > 0) {
            childId.value = children.value[0].value;
          }
        } catch (e) {
          // 孩子列表取不到要说清楚，不静默失败（F-BASE-08）
          const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
          message.error(state.offline ? state.message : String(e?.message || '加载失败'));
        }
      });

      return { childId, childOptions, columns, loader, detailBuilder };
    },
  });
</script>
