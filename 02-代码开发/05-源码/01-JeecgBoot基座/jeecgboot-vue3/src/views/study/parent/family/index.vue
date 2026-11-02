<template>
  <StudyParentShell :child-name="childName" :child-code="childCode" :child-options="childOptions" @switch-child="onSwitch">
    <a-spin :spinning="loading">
      <a-alert v-if="netError" class="study-parent-family__net-error" type="warning" show-icon :message="netError.message">
        <template #action>
          <a-button size="small" @click="onRetry">重试</a-button>
        </template>
      </a-alert>

      <!-- ① 家庭信息卡：家庭名称 + 成员数 + 家庭码状态 -->
      <section class="study-parent-family__section">
        <div class="study-parent-family__card">
          <div class="study-parent-family__card-row">
            <span class="study-parent-family__card-label">家庭名称</span>
            <span class="study-parent-family__card-value">{{ familyName || '未命名家庭' }}</span>
          </div>
          <div class="study-parent-family__card-row">
            <span class="study-parent-family__card-label">成员数</span>
            <span class="study-parent-family__card-value">{{ memberCount }} 位家长</span>
          </div>
          <div class="study-parent-family__card-row">
            <span class="study-parent-family__card-label">家庭码</span>
            <span class="study-parent-family__card-value">
              <!-- 家庭码明文只在生成时显示一次，这里永远显示状态而不是码 -->
              <a-tag color="blue">已设置</a-tag>
              <span class="study-parent-family__card-note">明文只在生成时显示一次</span>
            </span>
          </div>
          <div class="study-parent-family__card-actions">
            <a-button size="small" @click="openRename">修改名称</a-button>
            <a-popconfirm title="重置后原来的家庭码立即失效，确定吗？" ok-text="确定" cancel-text="取消" @confirm="onResetCode">
              <a-button size="small" danger>重置家庭码</a-button>
            </a-popconfirm>
          </div>
        </div>
      </section>

      <!-- ② 家长成员表格 -->
      <section class="study-parent-family__section">
        <div class="study-parent-family__section-head">
          <h2 class="study-parent-family__section-title">家长成员</h2>
          <a-button type="primary" size="small" @click="openAddMember">邀请家长</a-button>
        </div>

        <!-- 家长成员列表：走 StudyDataList（≤992px 自动转卡片，TC-08-19 / F-16） -->
        <StudyDataList
          :columns="memberColumns"
          :data-source="members"
          row-key="memberId"
          class="study-parent-family__list"
        >
          <template #cell-relationLabel="{ row }">
            <StudyEnumTag kind="relation" :value="row.relationLabel || '其他'" />
          </template>
          <template #cell-memberRole="{ row }">
            <StudyEnumTag kind="role" :value="row.memberRole === 'OWNER' ? '创建者' : '家长'" />
          </template>
          <template #cell-status="{ row }">
            <a-badge :status="row.status === 1 ? 'success' : 'default'" :text="row.status === 1 ? '正常' : '已停用'" />
          </template>
          <template #cell-action="{ row }">
            <span class="study-parent-family__actions">
              <span class="study-parent-family__self">{{ row.isSelf ? '（我）' : '' }}</span>
              <StudyActionColumn :actions="memberActions(row)" />
            </span>
          </template>
        </StudyDataList>
      </section>
    </a-spin>

    <!-- 邀请家长弹窗 -->
    <a-modal
      v-model:open="addMemberOpen"
      title="邀请家长"
      ok-text="添加"
      cancel-text="取消"
      :confirm-loading="submitting"
      :body-style="STUDY_MODAL_BODY_STYLE"
      @ok="onAddMember"
    >
      <a-form :layout="vertical">
        <a-form-item label="账号名" required>
          <a-input v-model:value="addMemberForm.username" placeholder="对方的登录账号名" />
        </a-form-item>
        <a-form-item label="关系">
          <a-select v-model:value="addMemberForm.relationLabel" :options="relationOptions" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 修改家庭名称弹窗 -->
    <a-modal
      v-model:open="renameOpen"
      title="修改家庭名称"
      ok-text="保存"
      cancel-text="取消"
      :confirm-loading="submitting"
      :body-style="STUDY_MODAL_BODY_STYLE"
      @ok="onRename"
    >
      <a-form :layout="vertical">
        <a-form-item label="家庭名称">
          <a-input v-model:value="renameForm.familyName" :maxlength="32" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 明文一次性展示弹窗（家庭码） -->
    <a-modal
      v-model:open="codeModalOpen"
      title="新的家庭码"
      :footer="null"
      :mask-closable="false"
      :body-style="STUDY_MODAL_BODY_STYLE"
    >
      <div class="study-parent-family__secret">
        <div class="study-parent-family__secret-value">{{ newFamilyCode }}</div>
        <p class="study-parent-family__secret-hint">{{ ONE_TIME_HINT }}</p>
      </div>
    </a-modal>
  </StudyParentShell>
