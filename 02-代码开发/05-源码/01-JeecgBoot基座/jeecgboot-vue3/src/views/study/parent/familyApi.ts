/**
 * 家庭与孩子管理 · 前端 API 层（家长端写操作页，设计规范 §9.6 / §5.4–§5.6）。
 *
 * <p>对应后端：</p>
 * <ul>
 *   <li>{@code StudyFamilyController} —— {@code /api/v1/study/family}</li>
 *   <li>{@code StudyChildController} —— {@code /api/v1/study/children}</li>
 * </ul>
 *
 * <p><b>红线口径</b>：</p>
 * <ul>
 *   <li>家庭码 / PIN 的**明文只在服务端返回的那一次**出现，页面必须
 *       「拿到就展示、关闭即不可再查」（§5.4 / §5.5）；</li>
 *   <li>家长之间**互不可见**，成员列表只给昵称 / 账号名 / 关系标签，
 *       不给其他家长的任何数据指标（R-04 强化）；</li>
 *   <li>孩子档案里的性别、生日等**不参与任何对外比较**（§5.5）。</li>
 * </ul>
 */

import { defHttp } from '/@/utils/http/axios';

/**
 * 静默失败开关：写操作页需要自己渲染可读的错误态，
 * 不让 axios 统一弹一次 toast 再抛（家长端要「提示 + 重试」而不是双份提示）。
 */
const SILENT = { isTransformResponse: false, errorMessageMode: 'none' as const };

// ------------------------------------------------------------------ 类型

/** 家庭成员（家长） */
export interface StudyFamilyMemberVO {
  /** 成员记录 id */
  memberId?: string;
  /** 关系标签（妈妈 / 爸爸 / 爷爷 / 奶奶 / 其他） */
  relationLabel?: string;
  /** 成员角色：OWNER / PARENT */
  memberRole?: string;
  /** 状态：1 正常 */
  status?: number;
  /** 是否是当前登录的家长本人 */
  isSelf?: boolean;
  /** 加入时间 */
  createTime?: string;
}

/** 家庭详情 */
export interface StudyFamilyDetailVO {
  familyId?: string;
  familyName?: string;
  /** 成员数 */
  memberCount?: number;
  /** 当前登录家长在本家庭里的角色：OWNER / PARENT */
  myRole?: string;
  members?: StudyFamilyMemberVO[];
}

/** 孩子档案 */
export interface StudyChildVO {
  /** 孩子主键 */
  childId?: string;
  /** 业务 id（CHD-xxx） */
  childCode?: string;
  /** 昵称 */
  nickname?: string;
  /** 头像 */
  avatar?: string;
  /** 性别：0 未知 / 1 男 / 2 女 */
  gender?: number;
  /** 生日 */
  birthday?: string;
  /** 年级：G1..G12 */
  gradeCode?: string;
  /** 状态：1 正常 / 0 停用 */
  status?: number;
  /** 当前家长与该孩子的关系标签 */
  relationLabel?: string;
  /** 是否为主家长关联 */
  isPrimary?: boolean;
}

/** 孩子 ↔ 家长关联 */
export interface StudyRelationVO {
  relationId?: string;
  /** 关系标签 */
  relationLabel?: string;
  /** 是否主家长 */
  isPrimary?: boolean;
  /** 状态：1 正常 */
  status?: number;
  /** 是否是当前登录家长本人 */
  isSelf?: boolean;
}

/** 新增孩子入参 */
export interface StudyChildCreateRequest {
  nickname?: string;
  avatar?: string;
  gender?: number;
  birthday?: string;
  gradeCode?: string;
  /** 初始 PIN（4–6 位数字）；不传则由服务端生成 */
  pin?: string;
  /** 底座账号 id；不传则新建账号 */
  userId?: string;
  /** 与孩子的关系标签 */
  relationLabel?: string;
}

/** 新增孩子返回（明文 PIN 只在这一次返回） */
export interface StudyChildCreateVO {
  childId?: string;
  childCode?: string;
  nickname?: string;
  avatar?: string;
  gender?: number;
  birthday?: string;
  gradeCode?: string;
  /** 底座账号名 */
  username?: string;
  /** 初始 PIN 明文——只此一次 */
  pin?: string;
}

/** 创建家庭返回（家庭码明文只此一次） */
export interface StudyFamilyCreateVO {
  familyId?: string;
  familyName?: string;
  /** 家庭码明文——只此一次 */
  familyCode?: string;
  memberRole?: string;
}

// ------------------------------------------------------------------ 家庭

/** 家庭详情（含成员） */
export const fetchFamilyDetail = (familyId?: string) =>
  defHttp.get<StudyFamilyDetailVO>({
    url: '/api/v1/study/family',
    params: { familyId },
    ...SILENT,
  });

/** 家庭成员列表 */
export const fetchFamilyMembers = (familyId?: string) =>
  defHttp.get<StudyFamilyMemberVO[]>({
    url: '/api/v1/study/family/members',
    params: { familyId },
    ...SILENT,
  });

