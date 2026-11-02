/**
 * 学习端「断网可读提示 + 可重试」共享模块（F-BASE-08）。
 *
 * <p><b>V0 明确不做</b>离线答题、本地缓存与断网补交（F-BASE-08）：
 * 本模块只提供三件事——</p>
 * <ol>
 *   <li>判定「网络不可达」（axios 网络错误 / 超时 / {@code ERR_NETWORK} 一类）；</li>
 *   <li>一句<b>统一文案</b>，保证断网时给出的是可读提示而不是静默失败；</li>
 *   <li>一个可展示的<b>错误态</b>，页面据此渲染「重试」入口。</li>
 * </ol>
 *
 * <p><b>红线口径</b>：</p>
 * <ul>
 *   <li>断网<b>不降级为本地判分</b>——判定仍然只由服务端规则给出（R-02）；
 *       本模块不引入任何本地缓存 / 离线队列 / Service Worker。</li>
 *   <li>重试只是<b>重新发起同一个服务端请求</b>，不做断网补交（F-BASE-08）。</li>
 *   <li>提示文案不得出现评价孩子表现、横向比较一类的措辞（R-04）。</li>
 * </ul>
 */

/**
 * 断网提示的统一文案。
 *
 * <p>口径统一，页面不再各写各的；文案里必须带「重试」二字，
 * 让用户一眼知道「可以重试」，而不是以为系统坏了（R-04：不评价孩子）。</p>
 */
export const OFFLINE_HINT = '网络好像断了，检查一下网络后点重试';

/** 重试入口的统一文案 */
export const RETRY_LABEL = '重试';

/** 业务错误（非断网）的兜底文案：业务错误仍由页面按自己的语义提示 */
export const DEFAULT_ERROR_MESSAGE = '这次没成功，再试一次';

/**
 * 判定为「网络不可达」的错误码。
 *
 * <p>axios v1 用 {@code ERR_NETWORK}；超时用 {@code ECONNABORTED}；
 * 其余是浏览器网络栈的连接类错误码。</p>
 */
const OFFLINE_ERROR_CODES = [
  'ERR_NETWORK',
  'ECONNABORTED',
  'ECONNRESET',
  'ECONNREFUSED',
  'ETIMEDOUT',
  'ENOTFOUND',
  'EAI_AGAIN',
];

/**
 * 可展示的失败错误态。
 *
 * <p>页面拿到它就能渲染「可读提示 + 重试入口」，不需要自己再判断异常类型。</p>
 */
export interface StudyNetError {
  /** 是否网络不可达（断网 / 超时 / 连接失败） */
  offline: boolean;
  /** 可直接展示的提示文案 */
  message: string;
  /** 重试入口的文案 */
  retryLabel: string;
  /** 原始异常：页面可再按业务语义判断（例如越权 4010） */
  raw: any;
}

/**
 * 判断一个异常是不是「网络不可达」。
 *
 * <p>识别三类：</p>
 * <ul>
 *   <li>axios 的网络错误码（{@code ERR_NETWORK} / {@code ECONNABORTED} 等）；</li>
 *   <li>错误文案里的 {@code Network Error} / {@code timeout}；</li>
 *   <li>请求已经发出但<b>没拿到响应</b>（{@code response} 为空且带 {@code request} / {@code config}）。</li>
 * </ul>
 *
 * <p><b>刻意不把业务错误算进来</b>：服务端的 {@code Result} 失败会被抛成普通
 * {@code Error}（只有中文业务文案，没有 code / request / config），
 * 因此业务错误仍然走页面原来的提示语义，不显示「网络断了」。</p>
 *
 * @param e 捕获到的异常
 * @return true = 网络不可达
 */
export function isNetworkError(e: any): boolean {
  if (!e) {
    return false;
  }
  const code = String(e.code ?? '').toUpperCase();
  if (OFFLINE_ERROR_CODES.includes(code)) {
    return true;
  }
  const message = String(e.message ?? '');
  if (message.includes('Network Error')) {
    return true;
  }
  if (/timeout/i.test(message)) {
    return true;
  }
  // 请求发出去了但没有响应 = 网络不可达（服务端返回错误时 response 是有的）
  if (e.response === undefined && (e.request !== undefined || e.config !== undefined)) {
    return true;
  }
  return false;
}

/**
 * 把一个异常转成「可展示的错误态」。
 *
 * <p>断网时给出统一的可读提示（{@link OFFLINE_HINT}）；
 * 非断网时给出兜底文案，业务语义仍由页面自己决定。</p>
 *
 * @param e          捕获到的异常
 * @param hint       断网提示文案
 * @param retryLabel 重试入口文案
 * @return 可展示的错误态
 */
export function describeNetError(
  e: any,
  hint: string = OFFLINE_HINT,
  retryLabel: string = RETRY_LABEL,
): StudyNetError {
  const offline = isNetworkError(e);
  return {
    offline,
    message: offline ? hint : DEFAULT_ERROR_MESSAGE,
    retryLabel,
    raw: e,
  };
}

/**
 * 「重试」helper 的返回值。
 *
 * @param T 加载结果的类型
 */
export interface StudyRetryState<T> {
  /** 加载成功时的数据；失败时为 null */
  data: T | null;
  /** 失败时的可展示错误态；成功时为 null */
  error: StudyNetError | null;
}

/**
 * 「重试」helper：把一个异步加载函数包一层。
 *
 * <p>失败时<b>返回可展示的错误态</b>而不是静默吞掉异常，
 * 页面据此渲染「可读提示 + 重试入口」（F-BASE-08：断网时提示明确、无静默失败）。</p>
 *
 * <p>重试语义 = <b>重新发起同一个服务端请求</b>：V0 不做本地缓存，
 * 也不做断网补交，网络恢复后数据不会丢、也不会重复提交（F-BASE-08）。</p>
 *
 * @param loader     异步加载函数
 * @param hint       断网提示文案
 * @param retryLabel 重试入口文案
 * @return 加载结果与错误态
 */
export async function withRetry<T>(
  loader: () => Promise<T>,
  hint: string = OFFLINE_HINT,
  retryLabel: string = RETRY_LABEL,
): Promise<StudyRetryState<T>> {
  try {
    const data = await loader();
    return { data, error: null };
  } catch (e) {
    return { data: null, error: describeNetError(e, hint, retryLabel) };
  }
}


