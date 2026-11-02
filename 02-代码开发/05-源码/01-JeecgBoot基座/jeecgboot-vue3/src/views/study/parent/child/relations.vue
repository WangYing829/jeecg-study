<template>
  <StudyParentShell :child-name="childName" :child-code="childCode" :child-options="childOptions" @switch-child="onSwitch">
    <a-spin :spinning="loading">
      <!-- 断网：可读提示 +「重试」入口（F-BASE-08：断网不能伪装成「没有关联」） -->
      <a-alert
        v-if="netError"
        class="study-parent-relation__net-error"
        type="warning"
        show-icon
        :message="netError.message"
      >
        <template #action>
          <a-button size="small" @click="onRetry">重试</a-button>
        </template>
      </a-alert>

      <!-- 孩子信息条：昵称 + 头像 + 业务 id（§5.6） -->
      <section class="study-parent-relation__section">
        <div class="study-parent-relation__head">
          <a-avatar
            v-if="childAvatar"
            class="study-parent-relation__avatar"
            :size="48"
            :src="childAvatar"
            :alt="childLabel"
          />
          <a-avatar v-else class="study-parent-relation__avatar study-parent-relation__avatar--empty" :size="48">
            {{ (childLabel || '?').slice(0, 1) }}
          </a-avatar>
          <div class="study-parent-relation__head-text">
            <div class="study-parent-relation__name">{{ childLabel }}</div>
            <div class="study-parent-relation__code">{{ routeCode }}</div>
          </div>
        </div>
      </section>

      <section class="study-parent-relation__section">
        <div class="study-parent-relation__section-head">
          <h2 class="study-parent-relation__section-title">关联的家长</h2>
          <a-button type="primary" size="small" @click="openAddRelation">添加关联</a-button>
        </div>

        <!-- 关联列表：走 StudyDataList（≤992px 自动转卡片，TC-08-19 / F-16） -->
        <StudyDataList
          :columns="relationColumns"
          :data-source="relations"
          row-key="relationId"
          class="study-parent-relation__list"
        >
          <template #cell-relationLabel="{ row }">
            <StudyEnumTag kind="relation" :value="row.relationLabel || '其他'" />
          </template>
          <template #cell-isPrimary="{ row }">
            <a-tag v-if="row.isPrimary" color="warning">主家长</a-tag>
            <span v-else class="study-parent-relation__muted">—</span>
          </template>
          <template #cell-status="{ row }">
            <a-badge :status="row.status === 1 ? 'success' : 'default'" :text="row.status === 1 ? '正常' : '已停用'" />
          </template>
          <template #cell-action="{ row }">
            <span class="study-parent-relation__actions">
              <span class="study-parent-relation__muted">{{ row.isSelf ? '（我）' : '' }}</span>
              <StudyActionColumn :actions="relationActions(row)" />
            </span>
          </template>
        </StudyDataList>
      </section>
    </a-spin>

    <!-- 添加关联弹窗 -->
    <a-modal
      v-model:open="addOpen"
      title="添加关联家长"
      ok-text="添加"
      cancel-text="取消"
      :confirm-loading="submitting"
      :body-style="STUDY_MODAL_BODY_STYLE"
      @ok="onAddRelation"
    >
      <a-form :layout="vertical">
        <a-form-item label="家长" required>
          <a-select
            v-model:value="addForm.parentUserId"
            :options="memberOptions"
            placeholder="选择本家庭里的家长"
            show-search
            option-filter-prop="label"
          />
        </a-form-item>
        <a-form-item label="关系">
          <a-select v-model:value="addForm.relationLabel" :options="relationOptions" />
        </a-form-item>
        <a-form-item label="设为主家长">
          <a-switch v-model:checked="addForm.primary" />
        </a-form-item>
      </a-form>
    </a-modal>
  </StudyParentShell>
</template>

