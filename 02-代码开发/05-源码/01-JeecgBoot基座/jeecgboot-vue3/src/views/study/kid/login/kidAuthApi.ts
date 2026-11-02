/**
 * 孩子端两步登录 · 前端 API 层（设计规范 §9.8 / §6.3）。
 *
 * <p>对应后端 {@code StudyKidAuthController}：</p>
 * <ol>
 *   <li>{@code POST /api/v1/study/kid/lookup} —— 家庭码 → 候选孩子列表（免认证）；</li>
 *   <li>{@code POST /api/v1/study/kid/login} —— 孩子 id + PIN → JWT（免认证）。</li>
 * </ol>
 *
 * <p><b>红线口径</b>：</p>
 * <ul>
 *   <li>两步都是<b>免认证</b>端点，不暴露底座登录页与管理端任何入口（§6.1）；</li>
 *   <li>家庭码错误只提示「家庭码不正确」，<b>不泄露家庭是否存在</b>（§6.2）；</li>
 *   <li>PIN 错误**不显示剩余次数**，连续 5 次失败锁 10 分钟（§9.8）。</li>
 * </ul>
 */

import { defHttp } from '/@/utils/http/axios';

/** 静默失败：登录页要自己渲染错误态，不让 axios 再弹一次 toast */
const SILENT = { isTransformResponse: false, errorMessageMode: 'none' as const };

// ------------------------------------------------------------------ 类型

/**
 * 候选孩子。
 *
 * <p><b>只有 id / 昵称 / 头像</b>——服务端不下发账号名、生日、年级等任何其他信息，
 * 界面也不得凭空补（§6.2「只有 id / 昵称 / 头像」）。</p>
 */
export interface StudyKidCandidateVO {
  /** 孩子主键（登录时提交这个，不是业务 id） */
  id?: string;
  /** 昵称 */
  nickname?: string;
  /** 头像 */
  avatar?: string;
}

/**
 * 登录成功返回。
 *
 * <p>{@code parentNames} 只有**昵称**，不含账号名（§9.8「不得出现任何家长账号标识」）。</p>
 */
export interface StudyKidLoginVO {
  /** JWT */
  token?: string;
  childId?: string;
  childCode?: string;
  nickname?: string;
  avatar?: string;
  gradeCode?: string;
  /** 家长昵称列表（仅昵称） */
  parentNames?: string[];
}

/** 当前孩子信息 */
export interface StudyKidProfileVO {
  childId?: string;
  childCode?: string;
  nickname?: string;
  avatar?: string;
  gradeCode?: string;
  parentNames?: string[];
}

// ------------------------------------------------------------------ 接口

/**
 * 步骤①：家庭码 → 候选孩子列表。
 *
 * @param familyCode 6 位大写字母数字家庭码
 * @returns 候选孩子列表（仅 id / 昵称 / 头像）
 */
export const lookupKids = (familyCode: string) =>
  defHttp.post<StudyKidCandidateVO[]>({
    url: '/api/v1/study/kid/lookup',
    data: { familyCode },
    ...SILENT,
  });

/**
 * 步骤②：孩子 id + PIN → JWT。
 *
 * @param childId 候选列表里的孩子 id
 * @param pin     4–6 位数字 PIN
 * @returns 登录结果（含 token 与孩子信息）
 */
export const loginKid = (childId: string, pin: string) =>
  defHttp.post<StudyKidLoginVO>({
    url: '/api/v1/study/kid/login',
    data: { childId, pin },
    ...SILENT,
  });

/** 退出登录（清会话回到步骤①） */
export const logoutKid = () =>
  defHttp.post<string>({
    url: '/api/v1/study/kid/logout',
    ...SILENT,
  });

/** 当前孩子信息 */
export const fetchKidProfile = () =>
  defHttp.get<StudyKidProfileVO>({
    url: '/api/v1/study/kid/me',
    ...SILENT,
  });

// ------------------------------------------------------------------ 展示口径

/** 家庭码错误提示（§6.2：不泄露家庭是否存在） */
export const FAMILY_CODE_ERROR = '家庭码不正确';

/** PIN 错误提示（§9.8：中性表述，不显示剩余次数） */
export const PIN_ERROR = 'PIN 不对，再试一次';

/** 锁定提示（§9.8：不含内部计数细节） */
export const LOCKED_HINT = '暂时不能登录，请过一会儿再试';

/** 家庭码输入的长度（6 位大写字母数字） */
export const FAMILY_CODE_LENGTH = 6;

/** PIN 长度范围（4–6 位数字） */
export const PIN_MIN_LENGTH = 4;
export const PIN_MAX_LENGTH = 6;

/**
 * 家庭码格式校验。
 *
 * <p>格式必须是 6 位大写字母或数字（与 {@code StudySecretUtil.newFamilyCode} 一致）。
 * 前端先拦一道，避免把明显非法的输入发给服务端。</p>
 *
 * @param value 输入值
 * @return true = 格式合法
 */
export function isFamilyCodeFormatValid(value: string): boolean {
  return /^[A-Z0-9]{6}$/.test(String(value || '').trim());
}

/**
 * PIN 格式校验。
 *
 * @param value 输入值
 * @return true = 格式合法（4–6 位数字）
 */
export function isPinFormatValid(value: string): boolean {
  const text = String(value || '').trim();
  return new RegExp(`^\\d{${PIN_MIN_LENGTH},${PIN_MAX_LENGTH}}$`).test(text);
}

/**
 * 把家庭码规整成大写（输入时自动纠正大小写，省得用户自己切大写）。
 *
 * @param value 输入值
 * @return 规整后的家庭码
 */
export function normalizeFamilyCode(value: string): string {
  return String(value || '')
    .trim()
    .toUpperCase();
}
