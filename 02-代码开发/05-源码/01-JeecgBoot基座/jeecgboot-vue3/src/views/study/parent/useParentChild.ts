import { computed, ref } from 'vue';
import { fetchChildren, type StudyChildVO } from '../api';
import { withRetry, type StudyNetError } from '../net';

/**
 * 家长端的「当前孩子」状态。
 *
 * <p>孩子由家长自己切换，但**只能切到已关联的孩子**——
 * 服务端会再与关系表求交集，越权一律 4010（设计规范 §9.6 / 数据范围隔离）。</p>
 */
export interface UseParentChild {
  /** 已关联的孩子 */
  children: ReturnType<typeof ref<StudyChildVO[]>>;
  /** 当前孩子的业务ID */
  childCode: ReturnType<typeof ref<string>>;
  /** 当前孩子的昵称 */
  childName: ReturnType<typeof computed<string>>;
  /** 切换条用的下拉项 */
  childOptions: ReturnType<typeof computed<{ label: string; value: string }[]>>;
  /** 加载中 */
  loading: ReturnType<typeof ref<boolean>>;
  /** 是否越权（4010） */
  forbidden: ReturnType<typeof ref<boolean>>;
  /** 载入已关联的孩子 */
  loadChildren: () => Promise<void>;
}

/**
 * 判断异常是否为越权（4010）。
 *
 * <p>越权的提示统一是「无权查看该孩子的数据」，
 * 不换成别的说法（设计规范 §11.3）。</p>
 *
 * @param e 捕获到的异常
 * @return true = 越权
 */
export function isForbidden(e: any): boolean {
  const text = String(e?.message || '');
  return text.includes('无权');
}

/**
 * 创建家长端「当前孩子」状态。
 *
 * <p>取孩子列表失败时**不静默吞掉**：断网会通过 {@code onError} 回调把
 * 「可读提示 + 重试入口」交给页面渲染（F-BASE-08：断网时提示明确、无静默失败）；
 * 越权（4010）仍走 {@code forbidden} 标记，提示口径不变（设计规范 §11.3）。</p>
 *
 * @param onError 失败回调（收到可展示的错误态；越权由 forbidden 表达，不走这里）
 * @return 孩子状态与切换能力
 */
export function useParentChild(onError?: (state: StudyNetError) => void): UseParentChild {
  const children = ref<StudyChildVO[]>([]);
  const childCode = ref('');
  const loading = ref(false);
  const forbidden = ref(false);

  const childName = computed(
    () => children.value.find((item) => item.childCode === childCode.value)?.nickname || '',
  );
  const childOptions = computed(() =>
    children.value.map((item) => ({ label: item.nickname, value: item.childCode })),
  );

  const loadChildren = async () => {
    loading.value = true;
    forbidden.value = false;
    // withRetry：失败时返回可展示的错误态，而不是静默吞掉（F-BASE-08）
    const outcome = await withRetry(() => fetchChildren());
    if (outcome.error) {
      children.value = [];
      forbidden.value = isForbidden(outcome.error.raw);
      onError?.(outcome.error);
    } else {
      children.value = outcome.data || [];
      if (children.value.length > 0 && !childCode.value) {
        childCode.value = children.value[0].childCode;
      }
    }
    loading.value = false;
  };

  return { children, childCode, childName, childOptions, loading, forbidden, loadChildren };
}
