<template>
  <StudyLayout module-title="错题录入">
    <div class="study-kid-error-import">
      <!-- 断网：可读提示 +「重试」入口（F-BASE-08：V0 不做离线补交，只重试） -->
      <a-alert
        v-if="netError"
        class="study-kid-error-import__net-error"
        type="warning"
        show-icon
        :message="netError.message"
      >
        <template #action>
          <a-button size="small" @click="onRetry">重试</a-button>
        </template>
      </a-alert>

      <a-spin :spinning="loading" class="study-kid-error-import__spin">
        <div class="study-kid-error-import__head">
          <Icon icon="ant-design:scan-outlined" class="study-kid-error-import__head-icon" />
          <h2 class="study-kid-error-import__title">记一道错题</h2>
        </div>
        <p class="study-kid-error-import__hint">写清楚是哪道题、卡在哪一步，之后复习就按这个来</p>

        <div class="study-kid-error-import__field">
          <label class="study-kid-error-import__label">来自哪门</label>
          <a-select
            v-model:value="form.sourceModule"
            class="study-kid-error-import__control"
            size="large"
            :options="moduleOptions"
            placeholder="选一门"
          />
        </div>

        <div class="study-kid-error-import__field">
          <label class="study-kid-error-import__label">题目类型</label>
          <a-select
            v-model:value="form.contentType"
            class="study-kid-error-import__control"
            size="large"
            :options="contentTypeOptions"
            placeholder="选一种"
          />
        </div>

        <div class="study-kid-error-import__field">
          <label class="study-kid-error-import__label">题目编号</label>
          <a-input
            v-model:value="form.contentId"
            class="study-kid-error-import__control"
            size="large"
            placeholder="课本或练习册上的题号"
            :maxlength="32"
          />
        </div>

        <div class="study-kid-error-import__field">
          <label class="study-kid-error-import__label">考点（可不填）</label>
          <a-input
            v-model:value="form.knowledgePointCode"
            class="study-kid-error-import__control"
            size="large"
            placeholder="比如：分数的加减"
            :maxlength="32"
          />
        </div>

        <div class="study-kid-error-import__field">
          <label class="study-kid-error-import__label">我写的（可不填）</label>
          <a-textarea
            v-model:value="form.rawAnswer"
            class="study-kid-error-import__control"
            :rows="3"
            placeholder="把你写的写下来，方便以后回头看"
            :maxlength="500"
          />
        </div>

        <div class="study-kid-error-import__field">
          <label class="study-kid-error-import__label">卡在哪一步</label>
          <div class="study-kid-error-import__cause-list">
            <a-button
              v-for="item in KID_CAUSE_OPTIONS"
              :key="item"
              class="study-kid-error-import__cause"
              :class="{ 'is-active': form.errorCause === item }"
              @click="form.errorCause = item"
            >
              {{ item }}
            </a-button>
          </div>
        </div>

        <div class="study-kid-error-import__field">
          <label class="study-kid-error-import__label">题目来源</label>
          <a-select
            v-model:value="form.copyrightType"
            class="study-kid-error-import__control"
            size="large"
            :options="copyrightOptions"
            placeholder="选一种"
          />
        </div>

        <div class="study-kid-error-import__field">
          <label class="study-kid-error-import__label">出处</label>
          <a-input
            v-model:value="form.sourceRef"
            class="study-kid-error-import__control"
            size="large"
            placeholder="课本名称或页码"
            :maxlength="64"
          />
        </div>

        <p v-if="doneMessage" class="study-kid-error-import__done">{{ doneMessage }}</p>
      </a-spin>
    </div>

    <template #footer>
      <a-button
        type="primary"
        class="study-kid-error-import__primary"
        :loading="submitting"
        :disabled="!canSubmit"
        @click="onSubmit"
      >
        记下来
      </a-button>
    </template>
  </StudyLayout>
</template>

