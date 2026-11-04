<template>
  <StudyLayout module-title="错题详情">
    <div class="study-kid-error-detail">
      <!-- 断网：可读提示 +「重试」入口（F-BASE-08：V0 不做离线补交，只重试） -->
      <a-alert
        v-if="netError"
        class="study-kid-error-detail__net-error"
        type="warning"
        show-icon
        :message="netError.message"
      >
        <template #action>
          <a-button size="small" @click="onRetry">重试</a-button>
        </template>
      </a-alert>

      <a-spin :spinning="loading" class="study-kid-error-detail__spin">
        <div class="study-kid-error-detail__head">
          <Icon icon="ant-design:file-search-outlined" class="study-kid-error-detail__head-icon" />
          <h2 class="study-kid-error-detail__title">这道题</h2>
        </div>

        <p v-if="!loading && !detail" class="study-kid-error-detail__hint">没找到这道题</p>

        <template v-if="detail">
          <section class="study-kid-error-detail__section">
            <StudyDataList
              mode="card"
              :columns="detailColumns"
              :data-source="detailRows"
              row-key="key"
            />
          </section>

          <section class="study-kid-error-detail__section">
            <h2 class="study-kid-error-detail__subtitle">可以练的变式</h2>
            <p v-if="variantRows.length === 0" class="study-kid-error-detail__hint">
              这道题暂时没有变式
            </p>
            <StudyDataList
              v-else
              mode="card"
              :columns="variantColumns"
              :data-source="variantRows"
              row-key="variantCode"
            />
          </section>
        </template>
      </a-spin>
    </div>

    <template #footer>
      <a-button
        type="primary"
        class="study-kid-error-detail__primary"
        :loading="loading"
        @click="onGoReview"
      >
        去复习
      </a-button>
    </template>
  </StudyLayout>
</template>

