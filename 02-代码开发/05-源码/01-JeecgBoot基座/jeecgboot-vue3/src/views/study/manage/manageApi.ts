/**
 * 管理端内容维护 · 前端 API 层（用户手册 §11.2 第 4 行「管理端内容维护页」）。
 *
 * <p>覆盖《前端页面清单》里管理端 9 个内容维护页对应的后端接口。
 * <b>字段名与后端 VO 一一对应</b>（改动前必须先核对 VO 源码，不要凭印象写）：</p>
 * <table border="1">
 *   <caption>页面 → 接口 → 视图对象</caption>
 *   <tr><th>页面</th><th>路由</th><th>接口</th><th>VO</th></tr>
 *   <tr><td>全局上下文配置</td><td>/study/manage/context</td><td>/context/list、/context</td><td>ContextVO</td></tr>
 *   <tr><td>坐标树浏览器</td><td>/study/manage/coordinate</td><td>/knowledge-points/tree</td><td>KnowledgePointTreeVO</td></tr>
 *   <tr><td>考点维护</td><td>/study/manage/coordinate/:code</td><td>/knowledge-points/{code}</td><td>KnowledgePointVO</td></tr>
 *   <tr><td>考点版本历史</td><td>/study/manage/coordinate/:code/history</td><td>/knowledge-points/{code}/history</td><td>List&lt;KnowledgePointVO&gt;</td></tr>
 *   <tr><td>题目维护</td><td>/study/manage/question</td><td>/knowledge-points/tree、/knowledge-points/search</td><td>KnowledgePointVO</td></tr>
 *   <tr><td>母题维护</td><td>/study/manage/mother</td><td>/math/mother/list、/math/mother/{code}</td><td>MotherListItemVO</td></tr>
 *   <tr><td>词表维护</td><td>/study/manage/word</td><td>/word/line/{t}/list</td><td>WordListVO</td></tr>
 *   <tr><td>语法点维护</td><td>/study/manage/grammar</td><td>/grammar/points</td><td>GrammarPointListVO</td></tr>
 *   <tr><td>拆解库维护</td><td>/study/manage/writing</td><td>/writing/dissections</td><td>WritingDissectionListVO</td></tr>
 * </table>
 *
 * <p><b>红线口径</b>：管理端维护的是<b>全局共享的内容</b>，
 * 不带 {@code child_id}——内容对所有孩子复用（内容表全局共享 / 行为表按孩子隔离）。
 * 页面里<b>不得出现</b>任何孩子的作答数据（R-04：不做公开比较）。</p>
 *
 * <p>因此有两个接口虽然返回体里混着行为字段，页面也<b>只取内容字段</b>：</p>
 * <ul>
 *   <li>{@code /math/mother/list} 的 {@code MotherListItemVO} 同时带
 *       {@code attemptCount / errorCount / mastery / passed}（按孩子统计），
 *       管理端<b>一律不显示</b>这四列；</li>
 *   <li>{@code /word/line/{t}/list} 的 {@code WordListItemVO} 带
 *       {@code feedbackCount / memoryStrength / lastFeedback}（按孩子的记忆状态），
 *       管理端<b>一律不显示</b>。</li>
 * </ul>
 */

import { defHttp } from '/@/utils/http/axios';

/** 管理端是登录态下的写操作，错误由页面自己渲染 */
const SILENT = { isTransformResponse: false, errorMessageMode: 'none' as const };

// ------------------------------------------------------------------ 上下文配置

/**
 * 上下文（教材版本 / 年级 / 护眼间隔等）。
 *
 * <p>字段名对应后端 {@code ContextVO}。</p>
 */
export interface StudyContextVO {
  /** 上下文 id */
  contextId?: string;
  /** 所属孩子（上下文按孩子维度保存） */
  childId?: string;
  /** 上下文编码 */
  contextCode?: string;
  /** 省份编码 */
  provinceCode?: string;
  /** 省份名 */
  provinceName?: string;
  /** 学段编码 */
  stageCode?: string;
  /** 学段名 */
  stageName?: string;
  /** 年级编码 */
  gradeCode?: string;
  /** 年级名 */
  gradeName?: string;
  /** 教材版本 id */
  textbookVersionId?: string;
  /** 教材版本名 */
  textbookVersionName?: string;
  /** 护眼提醒间隔（分钟） */
  eyeIntervalMin?: number;
  /** 是否已配置 */
  configured?: boolean;
  /** 生效时间 */
  effectiveFrom?: string;
}