<script lang="ts">
  import { computed, defineComponent, onMounted, ref } from 'vue';
  import { Icon } from '/@/components/Icon';
  import { useMessage } from '/@/hooks/web/useMessage';
  import StudyLayout from '/@/layouts/study/index.vue';
  import { fetchCurrentKid, reportError } from '../../../api';
  import { describeNetError, OFFLINE_HINT, RETRY_LABEL, type StudyNetError } from '../../../net';
  import { KID_CAUSE_OPTIONS } from '../../kidCommon';

  /** 版权类型：自编（默认） / 外部来源 */
  const COPYRIGHT_SELF = 'SELF';

  /**
   * 孩子端错题录入页（设计规范 §9.8 / §10.2）。
   *
   * 红线要点：
   * 孩子身份来自会话，服务端按登录账号做数据范围判定，前端不传别人的标识（R-04 / ADR-018）；
   * 错因必须先选（服务端缺错因返回 4001），错因五维是中性表述（§10.3）；
   * 不索取、不展示任何答案字段——只记「我写的」与卡点（R-01）；
   * 外部来源必须填出处（服务端缺出处返回 4006）。
   *
   * ⚠ 注释里不要写 HTML 标签：红线扫描取的是尖括号之间的文本（R04-3）。
   */
  export default defineComponent({
    name: 'StudyKidErrorImportPage',
    components: { Icon, StudyLayout },
    setup() {
      const { createMessage } = useMessage();

      const form = ref({
        sourceModule: 'MATH',
        contentType: 'QUESTION',
        contentId: '',
        knowledgePointCode: '',
        rawAnswer: '',
        errorCause: '',
        copyrightType: COPYRIGHT_SELF,
        sourceRef: '',
      });
      const loading = ref(false);
      const submitting = ref(false);
      /** 当前登录孩子（服务端按会话判定，前端只用来提交） */
      const childId = ref('');
      /** 录入完成提示 */
      const doneMessage = ref('');
      /** 断网提示与重试入口（F-BASE-08） */
      const netError = ref<StudyNetError | null>(null);
      /** 重试动作：断网后点「重试」重放上一次失败的服务端请求 */
      const retryAction = ref<null | (() => Promise<void>)>(null);

      /** 来源模块选项 */
      const moduleOptions = [
        { value: 'MATH', label: '数学' },
        { value: 'GRAMMAR', label: '语法' },
        { value: 'WORD', label: '背单词' },
        { value: 'WRITING', label: '写作' },
        { value: 'ERROR', label: '其他' },
      ];

      /** 题型选项（与后端 content_type 取值一致） */
      const contentTypeOptions = [
        { value: 'QUESTION', label: '题目' },
        { value: 'SENTENCE', label: '句子' },
        { value: 'WORD', label: '单词' },
        { value: 'MOTHER', label: '母题' },
        { value: 'MATH_VARIANT', label: '变式' },
      ];

      /** 来源选项 */
      const copyrightOptions = [
        { value: COPYRIGHT_SELF, label: '自己整理的' },
        { value: 'BOOK', label: '课本或练习册上的' },
      ];

      /** 能不能提交：题目编号 + 错因必填；外部来源还要填出处（服务端缺出处返回 4006） */
      const canSubmit = computed(() => {
        if (!form.value.contentId.trim()) {
          return false;
        }
        if (!form.value.errorCause) {
          return false;
        }
        if (form.value.copyrightType !== COPYRIGHT_SELF && !form.value.sourceRef.trim()) {
          return false;
        }
        return true;
      });

      /** 取当前孩子（身份来自会话） */
      const load = async () => {
        loading.value = true;
        try {
          const kid = await fetchCurrentKid();
          childId.value = kid?.childId || '';
          netError.value = null;
        } catch (e) {
          // F-BASE-08：断网 → 可读提示 +「重试」入口；业务错误 → 保留原来的提示语义
          const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
          netError.value = state.offline ? state : null;
          if (state.offline) {
            retryAction.value = () => load();
          } else {
            createMessage.warning('没取到当前身份，稍后再看看');
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

      /** 提交：错题入池，判定来源固定为规则（R-02） */
      const onSubmit = async () => {
        if (!canSubmit.value) {
          return;
        }
        submitting.value = true;
        doneMessage.value = '';
        try {
          const result = await reportError({
            childId: childId.value,
            sourceModule: form.value.sourceModule,
            contentType: form.value.contentType,
            contentId: form.value.contentId.trim(),
            knowledgePointCode: form.value.knowledgePointCode.trim(),
            errorCause: form.value.errorCause,
            rawAnswer: form.value.rawAnswer.trim(),
            judgeSource: 'rule',
            copyrightType: form.value.copyrightType,
            sourceRef: form.value.sourceRef.trim(),
          });
          doneMessage.value = result?.duplicated ? '这一道已经在错题本里了' : '记下来了，之后按这个复习';
          form.value.contentId = '';
          form.value.rawAnswer = '';
          form.value.knowledgePointCode = '';
          form.value.errorCause = '';
          form.value.sourceRef = '';
        } catch (e) {
          // F-BASE-08：断网 → 可读提示 +「重试」入口；业务错误 → 保留原来的提示语义
          const state = describeNetError(e, OFFLINE_HINT, RETRY_LABEL);
          netError.value = state.offline ? state : null;
          if (state.offline) {
            retryAction.value = () => onSubmit();
          } else {
            createMessage.warning('这次没记上，再试一次');
          }
        } finally {
          submitting.value = false;
        }
      };

      onMounted(() => {
        load();
      });

      return {
        KID_CAUSE_OPTIONS,
        form,
        moduleOptions,
        contentTypeOptions,
        copyrightOptions,
        canSubmit,
        doneMessage,
        loading,
        submitting,
        netError,
        onRetry,
        onSubmit,
      };
    },
  });
</script>

<style lang="less">
  .study-kid-error-import {
    padding: 16px;

    &__spin {
      display: block;
      min-height: 240px;
    }

    &__head {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    &__head-icon {
      font-size: 20px;
      color: @primary-color;
    }

    &__title {
      margin: 0;
      font-size: 20px;
      font-weight: 600;
      line-height: 1.4;
      color: @text-color-base;
    }

    &__hint {
      margin: 8px 0 16px;
      font-size: 14px;
      line-height: 1.5715;
      color: @text-color-call-out;
    }

    &__field {
      margin-bottom: 16px;
    }

    &__label {
      display: block;
      margin-bottom: 8px;
      font-size: 16px;
      font-weight: 500;
      line-height: 1.4;
      color: @text-color-base;
    }

    &__control {
      width: 100%;
      min-height: 44px;
      font-size: 16px;
    }

    &__cause-list {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }

    /* 错因：点击目标 ≥44px（§10.1） */
    &__cause {
      min-height: 44px;
      font-size: 14px;
      border-radius: 8px;

      &.is-active {
        border: 1px solid @primary-color;
        color: @primary-color;
      }
    }

    &__done {
      margin: 16px 0 0;
      padding: 12px 16px;
      border: 1px solid @border-color-light;
      border-radius: 8px;
      background-color: @success-background-color;
      font-size: 16px;
      line-height: 1.5715;
      color: @text-color-base;
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