<script lang="ts">
  import { computed, defineComponent, onMounted, ref } from 'vue';
  import { useRoute, useRouter } from 'vue-router';
  import { Icon } from '/@/components/Icon';
  import { useMessage } from '/@/hooks/web/useMessage';
  import StudyLayout from '/@/layouts/study/index.vue';
  import StudyDataList from '/@/components/study/StudyDataList.vue';
  import {
    fetchErrorDetail,
    fetchErrorVariants,
    type StudyErrorDetailVO,
    type StudyErrorVariantVO,
  } from '../../../api';
  import { describeNetError, OFFLINE_HINT, RETRY_LABEL, type StudyNetError } from '../../../net';
  import { kidCauseLabel, kidDateLabel, kidMasteryLabel, kidModuleLabel } from '../../kidCommon';

  /**
   * 孩子端错题详情页（设计规范 §10.2）。
   *
   * 红线要点：
   * 详情接口不下发答案与讲解，页面也就无从提前展示（R-01）；
   * 只给这一道题自己的卡点与状态，不出现任何他人标识（R-04）；
   * 变式清单只给题干与层次，是否算对由服务端在作答后给出（R-02）。
   *
   * ⚠ 注释里不要写 HTML 标签：红线扫描取的是尖括号之间的文本（R04-3）。
   */
  export default defineComponent({
    name: 'StudyKidErrorDetailPage',
    components: { Icon, StudyLayout, StudyDataList },
    setup() {
      const route = useRoute();
      const router = useRouter();
      const { createMessage } = useMessage();

      const detail = ref<StudyErrorDetailVO | null>(null);
      const variants = ref<StudyErrorVariantVO[]>([]);
      const loading = ref(false);
      /** 断网提示与重试入口（F-BASE-08） */
      const netError = ref<StudyNetError | null>(null);
      /** 重试动作：断网后点「重试」重放上一次失败的服务端请求 */
      const retryAction = ref<null | (() => Promise<void>)>(null);

      /** 当前错题编号（来自路由参数） */
      const errorCode = computed(() => String(route.params.code || ''));

      /** 详情字段的列 */
      const detailColumns = [
        { key: 'label', title: '项目' },
        { key: 'value', title: '内容' },
      ];

      /** 变式清单的列 */
      const variantColumns = [
        { key: 'layerName', title: '层次' },
        { key: 'stem', title: '题目' },
      ];

      /** 详情行（展示用的中文标签在页面里定） */
      const detailRows = computed<Record<string, any>[]>(() => {
        if (!detail.value) {
          return [];
        }
        const item = detail.value;
        const fields = [
          { label: '来自', value: kidModuleLabel(item.sourceModule) },
          { label: '题型', value: item.contentType },
          { label: '题目编号', value: item.contentId },
          { label: '考点', value: item.knowledgePointName || item.knowledgePointCode },
          { label: '卡在哪', value: kidCauseLabel(item.errorCauseName || item.errorCause) },
          { label: '掌握度', value: kidMasteryLabel(item.masteryState) },
          { label: '下次复习', value: kidDateLabel(item.nextReviewAt) },
        ];
        return fields
          .filter((field) => Boolean(field.value))
          .map((field, index) => ({ key: `${index}-${field.label}`, ...field }));
      });

      /** 变式行 */
      const variantRows = computed<Record<string, any>[]>(() =>
        variants.value.map((item) => ({
          variantCode: item.variantCode,
          layerName: item.layerName || `第 ${item.layer || 1} 层`,
          stem: item.stem,
        })),
      );

      const load = async () => {
        if (!errorCode.value) {
          return;
        }
        loading.value = true;
        try {
          const result = await fetchErrorDetail(errorCode.value);
          detail.value = result;
          const variantList = await fetchErrorVariants(errorCode.value);
          variants.value = variantList?.variants ?? [];
          netError.value = null;
        } catch (e) {
          // F-BASE-08：断网 → 可读提示 +「重试」入口；业务错误 → 保留原来的提示语义
          const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
          netError.value = state.offline ? state : null;
          if (state.offline) {
            retryAction.value = () => load();
          } else {
            createMessage.warning('这道题没取到，稍后再看看');
          }
        } finally {
          loading.value = false;
        }
      };

      /** 断网后点「重试」：重放上一次失败的服务端请求 */
      const onRetry = async () => {
        const action = retryAction.value;
        retryAction.value = null;
        netError.value = null;
        if (action) {
          await action();
        }
      };

      /** 底部主按钮：进入今日复习 */
      const onGoReview = () => {
        router.push('/study/kid/review');
      };

      onMounted(() => {
        load();
      });

      return {
        detail,
        detailColumns,
        detailRows,
        variantColumns,
        variantRows,
        loading,
        netError,
        onRetry,
        onGoReview,
      };
    },
  });
</script>

<style lang="less">
  .study-kid-error-detail {
    padding: 16px;

    &__spin {
      display: block;
      min-height: 240px;
    }

    &__head {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 12px;
    }

    &__head-icon {
      font-size: 20px;
      color: @primary-color;
    }

    &__title {
      margin: 0;
      font-size: 18px;
      font-weight: 600;
      line-height: 1.4;
      color: @text-color-base;
    }

    &__subtitle {
      margin: 0 0 12px;
      font-size: 16px;
      font-weight: 600;
      line-height: 1.4;
      color: @text-color-base;
    }

    &__section {
      margin-bottom: 16px;
    }

    &__hint {
      margin: 0;
      padding: 12px 16px;
      border: 1px solid @border-color-light;
      border-radius: 8px;
      background-color: @app-content-background;
      font-size: 14px;
      line-height: 1.5715;
      color: @text-color-call-out;
    }

    /* 断网提示条（F-BASE-08） */
    &__net-error {
      margin-bottom: 16px;
    }

    &__primary {
      width: 100%;
      min-height: 44px;
      font-size: 18px;
      border-radius: 8px;
    }

    @media (max-width: 992px) {
      padding: 12px;
    }
  }
</style>