</template>

<script lang="ts">
  import { computed, defineComponent, onMounted, ref } from 'vue';
  import { message } from 'ant-design-vue';
  import StudyParentShell from '/@/components/study/StudyParentShell.vue';
  import StudyDataList from '/@/components/study/StudyDataList.vue';
  import StudyEnumTag from '/@/components/study/StudyEnumTag.vue';
  import StudyActionColumn, { type StudyActionColumnAction } from '/@/components/study/StudyActionColumn.vue';
  import { useParentChild } from '../useParentChild';
  import { describeNetError, OFFLINE_HINT, RETRY_LABEL, type StudyNetError } from '../../net';
  import {
    ONE_TIME_HINT,
    RELATION_LABELS,
    addFamilyMember,
    fetchFamilyDetail,
    fetchFamilyMembers,
    removeFamilyMember,
    renameFamily,
    resetFamilyCode,
    type StudyFamilyMemberVO,
  } from '../familyApi';

  /** 弹窗正文样式（设计规范 §8.5） */
  const STUDY_MODAL_BODY_STYLE = { paddingTop: '12px' };

  /**
   * 家长端「家庭与成员管理」页（设计规范 §5.4）。
   *
   * <p>写操作页：与只读数据页不同，本页保留「移除」「重置家庭码」等动作，
   * 危险操作走 {@code a-popconfirm} 二次确认（§8.7）。</p>
   *
   * <p><b>红线口径</b>：成员列表只给昵称 / 关系标签 / 角色 / 状态，
   * 不展示任何家长的学习数据指标——家长之间互不可见（R-04 强化）。</p>
   */
  export default defineComponent({
    name: 'StudyParentFamilyPage',
    components: { StudyParentShell, StudyDataList, StudyEnumTag, StudyActionColumn },
    setup() {
      const { childCode, childName, childOptions, loadChildren } = useParentChild();

      const familyName = ref('');
      const memberCount = ref(0);
      const members = ref<StudyFamilyMemberVO[]>([]);
      const loading = ref(false);
      const submitting = ref(false);
      const netError = ref<StudyNetError | null>(null);

      const addMemberOpen = ref(false);
      const renameOpen = ref(false);
      const codeModalOpen = ref(false);
      const newFamilyCode = ref('');
      const addMemberForm = ref<{ username: string; relationLabel: string }>({ username: '', relationLabel: '妈妈' });
      const renameForm = ref<{ familyName: string }>({ familyName: '' });

      const memberColumns = [
        { key: 'relationLabel', title: '关系', width: '120px' },
        { key: 'memberRole', title: '角色', width: '100px' },
        { key: 'status', title: '状态', width: '120px' },
        { key: 'action', title: '操作', width: '120px' },
      ];

      const relationOptions = RELATION_LABELS.map((item) => ({ label: item, value: item }));

      /**
       * 成员行的操作项（§8.7：走统一操作列，危险动作二次确认）。
       *
       * <p>「移除」是危险动作且文案里没有「删除」二字，
       * 所以这里显式标 {@code danger}——危险判定以显式标记为准。</p>
       */
      const memberActions = (record: StudyFamilyMemberVO): StudyActionColumnAction[] => {
        if (record.isSelf) {
          return [];
        }
        return [
          {
            key: 'remove',
            label: '移除',
            danger: true,
            confirm: {
              title: '移除后该家长将看不到这个家庭，确定吗？',
              okText: '确定',
              cancelText: '取消',
            },
            onClick: () => onRemoveMember(record.memberId),
          },
        ];
      };

      const load = async () => {
        loading.value = true;
        try {
          const [detail, list] = await Promise.all([fetchFamilyDetail(), fetchFamilyMembers()]);
          familyName.value = detail?.familyName || '';
          memberCount.value = detail?.memberCount ?? list?.length ?? 0;
          members.value = list || [];
        } catch (e) {
          members.value = [];
          const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
          netError.value = state.offline ? state : null;
        } finally {
          loading.value = false;
        }
      };

      const onRetry = async () => {
        netError.value = null;
        await load();
      };

      const onSwitch = (value: string) => {
        childCode.value = value;
      };

      /** 打开「邀请家长」弹窗 */
      const openAddMember = () => {
        addMemberForm.value = { username: '', relationLabel: '妈妈' };
        addMemberOpen.value = true;
      };

      /** 提交邀请 */
      const onAddMember = async () => {
        const username = addMemberForm.value.username.trim();
        if (!username) {
          message.warning('请填写账号名');
          return;
        }
        submitting.value = true;
        try {
          await addFamilyMember({ username, relationLabel: addMemberForm.value.relationLabel });
          message.success('已添加');
          addMemberOpen.value = false;
          await load();
        } catch (e) {
          message.error(String(e?.message || '添加失败'));
        } finally {
          submitting.value = false;
        }
      };

      /** 移除家长成员 */
      const onRemoveMember = async (memberId?: string) => {
        if (!memberId) {
          return;
        }
        try {
          await removeFamilyMember(memberId);
          message.success('已移除');
          await load();
        } catch (e) {
          message.error(String(e?.message || '移除失败'));
        }
      };

      /** 打开「修改名称」弹窗 */
      const openRename = () => {
        renameForm.value = { familyName: familyName.value };
        renameOpen.value = true;
      };

      /** 提交家庭名称 */
      const onRename = async () => {
        const name = renameForm.value.familyName.trim();
        if (!name) {
          message.warning('请填写家庭名称');
          return;
        }
        submitting.value = true;
        try {
          await renameFamily(name);
          message.success('已保存');
          renameOpen.value = false;
          await load();
        } catch (e) {
          message.error(String(e?.message || '保存失败'));
        } finally {
          submitting.value = false;
        }
      };

      /** 重置家庭码：明文只在这里显示一次 */
      const onResetCode = async () => {
        try {
          const code = await resetFamilyCode();
          newFamilyCode.value = code || '';
          codeModalOpen.value = true;
        } catch (e) {
          message.error(String(e?.message || '重置失败'));
        }
      };

      onMounted(async () => {
        await loadChildren();
        await load();
      });

      return {
        childCode,
        childName,
        childOptions,
        familyName,
        memberCount,
        members,
        loading,
        submitting,
        netError,
        memberColumns,
        relationOptions,
        addMemberOpen,
        renameOpen,
        codeModalOpen,
        newFamilyCode,
        addMemberForm,
        renameForm,
        memberActions,
        STUDY_MODAL_BODY_STYLE,
        ONE_TIME_HINT,
        onRetry,
        onSwitch,
        openAddMember,
        onAddMember,
        onRemoveMember,
        openRename,
        onRename,
        onResetCode,
      };
    },
  });