/** 上下文保存入参（对应 {@code ContextSaveRequest}） */
export interface StudyContextSaveRequest {
  /** 目标孩子业务 id */
  childCode?: string;
  provinceCode?: string;
  provinceName?: string;
  stageCode?: string;
  gradeCode?: string;
  textbookVersionId?: string;
  /** 护眼提醒间隔（分钟） */
  eyeIntervalMin?: number;
}

/**
 * 取上下文列表。
 *
 * @param childCode 目标孩子业务 id；不传则取当前身份可见的第一个孩子
 */
export const fetchContexts = (childCode?: string) =>
  defHttp.get<StudyContextVO[]>({
    url: '/api/v1/study/context/list',
    params: { childCode },
    ...SILENT,
  });

/** 保存上下文 */
export const saveContext = (payload: StudyContextSaveRequest) =>
  defHttp.post<StudyContextVO>({
    url: '/api/v1/study/context',
    data: payload,
    ...SILENT,
  });

// ------------------------------------------------------------------ 坐标树 / 考点

/** 考点内容统计（对应 {@code KnowledgePointContentStatVO}） */
export interface StudyContentStatVO {
  /** 题量 */
  question?: number;
  /** 词量 */
  word?: number;
  /** 母题量 */
  mother?: number;
  /** 语法点量 */
  grammar?: number;
  /** 拆解条数 */
  writing?: number;
}

/** 坐标点（对应 {@code KnowledgePointVO}） */
export interface StudyKnowledgePointVO {
  id?: string;
  /** 考点业务 id */
  code?: string;
  /** 父节点 code */
  parentCode?: string;
  /** 层级 */
  level?: number;
  /** 学科 */
  subjectCode?: string;
  /** 名称 */
  name?: string;
  /** 路径 */
  path?: string;
  /** 题型 */
  questionType?: string;
  /** 分值权重 */
  scoreWeight?: number;
  /** 关联错因 */
  errorTypes?: string[];
  /** 母题业务 id */
  motherQuestionCode?: string;
  /** 版本 */
  version?: string;
  effectiveFrom?: string;
  invalidAt?: string;
  status?: number;
  sortNo?: number;
  /** 内容条数统计 */
  childCount?: StudyContentStatVO;
  /** 子节点 */
  children?: StudyKnowledgePointVO[];
}

/** 坐标树（对应 {@code KnowledgePointTreeVO}） */
export interface StudyKnowledgeTreeVO {
  version?: string;
  updatedAt?: string;
  nodes?: StudyKnowledgePointVO[];
}

/** 考点更新入参（对应 {@code KnowledgePointUpdateRequest}） */
export interface StudyKnowledgeUpdateRequest {
  questionType?: string;
  scoreWeight?: number;
  errorTypes?: string[];
  motherQuestionCode?: string;
}

/** 取坐标树 */
export const fetchKnowledgeTree = () =>
  defHttp.get<StudyKnowledgeTreeVO>({
    url: '/api/v1/study/knowledge-points/tree',
    ...SILENT,
  });

/** 考点详情 */
export const fetchKnowledgePoint = (code: string) =>
  defHttp.get<StudyKnowledgePointVO>({
    url: `/api/v1/study/knowledge-points/${code}`,
    ...SILENT,
  });

/** 更新考点 */
export const updateKnowledgePoint = (code: string, payload: StudyKnowledgeUpdateRequest) =>
  defHttp.put<StudyKnowledgePointVO>({
    url: `/api/v1/study/knowledge-points/${code}`,
    data: payload,
    ...SILENT,
  });

/** 考点版本历史（返回的是同一考点的历史版本列表） */
export const fetchKnowledgeHistory = (code: string) =>
  defHttp.get<StudyKnowledgePointVO[]>({
    url: `/api/v1/study/knowledge-points/${code}/history`,
    ...SILENT,
  });

/** 按关键字检索考点 */
export const searchKnowledgePoints = (keyword: string) =>
  defHttp.get<StudyKnowledgePointVO[]>({
    url: '/api/v1/study/knowledge-points/search',
    params: { keyword },
    ...SILENT,
  });

// ------------------------------------------------------------------ 母题维护