/** 修改家庭名称 */
export const renameFamily = (familyName: string) =>
  defHttp.post<string>({
    url: '/api/v1/study/family',
    data: { familyName },
    ...SILENT,
  });

/** 重置家庭码：返回**明文一次**，之后任何界面都查不到 */
export const resetFamilyCode = () =>
  defHttp.post<string>({
    url: '/api/v1/study/family/code/reset',
    ...SILENT,
  });

/** 添加家长成员（按底座账号名） */
export const addFamilyMember = (payload: { username: string; relationLabel?: string }) =>
  defHttp.post<string>({
    url: '/api/v1/study/family/members',
    data: payload,
    ...SILENT,
  });

/** 移除家长成员 */
export const removeFamilyMember = (memberId: string) =>
  defHttp.delete<string>({
    url: `/api/v1/study/family/members/${memberId}`,
    ...SILENT,
  });

// ------------------------------------------------------------------ 孩子档案

/** 孩子档案详情 */
export const fetchChildDetail = (code: string) =>
  defHttp.get<StudyChildVO>({
    url: `/api/v1/study/children/${code}`,
    ...SILENT,
  });

/** 更新孩子档案 */
export const updateChild = (code: string, payload: {
  nickname?: string;
  avatar?: string;
  gender?: number;
  birthday?: string;
  gradeCode?: string;
}) =>
  defHttp.put<string>({
    url: `/api/v1/study/children/${code}`,
    data: payload,
    ...SILENT,
  });

/** 新增孩子档案：返回**明文 PIN 一次** */
export const createChild = (payload: StudyChildCreateRequest) =>
  defHttp.post<StudyChildCreateVO>({
    url: '/api/v1/study/children',
    data: payload,
    ...SILENT,
  });

/** 停用孩子档案（数据保留） */
export const disableChild = (code: string) =>
  defHttp.delete<string>({
    url: `/api/v1/study/children/${code}`,
    ...SILENT,
  });

/** 重置孩子 PIN：返回**明文一次** */
export const resetChildPin = (code: string) =>
  defHttp.post<string>({
    url: `/api/v1/study/children/${code}/pin/reset`,
    ...SILENT,
  });

// ------------------------------------------------------------------ 关联管理

/** 孩子的关联家长列表 */
export const fetchChildRelations = (code: string) =>
  defHttp.get<StudyRelationVO[]>({
    url: `/api/v1/study/children/${code}/relations`,
    ...SILENT,
  });

/** 关联家长（重复关联由服务端拒绝） */
export const addChildRelation = (code: string, payload: {
  parentUserId: string;
  relationLabel?: string;
  primary?: boolean;
}) =>
  defHttp.post<string>({
    url: `/api/v1/study/children/${code}/relations`,
    data: payload,
    ...SILENT,
  });

/** 解除关联：解除后该家长立即失去该孩子的数据可见性 */
export const removeChildRelation = (code: string, parentUserId: string) =>
  defHttp.delete<string>({
    url: `/api/v1/study/children/${code}/relations/${parentUserId}`,
    ...SILENT,
  });

// ------------------------------------------------------------------ 展示口径

/**
 * 关系标签的可选值（§5.4「邀请家长」表单）。
 *
 * <p>用固定枚举而不是自由输入：家长之间互不可见，标签只描述
 * 「这个家长和我的孩子是什么关系」，不允许写评价性文字（R-04 强化）。</p>
 */
export const RELATION_LABELS = ['妈妈', '爸爸', '爷爷', '奶奶', '其他'];

/**
 * 性别可选项（§5.5）。
 *
 * <p>「未知」是一等选项——不允许逼家长填性别。</p>
 */
export const GENDER_OPTIONS = [
  { label: '未知', value: 0 },
  { label: '男', value: 1 },
  { label: '女', value: 2 },
];

/**
 * 年级可选项（§5.5）。
 *
 * <p>可选字段，家长不填就不填。</p>
 */
export const GRADE_OPTIONS = Array.from({ length: 12 }, (_, i) => ({
  label: `${i + 1} 年级`,
  value: `G${i + 1}`,
}));

/**
 * 「明文只此一次」的统一提示文案。
 *
 * <p>页面在弹窗里展示完明文就关闭，**不再提供任何查看入口**——
 * 服务端只存哈希，本来也查不到（§5.4 / §5.5 / §11.4）。</p>
 */
export const ONE_TIME_HINT = '这个只会显示一次，请现在就记下来。关闭后无法再查看。';

/**
 * 判断错误是否为「越权」（4010）。
 *
 * <p>口径与家长端只读页保持一致：统一提示「无权查看该孩子的数据」，
 * 不换成别的说法（§11.3 / R-04）。</p>
 *
 * @param e 捕获到的异常
 * @return true = 越权
 */
export function isForbiddenError(e: any): boolean {
  const text = String(e?.message || '');
  return text.includes('无权');
}
