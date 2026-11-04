/**
 * 孩子端 kid 线的共用展示口径。
 *
 * 只放「展示用」的映射与常量，不放接口调用——接口封装统一收在
 * src/views/study/api.ts，页面不得直连 defHttp。
 *
 * 红线口径：
 * 文案中性，不评价孩子表现，也不出现任何横向比较措辞（R-04）；
 * 错因五维与后端 ErrorCause 常量逐字一致，中性表述，不做负向评价；
 * 掌握度只给当前这道题自己的客观状态（R-04）。
 *
 * ⚠ 注释里不要写 HTML 标签：红线扫描取的是尖括号之间的文本，
 *   注释里的标签会让注释散文被当成用户可见文案扫（R04-3）。
 */

/** 来源模块（后端 {@code source_module} 取值）→ 中文标签 */
const MODULE_LABELS: Record<string, string> = {
  ERROR: '其他',
  WORD: '背单词',
  MATH: '数学',
  GRAMMAR: '语法',
  WRITING: '写作',
};

/**
 * 来源模块的中文标签。
 *
 * @param code 后端返回的来源模块编码
 * @returns 中文标签；没有对应映射时原样返回编码，不臆造含义
 */
export function kidModuleLabel(code?: string): string {
  const key = String(code || '').trim().toUpperCase();
  if (!key) {
    return '其他';
  }
  return MODULE_LABELS[key] || key;
}

/** 错因五维（与后端 {@code ErrorCause} 一致，中性表述） */
export const KID_CAUSE_OPTIONS = ['概念不清', '审题偏差', '计算失误', '方法不会', '时间不够'];

/**
 * 错因的中文标签。
 *
 * @param cause 后端返回的错因
 * @returns 错因文本；还没标错因时给出中性兜底
 */
export function kidCauseLabel(cause?: string): string {
  const text = String(cause || '').trim();
  return text || '还没标错因';
}

/** 掌握度（后端 {@code ReviewSchedulePolicy} 的四个刻度）→ 中文标签 */
const MASTERY_LABELS: Record<number, string> = {
  1: '刚认识',
  2: '在学',
  3: '比较熟',
  4: '已掌握',
};

/**
 * 掌握度的中文标签。
 *
 * @param state 后端返回的掌握度刻度
 * @returns 中文标签；没有对应刻度时返回空串（不猜、不编）
 */
export function kidMasteryLabel(state?: number): string {
  if (state === undefined || state === null) {
    return '';
  }
  return MASTERY_LABELS[state] || '';
}

/**
 * 把后端返回的日期时间裁成「月-日」。
 *
 * <p>孩子端只显示「哪天」，不显示时分秒——信息够用，也不占屏幕。</p>
 *
 * @param value 后端返回的日期时间文本
 * @returns 月-日；没有值时返回空串
 */
export function kidDateLabel(value?: string): string {
  const text = String(value || '').trim();
  if (!text) {
    return '';
  }
  const matched = text.match(/^\d{4}-(\d{2})-(\d{2})/);
  if (!matched) {
    return text.slice(0, 10);
  }
  return `${matched[1]}-${matched[2]}`;
}
