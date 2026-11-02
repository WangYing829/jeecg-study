<template>
  <ManageContentPage
    title="词表维护"
    mode="list"
    :columns="columns"
    searchable
    :loader="loader"
    :detail-builder="detailBuilder"
    :refresh-key="lineType"
    detail-title="词条详情"
    note="词表接口不下发拼写（拼写就是拼写题的答案，红线 R-01），所以按「释义 + 单元 + 业务 id」定位词条。记忆强度、反馈次数等按孩子的字段这里不显示（R-04）。"
  >
    <template #extra>
      <a-radio-group v-model:value="lineType" size="small">
        <a-radio-button v-for="option in lineOptions" :key="option.value" :value="option.value">
          {{ option.label }}
        </a-radio-button>
      </a-radio-group>
    </template>
  </ManageContentPage>
</template>

<script lang="ts">
  import { defineComponent, ref } from 'vue';
  import ManageContentPage, { type StudyManageColumn } from '../ManageContentPage.vue';
  import { WORD_LINE_OPTIONS, fetchWordLine, wordLineLabel } from '../manageApi';

  /**
   * 管理端「词表维护」页（页面清单 #7，`/study/manage/word`）。
   *
   * <p>词表分「课内 / 课外」两条线，页面用线型切换查看。</p>
   *
   * <p><b>线别的取值域是 {@code IN} / {@code OUT}</b>（后端 {@code WordLineType}），
   * 不是中文——把「课内」直接当 lineType 传过去会被判非法。</p>
   *
   * <p><b>红线口径</b>：词条是<b>全局共享内容</b>，
   * 不展示任何孩子的背诵进度（R-04）；拼写由接口按 R-01 口径不下发。</p>
   */
  export default defineComponent({
    name: 'StudyManageWordPage',
    components: { ManageContentPage },
    setup() {
      const lineType = ref('IN');
      const lineOptions = WORD_LINE_OPTIONS;

      const columns: StudyManageColumn[] = [
        { key: 'wordCode', title: '业务 id', width: '160px' },
        { key: 'meaning', title: '释义', width: '260px' },
        { key: 'phonetic', title: '音标', width: '160px' },
        { key: 'pos', title: '词性', width: '100px' },
        { key: 'unitCode', title: '单元', width: '120px' },
      ];

      const loader = async (keyword?: string) => {
        const result = await fetchWordLine(lineType.value);
        const rows = result?.items || [];
        const kw = String(keyword || '').trim();
        if (!kw) {
          return rows;
        }
        return rows.filter((item: any) => JSON.stringify(item).includes(kw));
      };

      const detailBuilder = (record: any) => [
        { label: '业务 id', value: record?.wordCode || '—' },
        { label: '释义', value: record?.meaning || '—' },
        { label: '音标', value: record?.phonetic || '—' },
        { label: '词性', value: record?.pos || '—' },
        { label: '单元', value: record?.unitCode || '—' },
        { label: '线别', value: wordLineLabel(record?.lineType) },
        { label: '例句（目标词已挖空）', value: record?.contextSentence || '—' },
      ];

      return { lineType, lineOptions, columns, loader, detailBuilder };
    },
  });
</script>
