import { defHttp } from '/@/utils/http/axios';

/**
 * jeecg-study 前端接口封装（学习端 / 孩子端 / 家长端）。
 *
 * <p>接口根统一为 {@code /api/v1/study}（见《接口设计说明书》）。</p>
 *
 * <p><b>红线约束</b>：</p>
 * <ul>
 *   <li>取题接口<b>不返回</b>答案字段，前端拿不到也就无法提前展示（R-01）；</li>
 *   <li>讲解内容必须<b>先作答</b>再取（4003），页面按状态机渲染（R-01 / §10.6）；</li>
 *   <li>护眼休息阻断标志由服务端下发，前端不自行计时（R-03）；</li>
 *   <li>家长端报告接口全部只读，只含当前孩子自己的数据，不涉及其他人（R-04）。</li>
 * </ul>
 */

/** 统一的请求选项：错误提示由页面自己处理，避免弹窗打断学习流 */
const SILENT = { errorMessageMode: 'none' } as const;

// ------------------------------------------------------------------ 通用类型

export interface StudyChildVO {
  childId: string;
  childCode: string;
  nickname: string;
  gradeCode?: string;
  relationLabel?: string;
}

export interface StudyAnswerEntryVO {
  type?: string;
  unit?: string;
  placeholder?: string;
}

export interface StudyPracticeQuestionVO {
  contentId: string;
  moduleType?: string;
  questionType?: string;
  subjectCode?: string;
  knowledgePointCode?: string;
  stem?: string;
  /** 选项 JSON 文本 */
  options?: string;
  difficulty?: number;
  answerEntry?: StudyAnswerEntryVO;
  errorCode?: string;
}

export interface StudyJudgeDetailVO {
  steps?: { tag?: string; note?: string }[];
}

export interface StudyExplainVO {
  type?: string;
  text?: string;
}

export interface StudyAnswerResultVO {
  contentId: string;
  correct?: boolean;
  /** 判定来源：rule / rule+model（AI 只做解释层，R-02） */
  source?: string;
  /** 判定依据（人可读） */
  basis?: string;
  detail?: StudyJudgeDetailVO;
  explain?: StudyExplainVO;
  explainStatus?: string;
  errorId?: string;
  nextReviewAt?: string;
  masteryState?: number;
}

export interface StudyWordPracticeVO {
  wordCode: string;
  lineType?: string;
  unitCode?: string;
  meaning?: string;
  phonetic?: string;
  pos?: string;
  /** 例句（目标词已挖空） */
  contextSentence?: string;
  nextReviewAt?: string;
}

/** 护眼会话状态（服务端下发，R-03） */
export interface StudyEyeSessionVO {
  sessionId?: string;
  /** 是否需要休息；为 true 时前端显示阻断遮罩 */
  restRequired?: boolean;
  /** 休息提醒文案（含可执行的具体动作） */
  restPrompt?: string;
  /** 蓝光过滤是否开启（已落库，刷新后保持） */
  blueLightOn?: boolean;
  /** 护眼提醒间隔（分钟，家长端配置，随会话下发） */
  eyeIntervalMin?: number;
  lastHeartbeatAt?: string;
}

export interface StudyGrammarPointVO {
  grammarCode: string;
  title?: string;
  rule?: string;
  example?: string;
  counterExample?: string;
  examType?: string;
  knowledgePointCode?: string;
}

export interface StudyContextVO {
  contextId?: string;
  childCode?: string;
  /** 护眼提醒间隔（分钟） */
  eyeIntervalMin?: number;
  configured?: boolean;
}

// ------------------------------------------------------------------ 家长端报告（只读）

export interface StudyParentEyeUsageVO {
  todayMinutes?: number;
  weekMinutes?: number;
  avgSessionMinutes?: number;
  restCount?: number;
  restConfirmedCount?: number;
}

export interface StudyParentStreakVO {
  days?: number;
  todayCompleted?: boolean;
}

export interface StudyParentMasteryVO {
  mastered?: number;
  familiar?: number;
  learning?: number;
  unknown?: number;
}

export interface StudyParentErrorCauseVO {
  cause?: string;
  causeName?: string;
  count?: number;
}

export interface StudyParentWeakPointVO {
  pointCode?: string;
  pointName?: string;
  masteryState?: number;
  errorCount?: number;
}

export interface StudyParentWritingTrendVO {
  date?: string;
  writingCount?: number;
}

export interface StudyParentOverviewVO {
  childId?: string;
  childCode?: string;
  nickname?: string;
  eyeUsage?: StudyParentEyeUsageVO;
  streak?: StudyParentStreakVO;
  mastery?: StudyParentMasteryVO;
  errorTop?: StudyParentErrorCauseVO[];
  weakPoints?: StudyParentWeakPointVO[];
  writingTrend?: StudyParentWritingTrendVO[];
}

export interface StudyParentDailyItemVO {
  date?: string;
  eyeMinutes?: number;
  restCount?: number;
  errorCount?: number;
  writingCount?: number;
}

export interface StudyParentWeeklyVO {
  childCode?: string;
  fromDate?: string;
  toDate?: string;
  activeDays?: number;
  totalEyeMinutes?: number;
  totalRestCount?: number;
  totalErrorCount?: number;
  totalWritingCount?: number;
  days?: StudyParentDailyItemVO[];
}

export interface StudyParentErrorReportVO {
  childCode?: string;
  total?: number;
  causes?: StudyParentErrorCauseVO[];
  weakPoints?: StudyParentWeakPointVO[];
}

// ------------------------------------------------------------------ 学习端