/** 母题列表条目（对应 {@code MotherListItemVO}） */
export interface StudyMotherListItemVO {
  /** 母题业务 id */
  motherCode?: string;
  /** 标题 */
  title?: string;
  /** 学科 */
  subjectCode?: string;
  /** 所属考点 */
  knowledgePointCode?: string;
  /** 考纲权重 */
  outlineWeight?: number;
  /** 关卡顺序 */
  orderNo?: number;
  /** 变式条数（内容维度） */
  variantCount?: number;
  /*
   * 以下四个字段是**按孩子统计的行为数据**，管理端一律不使用：
   * attemptCount / errorCount / consecutiveErrors / mastery / passed
   */
}

/** 母题详情（对应 {@code MotherDetailVO}） */
export interface StudyMotherDetailVO {
  motherCode?: string;
  title?: string;
  /** 条件类型 */
  conditionTypes?: { label?: string; value?: string }[];
  /** 关键关系 */
  keyRelation?: string;
  /** 步骤 */
  steps?: { title?: string; content?: string }[];
  /** 易错点 */
  pitfalls?: string[];
  subjectCode?: string;
  knowledgePointCode?: string;
  /** 需要通过的变式数 */
  passVariantCount?: number;
  orderNo?: number;
  status?: number;
  sectionOrder?: string[];
  keyRelationHighlighted?: boolean;
  highlightSection?: string;
}

/**
 * 母题列表。
 *
 * @param childId 目标孩子 id；管理端选孩子用，不传则按会话身份解析
 */
export const fetchMotherList = (childId?: string) =>
  defHttp.get<StudyMotherListItemVO[]>({
    url: '/api/v1/study/math/mother/list',
    params: { childId },
    ...SILENT,
  });

/** 母题详情 */
export const fetchMotherDetail = (code: string) =>
  defHttp.get<StudyMotherDetailVO>({
    url: `/api/v1/study/math/mother/${code}`,
    ...SILENT,
  });

// ------------------------------------------------------------------ 词表维护

/** 词条（对应 {@code WordListItemVO}） */
export interface StudyWordUnitVO {
  /** 单词业务 id */
  wordCode?: string;
  /** 线别：IN 课内 / OUT 课外 */
  lineType?: string;
  /** 教材版本 id */
  textbookVersionId?: string;
  /** 单元编码 */
  unitCode?: string;
  /** 单元内顺序 */
  unitOrder?: number;
  /** 释义 */
  meaning?: string;
  /** 音标 */
  phonetic?: string;
  /** 词性 */
  pos?: string;
  /** 例句（目标词已挖空） */
  contextSentence?: string;
  /*
   * 以下字段是**按孩子的记忆状态**，管理端一律不使用：
   * frequency / memoryStrength / decayLambda / feedbackCount / lastFeedback /
   * upgradePriority / nextReviewAt
   *
   * 另外：词表接口**不下发拼写**（拼写就是拼写题的答案，红线 R-01），
   * 管理端靠「释义 + 单元 + 业务 id」定位词条。
   */
}

/** 词表（对应 {@code WordListVO}） */
export interface StudyWordListVO {
  lineType?: string;
  textbookVersionId?: string;
  unitCode?: string;
  /** 过滤前总条数 */
  total?: number;
  /** 熟词过滤条数 */
  filteredKnownCount?: number;
  items?: StudyWordUnitVO[];
}

/**
 * 取词表。
 *
 * @param lineType 线别：IN（课内）/ OUT（课外）
 */
export const fetchWordLine = (lineType: string, pageNo = 1, pageSize = 100) =>
  defHttp.get<StudyWordListVO>({
    url: `/api/v1/study/word/line/${lineType}/list`,
    params: { pageNo, pageSize },
    ...SILENT,
  });

// ------------------------------------------------------------------ 语法点维护

/** 语法点（对应 {@code GrammarPointVO}） */
export interface StudyGrammarPointVO {
  /** 语法点业务 id */
  grammarCode?: string;
  /** 标题 */
  title?: string;
  /** 规则 */
  rule?: string;
  /** 例句 */
  example?: string;
  /** 反例 */
  counterExample?: string;
  /** 考试类型 */
  examType?: string;
  /** 所属考点 */
  knowledgePointCode?: string;
}