</script>

<style lang="less">
  .study-parent-family {
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

    /* 家庭信息卡 */
    &__card {
      padding: 16px;
      border-radius: 8px;
      background-color: @component-background;
    }

    &__card-row {
      display: flex;
      align-items: center;
      min-height: 32px;
      margin-bottom: 8px;
    }

    &__card-label {
      min-width: 72px;
      font-size: 14px;
      color: @text-color-call-out;
    }

    &__card-value {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 14px;
      color: @text-color-base;
    }

    &__card-note {
      font-size: 13px;
      color: @text-color-call-out;
    }

    &__card-actions {
      display: flex;
      gap: 8px;
      margin-top: 12px;
    }

    &__actions {
      display: inline-flex;
      align-items: center;
      gap: 4px;
    }

    &__self {
      font-size: 13px;
      color: @text-color-call-out;
    }

    &__net-error {
      margin-bottom: 16px;
    }

    /* 明文一次性展示 */
    &__secret {
      text-align: center;
    }

    &__secret-value {
      margin-bottom: 12px;
      padding: 16px;
      border-radius: 8px;
      background-color: fade(@primary-color, 8%);
      font-size: 20px;
      font-weight: 600;
      letter-spacing: 6px;
      color: @primary-color;
    }

    &__secret-hint {
      margin: 0;
      font-size: 14px;
      line-height: 1.5715;
      color: @text-color-call-out;
    }
  }
</style>