/** 取下一题（不返回答案字段，R01-1） */
export const fetchPracticeQuestion = (type: string) =>
  defHttp.get<StudyPracticeQuestionVO>({
    url: `/api/v1/study/practice/${type}/next`,
    ...SILENT,
  });

/** 取讲解（未作答时服务端返回 4003，R01-2） */
export const fetchPracticeExplain = (contentId: string) =>
  defHttp.get<{ type?: string; text?: string }>({
    url: `/api/v1/study/practice/${contentId}/explanation`,
    ...SILENT,
  });

/** 提交作答 */
export const submitPracticeAnswer = (payload: {
  sessionId?: string;
  contentId: string;
  answer: string;
  durationMs?: number;
  errorCause?: string;
  contentType?: string;
}) =>
  defHttp.post<StudyAnswerResultVO>({
    url: '/api/v1/study/practice/answer',
    data: payload,
    ...SILENT,
  });

/** 背单词：取下一个词（拼写不下发，避免替孩子作答） */
export const fetchWordPractice = (lineType?: string) =>
  defHttp.get<StudyWordPracticeVO>({
    url: '/api/v1/study/word/practice/next',
    params: { lineType },
    ...SILENT,
  });

/** 背单词：提交作答 */
export const submitWordPractice = (payload: { wordCode: string; answer?: string; lineType?: string }) =>
  defHttp.post<Record<string, any>>({
    url: '/api/v1/study/word/practice',
    data: payload,
    ...SILENT,
  });

/** 语法点列表 */
export const fetchGrammarPoints = (examType?: string) =>
  defHttp.get<{ items?: StudyGrammarPointVO[] }>({
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

// ------------------------------------------------------------------ 护眼（孩子端心跳通道）

/** 开始用眼会话 */
export const startEyeSession = () =>
  defHttp.post<StudyEyeSessionVO>({
    url: '/api/v1/study/eye/session/start',
    ...SILENT,
  });

/** 上报心跳：只说明「还在 / 已离开」，不上报时长（R-03） */
export const sendEyeHeartbeat = (sessionId: string, payload: { active?: boolean; blueLight?: boolean } = {}) =>
  defHttp.post<StudyEyeSessionVO>({
    url: `/api/v1/study/eye/session/${sessionId}/heartbeat`,
    data: payload,
    ...SILENT,
  });

/** 确认休息：清除阻断遮罩的唯一入口（R-03） */
export const confirmEyeRest = (sessionId: string) =>
  defHttp.post<StudyEyeSessionVO>({
    url: `/api/v1/study/eye/session/${sessionId}/rest`,
    ...SILENT,
  });

/** 查询用眼会话状态 */
export const fetchEyeSession = (sessionId: string) =>
  defHttp.get<StudyEyeSessionVO>({
    url: `/api/v1/study/eye/session/${sessionId}`,
    ...SILENT,
  });

// ------------------------------------------------------------------ 家长端

/** 已关联的孩子列表 */
export const fetchChildren = () =>
  defHttp.get<StudyChildVO[]>({
    url: '/api/v1/study/children',
    ...SILENT,
  });

/** 家长端可反馈的作文摘要（不含正文） */
export interface StudyParentWritingDraftVO {
  /** 初稿业务ID */
  writingCode: string;
  /** 题目 */
  title?: string;
  /** 提交时间 */
  createTime?: string;
  /** 所属孩子业务ID */
  childCode?: string;
}

/** 家长端总览（用眼 / 连续天数 / 掌握度 / 错因 / 薄弱考点 / 写作趋势） */
export const fetchParentOverview = (childCode?: string) =>
  defHttp.get<StudyParentOverviewVO>({
    url: '/api/v1/study/parent/report/overview',
    params: { childCode },
    ...SILENT,
  });

/** 家长端周报（近 7 天逐日） */
export const fetchParentWeekly = (childCode?: string) =>
  defHttp.get<StudyParentWeeklyVO>({
    url: '/api/v1/study/parent/report/weekly',
    params: { childCode },
    ...SILENT,
  });

/** 家长端错因分布与薄弱考点 */
export const fetchParentErrors = (childCode?: string) =>
  defHttp.get<StudyParentErrorReportVO>({
    url: '/api/v1/study/parent/report/errors',
    params: { childCode },
    ...SILENT,
  });

/**
 * 家长可反馈的最近作文（初稿）列表。
 *
 * <p>对应写作域端点 {@code GET /api/v1/study/writing/parent/drafts}：
 * 只返回关系表交集内孩子的初稿摘要（业务ID / 题目 / 提交时间），<b>不含正文</b>。</p>
 */
export const fetchParentWritingDrafts = (childCode?: string) =>
  defHttp.get<StudyParentWritingDraftVO[]>({
    url: '/api/v1/study/writing/parent/drafts',
    params: { childCode },
    ...SILENT,
  });

/**
 * 提交一句话反馈（家长端唯一的写入口之一）。
 *
 * <p>按 ADR-025，家长反馈归<b>写作域</b>，端点在 {@code /api/v1/study/writing/parent/feedback}；
 * 反馈必须绑定具体作品（F-PAR-02：某篇作文 / 某次练习），
 * 所以入参是 {@code targetCode}（初稿业务ID），不是 childCode。</p>
 */
export const submitParentFeedback = (payload: { targetCode: string; content: string }) =>
  defHttp.post<string>({
    url: '/api/v1/study/writing/parent/feedback',
    data: {
      targetType: 'WRITING',
      targetCode: payload.targetCode,
      content: payload.content,
    },
    ...SILENT,
  });

/** 当前上下文（护眼提醒间隔等） */
export const fetchContext = (childCode?: string) =>
  defHttp.get<StudyContextVO>({
    url: '/api/v1/study/context/current',
    params: { childCode },
    ...SILENT,
  });