<script lang="ts">
  import { computed, defineComponent, onMounted, ref, watch } from 'vue';
  import { useRoute } from 'vue-router';
  import { message } from 'ant-design-vue';
  import StudyParentShell from '/@/components/study/StudyParentShell.vue';
  import StudyDataList from '/@/components/study/StudyDataList.vue';
  import StudyEnumTag from '/@/components/study/StudyEnumTag.vue';
  import StudyActionColumn, { type StudyActionColumnAction } from '/@/components/study/StudyActionColumn.vue';
  import { useParentChild } from '../useParentChild';
  import { describeNetError, OFFLINE_HINT, RETRY_LABEL, type StudyNetError } from '../../net';
  import {
    RELATION_LABELS,
    addChildRelation,
    fetchChildDetail,
    fetchChildRelations,
    fetchFamilyMembers,
    removeChildRelation,
    type StudyRelationVO,
  } from '../familyApi';

  /** 弹窗正文样式（设计规范 §8.5） */
  const STUDY_MODAL_BODY_STYLE = { paddingTop: '12px' };

  /**
   * 家长端「孩子关联管理」页（设计规范 §5.6）。
   *
   * <p>一个家长可关联多个孩子，一个孩子可被多个家长关联（多对多）。
   * 解除关联后该家长<b>立即</b>失去该孩子的数据可见性（服务端强制，R-04）。</p>
   *
   * <p><b>红线口径</b>：本页只展示「哪位家长和这个孩子有关联」，
   * 不展示任何家长的学习数据——家长之间互不可见（R-04 强化）。</p>
   */
  export default defineComponent({
    name: 'StudyParentChildRelationPage',
    components: { StudyParentShell, StudyDataList, StudyEnumTag, StudyActionColumn },
    setup() {
      const route = useRoute();

      const relations = ref<StudyRelationVO[]>([]);
      const childLabel = ref('');
      const childAvatar = ref('');
      const loading = ref(false);
      const submitting = ref(false);
      /** 断网错误态（F-BASE-08） */
      const netError = ref<StudyNetError | null>(null);
      const addOpen = ref(false);
      const addForm = ref<{ parentUserId: string; relationLabel: string; primary: boolean }>({
        parentUserId: '',
        relationLabel: '妈妈',
        primary: false,
      });
      /** 本家庭成员（添加关联时只能选本家庭里的家长） */
      const familyMembers = ref<{ label: string; value: string }[]>([]);

      const { childCode, childName, childOptions, loadChildren } = useParentChild((state) => {
        netError.value = state.offline ? state : null;
      });

      /**
       * 路由上的孩子业务 id；没有 {@code :code} 时用顶部条当前选中的孩子
       * （家长端外壳自带孩子切换条，「当前孩子」是这一层的既有语义）。
       */
      const routeCode = computed(() => String(route.params.code || childCode.value || ''));

      const relationColumns = [
        { key: 'relationLabel', title: '关系', width: '120px' },
        { key: 'isPrimary', title: '主家长', width: '100px' },
        { key: 'status', title: '状态', width: '120px' },
        { key: 'action', title: '操作', width: '120px' },
      ];

      const relationOptions = RELATION_LABELS.map((item) => ({ label: item, value: item }));

      const memberOptions = computed(() => familyMembers.value);

      /**
       * 关联行的操作项（§8.7：走统一操作列，危险动作二次确认）。
       */
      const relationActions = (record: StudyRelationVO): StudyActionColumnAction[] => {
        if (record.isSelf) {
          return [];
        }
        return [
          {
            key: 'remove',
            label: '解除',
            danger: true,
            confirm: {
              title: '解除后该家长将看不到这个孩子的数据，确定吗？',
              okText: '确定',
              cancelText: '取消',
            },
            onClick: () => onRemoveRelation(record.relationId),
          },
        ];
      };

      const load = async () => {
        const code = routeCode.value;
        if (!code) {
          return;
        }
        loading.value = true;
        netError.value = null;
        try {
          const [list, detail, members] = await Promise.all([
            fetchChildRelations(code),
            fetchChildDetail(code),
            fetchFamilyMembers(),
          ]);
          relations.value = list || [];
          childLabel.value = detail?.nickname || '';
          childAvatar.value = detail?.avatar || '';
          // 候选只给「本家庭成员」，成员 id 用 relationLabel 之外的稳定标识
          familyMembers.value = (members || []).map((item: any, index: number) => ({
            label: item.relationLabel ? `${item.relationLabel}` : `家长 ${index + 1}`,
            value: String(item.memberId || ''),
          }));
        } catch (e) {
          relations.value = [];
          // 断网给可读提示 +「重试」；业务错误由消息提示透出，不静默失败（F-BASE-08）
          const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
          if (state.offline) {
            netError.value = state;
          } else {
            message.error(String(e?.message || '加载失败'));
          }
        } finally {
          loading.value = false;
        }
      };

      /** 重试 */
      const onRetry = async () => {
        netError.value = null;
        await load();
      };

      const onSwitch = (value: string) => {
        childCode.value = value;
      };

      /** 打开「添加关联」弹窗 */
      const openAddRelation = () => {
        addForm.value = { parentUserId: '', relationLabel: '妈妈', primary: false };
        addOpen.value = true;
      };

      /** 提交关联 */
      const onAddRelation = async () => {
        const code = routeCode.value;
        if (!code || !addForm.value.parentUserId) {
          message.warning('请选择家长');
          return;
        }
        submitting.value = true;
        try {
          await addChildRelation(code, {
            parentUserId: addForm.value.parentUserId,
            relationLabel: addForm.value.relationLabel,
            primary: addForm.value.primary,
          });
          message.success('已关联');
          addOpen.value = false;
          await load();
        } catch (e) {
          // 重复关联由服务端拒绝，错误信息直接透出
          message.error(String(e?.message || '关联失败'));
        } finally {
          submitting.value = false;
        }
      };

      /** 解除关联 */
      const onRemoveRelation = async (relationId?: string) => {
        const code = routeCode.value;
        if (!code || !relationId) {
          return;
        }
        try {
          await removeChildRelation(code, relationId);
          message.success('已解除');
          await load();
        } catch (e) {
          message.error(String(e?.message || '解除失败'));
        }
      };

      watch(routeCode, () => {
        load();
      });

      onMounted(async () => {
        await loadChildren();
        await load();
      });

      return {
        childCode,
        childName,
        childOptions,
        relations,
        childLabel,
        childAvatar,
        loading,
        submitting,
        netError,
        addOpen,
        addForm,
        memberOptions,
        routeCode,
        relationColumns,
        relationOptions,
        relationActions,
        onRetry,
        onSwitch,
        openAddRelation,
        onAddRelation,
        onRemoveRelation,
        STUDY_MODAL_BODY_STYLE,
      };
    },
  });
</script>

<style lang="less">
  .study-parent-relation {
    &__section {
      margin-bottom: 16px;
    }

    &__section-head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 12px;
    }

    &__section-title {
      margin: 0;
      font-size: 15px;
      font-weight: 600;
      line-height: 1.4;
      color: @text-color-base;
    }

    &__head {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 16px;
      border-radius: 8px;
      background-color: @component-background;
    }

    &__avatar {
      &--empty {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        background-color: fade(@primary-color, 12%);
        color: @primary-color;
        font-size: 20px;
        font-weight: 600;
      }
    }

    &__head-text {
      min-width: 0;
    }

    &__name {
      font-size: 15px;
      font-weight: 600;
      line-height: 1.4;
      color: @text-color-base;
    }

    &__code {
      font-size: 13px;
      color: @text-color-call-out;
    }

    &__actions {
      display: inline-flex;
      align-items: center;
      gap: 4px;
    }

    &__muted {
      font-size: 13px;
      color: @text-color-call-out;
    }

    &__net-error {
      margin-bottom: 16px;
    }
  }
</style>