/** 语法点列表（对应 {@code GrammarPointListVO}） */
export interface StudyGrammarPointListVO {
  items?: StudyGrammarPointVO[];
  total?: number;
}

/** 语法点列表 */
export const fetchGrammarPointList = (examType?: string) =>
  defHttp.get<StudyGrammarPointListVO>({
    url: '/api/v1/study/grammar/points',
    params: { examType },
    ...SILENT,
  });

/** 语法点详情 */
export const fetchGrammarPoint = (code: string) =>
  defHttp.get<StudyGrammarPointVO>({
    url: `/api/v1/study/grammar/points/${code}`,
    ...SILENT,
  });

// ------------------------------------------------------------------ 拆解库维护

/** 拆解层（对应 {@code WritingDissectionLayerVO}） */
export interface StudyDissectionLayerVO {
  /** 层级编码 */
  layer?: string;
  /** 层级名 */
  layerName?: string;
  /** 小标题 */
  title?: string;
  /** 内容 */
  content?: string;
  /** 教学点 */
  teachingPoint?: string;
  /** 引导问题 */
  promptQuestion?: string;
}

/** 拆解条目（对应 {@code WritingDissectionVO}） */
export interface StudyDissectionVO {
  /** 拆解业务 id */
  dissectionCode?: string;
  /** 标题 */
  title?: string;
  /** 体裁 */
  genre?: string;
  /** 学段 */
  stageCode?: string;
  /** 分层内容 */
  layers?: StudyDissectionLayerVO[];
}

/** 拆解库（对应 {@code WritingDissectionListVO}） */
export interface StudyDissectionListVO {
  items?: StudyDissectionVO[];
  total?: number;
}

/** 拆解库列表 */
export const fetchDissectionList = (genre?: string, stageCode?: string) =>
  defHttp.get<StudyDissectionListVO>({
    url: '/api/v1/study/writing/dissections',
    params: { genre, stageCode },
    ...SILENT,
  });

/** 拆解详情 */
export const fetchDissection = (code: string) =>
  defHttp.get<StudyDissectionVO>({
    url: `/api/v1/study/writing/dissections/${code}`,
    ...SILENT,
  });

// ------------------------------------------------------------------ 孩子下拉（管理端选孩子用）

/** 孩子条目（对应 {@code ChildVO}，只取内容字段） */
export interface StudyManageChildVO {
  childId?: string;
  childCode?: string;
  nickname?: string;
  avatar?: string;
  status?: number;
}

/**
 * 取当前身份可见的孩子列表。
 *
 * <p>服务端已按关系表过滤（家长只看自己关联的孩子），
 * 管理端拿它做「上下文 / 母题」的选孩子下拉。</p>
 */
export const fetchManageChildren = () =>
  defHttp.get<StudyManageChildVO[]>({
    url: '/api/v1/study/children',
    ...SILENT,
  });

// ------------------------------------------------------------------ 教材版本

/** 教材版本（对应 {@code TextbookVersionVO}） */
export interface StudyTextbookVersionVO {
  versionId?: string;
  versionCode?: string;
  versionName?: string;
  publisher?: string;
  regionScope?: string;
  stageCode?: string;
  sortNo?: number;
}

/** 教材版本列表 */
export const fetchTextbookVersions = (stageCode?: string) =>
  defHttp.get<StudyTextbookVersionVO[]>({
    url: '/api/v1/study/textbook-versions',
    params: { stageCode },
    ...SILENT,
  });

// ------------------------------------------------------------------ 展示口径

/**
 * 线别取值域（对应后端 {@code WordLineType}）。
 *
 * <p>取值是 {@code IN} / {@code OUT}，<b>不是</b>「课内」「课外」——
 * 直接把中文当 lineType 传给后端会被判为非法。</p>
 */
export const WORD_LINE_OPTIONS = [
  { label: '课内', value: 'IN' },
  { label: '课外', value: 'OUT' },
];

/** 线别 → 中文标签 */
export function wordLineLabel(lineType?: string): string {
  const hit = WORD_LINE_OPTIONS.find((item) => item.value === lineType);
  return hit ? hit.label : '—';
}

/**
 * 语法点的考试类型取值域（对应后端 {@code GrammarExamType}）。
 *
 * <p>后端会校验取值，传别的值直接 400。</p>
 */
export const GRAMMAR_EXAM_TYPES = ['单选', '完形', '语法填空', '书面表达'];

