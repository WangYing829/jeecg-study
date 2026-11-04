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

// ------------------------------------------------------------------ 孩子端（学习端 kid 线）
//
// 红线口径：
//   · 孩子身份来自会话（JWT），后端按登录账号反查孩子档案，
//     前端拿到的 childId / childCode 只用于提交与展示，不作为数据范围的唯一依据（ADR-018）；
//   · 错题详情与变式清单不下发答案与讲解，页面也就无从提前展示（R-01）；
//   · 响应里没有同类横向比较字段，也不含任何他人标识（R-04）。

/** 当前登录孩子信息 */
export interface StudyCurrentKidVO {
  childId?: string;
  childCode?: string;
  nickname?: string;
  gradeCode?: string;
}

/** 今日练习统计（只给客观计数，不给分数，也不与任何人横向比较） */
export interface StudyTodayStatsVO {
  date?: string;
  totalCount?: number;
  correctCount?: number;
  wrongCount?: number;
  durationMinutes?: number;
  moduleCount?: number;
}

/** 错题池条目 */
export interface StudyErrorPoolItemVO {
  errorCode?: string;
  sourceModule?: string;
  contentType?: string;
  contentId?: string;
  knowledgePointCode?: string;
  subjectCode?: string;
  errorCause?: string;
  judgeSource?: string;
  masteryState?: number;
  correctStreak?: number;
  reviewStatus?: number;
  nextReviewAt?: string;
  archived?: boolean;
}

/** 错题详情（不下发答案与讲解） */
export interface StudyErrorDetailVO {
  errorCode?: string;
  sourceModule?: string;
  contentType?: string;
  contentId?: string;
  knowledgePointCode?: string;
  knowledgePointName?: string;
  subjectCode?: string;
  errorCause?: string;
  errorCauseName?: string;
  judgeSource?: string;
  masteryState?: number;
  correctStreak?: number;
  reviewStatus?: number;
  nextReviewAt?: string;
  lastReviewAt?: string;
  archived?: boolean;
  createTime?: string;
  copyrightType?: string;
  sourceRef?: string;
}

/** 错题变式 */
export interface StudyErrorVariantVO {
  variantCode?: string;
  questionCode?: string;
  motherCode?: string;
  layer?: number;
  layerName?: string;
  stem?: string;
  source?: string;
}

/** 错题变式清单 */
export interface StudyErrorVariantListVO {
  errorCode?: string;
  motherCode?: string;
  variantAvailable?: boolean;
  degradeType?: string;
  variants?: StudyErrorVariantVO[];
}

/** 复习队列条目 */
export interface StudyReviewQueueItemVO {
  errorCode?: string;
  sourceModule?: string;
  contentType?: string;
  contentId?: string;
  orderWeight?: number;
  queueDate?: string;
  knowledgePointCode?: string;
}

/** 完成一项复习的结果（由服务端推进排期） */
export interface StudyReviewCompleteVO {
  errorCode?: string;
  correctStreak?: number;
  masteryState?: number;
  archived?: boolean;
  nextReviewAt?: string;
  counted?: boolean;
  message?: string;
}

/** 错因分布：错因 → 次数 */
export type StudyErrorCauseStatistics = Record<string, number>;

/** 错题入池结果 */
export interface StudyErrorReportVO {
  errorCode?: string;
  nextReviewAt?: string;
  duplicated?: boolean;
}

/** 单词条目（服务端不下发拼写，拼写就是拼写题的答案，R-01） */
export interface StudyWordItemVO {
  wordCode?: string;
  lineType?: string;
  textbookVersionId?: string;
  unitCode?: string;
  unitOrder?: number;
  meaning?: string;
  phonetic?: string;
  pos?: string;
  contextSentence?: string;
  frequency?: number;
  nextReviewAt?: string;
}

/** 课内单元 */
export interface StudyWordUnitVO {
  textbookVersionId?: string;
  unitCode?: string;
  unitOrder?: number;
  wordCount?: number;
  lineType?: string;
}

/** 词表 */
export interface StudyWordListVO {
  lineType?: string;
  textbookVersionId?: string;
  unitCode?: string;
  total?: number;
  filteredKnownCount?: number;
  items?: StudyWordItemVO[];
}

/** 到期复习词 */
export interface StudyWordDueVO {
  lineType?: string;
  totalDue?: number;
  filteredKnownCount?: number;
  items?: StudyWordItemVO[];
  upgradeItems?: StudyWordItemVO[];
}

/** 背诵统计 */
export interface StudyWordStatsVO {
  lineType?: string;
  totalWords?: number;
  queueWords?: number;
  filteredKnownCount?: number;
  learnedWords?: number;
  dueWords?: number;
  reviewPending?: number;
  streakDays?: number;
}

/** 熟词测试结果 */
export interface StudyWordKnownTestVO {
  lineType?: string;
  markedCount?: number;
  filteredKnownCount?: number;
  queueWordCount?: number;
}

/** 单元闯关结果 */
export interface StudyWordUnitCompleteVO {
  textbookVersionId?: string;
  unitCode?: string;
  learnedWordCount?: number;
  streakDays?: number;
  pureMode?: boolean;
  gameEnabled?: boolean;
  rewardVisible?: boolean;
  streakVisible?: boolean;
  rewardText?: string;
}

/** 当前登录孩子信息（身份来自会话） */
export const fetchCurrentKid = () =>
  defHttp.get<StudyCurrentKidVO>({
    url: '/api/v1/study/kid/me',
    ...SILENT,
  });

/** 今日练习统计 */
export const fetchTodayStats = () =>
  defHttp.get<StudyTodayStatsVO>({
    url: '/api/v1/study/practice/stats/today',
    ...SILENT,
  });

/** 今日复习队列（跨模块统一） */
export const fetchReviewQueue = () =>
  defHttp.get<StudyReviewQueueItemVO[] | { items?: StudyReviewQueueItemVO[] }>({
    url: '/api/v1/study/review/queue',
    ...SILENT,
  });

/** 未来 7 天复习排期预览 */
export const fetchReviewUpcoming = () =>
  defHttp.get<StudyReviewQueueItemVO[] | { items?: StudyReviewQueueItemVO[] }>({
    url: '/api/v1/study/review/upcoming',
    ...SILENT,
  });

/** 完成一项复习（推进排期；答错必须带错因，否则服务端返回 4001） */
export const completeReviewItem = (
  code: string,
  payload: { correct: boolean; errorCause?: string; reviewDate?: string },
) =>
  defHttp.post<StudyReviewCompleteVO>({
    url: `/api/v1/study/review/queue/${code}/complete`,
    data: payload,
    ...SILENT,
  });

/** 错题列表（统一错题本） */
export const fetchErrorPool = (childCode?: string) =>
  defHttp.get<StudyErrorPoolItemVO[] | { items?: StudyErrorPoolItemVO[] }>({
    url: '/api/v1/study/error-pool',
    params: { childCode },
    ...SILENT,
  });

/** 错题详情（不下发答案与讲解） */
export const fetchErrorDetail = (code: string) =>
  defHttp.get<StudyErrorDetailVO>({
    url: `/api/v1/study/error-pool/${code}`,
    ...SILENT,
  });

/** 错题变式清单 */
export const fetchErrorVariants = (code: string) =>
  defHttp.get<StudyErrorVariantListVO>({
    url: `/api/v1/study/error-pool/${code}/variants`,
    ...SILENT,
  });

/** 错因分布统计 */
export const fetchErrorStatistics = (childCode?: string) =>
  defHttp.get<StudyErrorCauseStatistics>({
    url: '/api/v1/study/error-pool/statistics',
    params: { childCode },
    ...SILENT,
  });

/** 错题入池（拍照记一道错题 / 各模块判错后调用） */
export const reportError = (payload: {
  childId?: string;
  sourceModule?: string;
  contentType?: string;
  contentId?: string;
  knowledgePointCode?: string;
  subjectCode?: string;
  errorCause?: string;
  rawAnswer?: string;
  judgeSource?: string;
  sourceRef?: string;
  copyrightType?: string;
}) =>
  defHttp.post<StudyErrorReportVO>({
    url: '/api/v1/study/error-pool',
    data: payload,
    ...SILENT,
  });

/** 课内单元列表 */
export const fetchWordUnits = (lineType: string) =>
  defHttp.get<StudyWordUnitVO[] | { items?: StudyWordUnitVO[] }>({
    url: `/api/v1/study/word/line/${lineType}/units`,
    ...SILENT,
  });

/** 词表（按单元取） */
export const fetchWordList = (
  lineType: string,
  params?: { textbookVersionId?: string; unitCode?: string },
) =>
  defHttp.get<StudyWordListVO>({
    url: `/api/v1/study/word/line/${lineType}/list`,
    params,
    ...SILENT,
  });

/** 到期复习词 */
export const fetchWordDue = (lineType?: string) =>
  defHttp.get<StudyWordDueVO>({
    url: '/api/v1/study/word/due',
    params: { lineType },
    ...SILENT,
  });

/** 背诵统计 */
export const fetchWordStats = (lineType?: string) =>
  defHttp.get<StudyWordStatsVO>({
    url: '/api/v1/study/word/stats',
    params: { lineType },
    ...SILENT,
  });

/** 熟词测试（把认识的词标记出来，过滤掉后进入背诵队列） */
export const submitKnownTest = (payload: { lineType: string; wordCodes: string[] }) =>
  defHttp.post<StudyWordKnownTestVO>({
    url: '/api/v1/study/word/known-test',
    data: payload,
    ...SILENT,
  });

/** 进入课内单元 */
export const enterWordUnit = (payload: {
  childCode?: string;
  textbookVersionId?: string;
  unitCode?: string;
  pureMode?: boolean;
}) =>
  defHttp.post<StudyWordListVO>({
    url: '/api/v1/study/word/unit/enter',
    data: payload,
    ...SILENT,
  });

/** 完成单元闯关 */
export const completeWordUnit = (payload: {
  childCode?: string;
  textbookVersionId?: string;
  unitCode?: string;
  pureMode?: boolean;
}) =>
  defHttp.post<StudyWordUnitCompleteVO>({
    url: '/api/v1/study/word/unit/complete',
    data: payload,
    ...SILENT,
  });

// ------------------------------------------------------------------ 数学线（母题模块）
//
// 红线口径：
//   · 变式与母题的响应里都没有答案字段，答案只在服务端参与判定（R-01）；
//   · 判对错只用确定性规则，判定来源由服务端在 judgeSource 里下发，AI 只做解释层（R-02）；
//   · 进度以「母题类」为单位，只给当前孩子自己的客观状态（R-04）。
//
// 身份口径：孩子的身份来自会话，后端按登录账号反查孩子档案；
// 前端不传 childId 作为数据范围的唯一依据（ADR-018）。

/** 母题列表条目 */
export interface StudyMathMotherListItemVO {
  motherCode: string;
  title?: string;
  subjectCode?: string;
  knowledgePointCode?: string;
  outlineWeight?: number;
  orderNo?: number;
  mastery?: number;
  attemptCount?: number;
  errorCount?: number;
  consecutiveErrors?: number;
  variantCount?: number;
  passed?: boolean;
}

/** 条件类型（一类取值下的若干情形） */
export interface StudyMathConditionTypeVO {
  key?: string;
  label?: string;
  variants?: string[];
  value?: number;
}

/** 母题步骤 */
export interface StudyMathStepVO {
  index?: number;
  title?: string;
  formula?: string;
  note?: string;
}

/** 母题详情（五段结构） */
export interface StudyMathMotherDetailVO {
  motherCode: string;
  title?: string;
  conditionTypes?: StudyMathConditionTypeVO[];
  keyRelation?: string;
  steps?: StudyMathStepVO[];
  pitfalls?: string[];
  subjectCode?: string;
  knowledgePointCode?: string;
  passVariantCount?: number;
  orderNo?: number;
  status?: number;
  sectionOrder?: string[];
  keyRelationHighlighted?: boolean;
  highlightSection?: string;
}

/** 变式（响应里没有答案字段，R-01） */
export interface StudyMathVariantVO {
  variantCode: string;
  motherCode?: string;
  layer?: number;
  layerName?: string;
  stem?: string;
  generatedBy?: string;
  answerTolerance?: number;
  params?: Record<string, any>;
}

/** 步骤级错因打标 */
export interface StudyMathStepTagVO {
  stepIndex?: number;
  errorCause?: string;
}

/** 变式作答结果 */
export interface StudyMathVariantAnswerResultVO {
  variantCode?: string;
  correct?: boolean;
  /** 判定依据（人可读） */
  basis?: string;
  /** 判定来源：rule / rule+model（R-02） */
  judgeSource?: string;
  errorCode?: string;
  stepTags?: StudyMathStepTagVO[];
  duplicated?: boolean;
}

/** 过关判定结果 */
export interface StudyMathPassResultVO {
  motherCode?: string;
  passed?: boolean;
  variantPassed?: boolean;
  variantCorrectCount?: number;
  variantRequiredCount?: number;
  narrativePassed?: boolean;
  hitKeywords?: string[];
  missingKeywords?: string[];
  followUpQuestion?: string;
  hint?: string;
}

/** 口述任务说明（不含答案字段） */
export interface StudyMathNarrativeBriefVO {
  motherCode?: string;
  title?: string;
  narrativeRequired?: boolean;
  keyPointCount?: number;
  prompt?: string;
}

/** 口述判定结果 */
export interface StudyMathNarrativeResultVO {
  motherCode?: string;
  narrativePassed?: boolean;
  hitKeywords?: string[];
  missingKeywords?: string[];
  followUpQuestion?: string;
}

/** 关卡进度（单位是母题类，不是题量） */
export interface StudyMathProgressVO {
  masteredTypeCount?: number;
  totalTypeCount?: number;
  progressText?: string;
  masteredMotherCodes?: string[];
}

/** 母题关卡列表 */
export const fetchMathMotherList = () =>
  defHttp.get<StudyMathMotherListItemVO[]>({
    url: '/api/v1/study/math/mother/list',
    ...SILENT,
  });

/** 母题详情 */
export const fetchMathMotherDetail = (code: string) =>
  defHttp.get<StudyMathMotherDetailVO>({
    url: `/api/v1/study/math/mother/${code}`,
    ...SILENT,
  });

/** 变式列表 */
export const fetchMathMotherVariants = (code: string) =>
  defHttp.get<StudyMathVariantVO[]>({
    url: `/api/v1/study/math/mother/${code}/variants`,
    ...SILENT,
  });

/** 口述任务说明 */
export const fetchMathMotherNarrative = (code: string) =>
  defHttp.get<StudyMathNarrativeBriefVO>({
    url: `/api/v1/study/math/mother/${code}/narrative`,
    ...SILENT,
  });

/** 关卡进度 */
export const fetchMathProgress = () =>
  defHttp.get<StudyMathProgressVO>({
    url: '/api/v1/study/math/progress',
    ...SILENT,
  });

/** 变式作答（服务端判对错 + 步骤级错因打标） */
export const submitMathVariantAnswer = (
  code: string,
  variantCode: string,
  payload: {
    answer: string;
    errorCause?: string;
    stepTags?: StudyMathStepTagVO[];
    durationMs?: number;
  },
) =>
  defHttp.post<StudyMathVariantAnswerResultVO>({
    url: `/api/v1/study/math/mother/${code}/variants/${variantCode}/answer`,
    data: payload,
    ...SILENT,
  });

/** 提交过关判定（变式全对且口述讲出关键关系才过关） */
export const submitMathMotherPass = (
  code: string,
  payload: {
    variantResults?: { variantCode: string; answer: string }[];
    narrative?: string;
  },
) =>
  defHttp.post<StudyMathPassResultVO>({
    url: `/api/v1/study/math/mother/${code}/pass`,
    data: payload,
    ...SILENT,
  });

/** 提交口述（返回命中 / 缺失关键词与一条追问） */
export const submitMathMotherNarrative = (code: string, payload: { narrative: string }) =>
  defHttp.post<StudyMathNarrativeResultVO>({
    url: `/api/v1/study/math/mother/${code}/narrative`,
    data: payload,
    ...SILENT,
  });

// ------------------------------------------------------------------ 语法线补充

/** 句子变形的一种形态 */
export interface StudyGrammarTransformVO {
  kind?: string;
  name?: string;
  text?: string;
  usage?: string;
  wrong?: boolean;
}

/** 句子变形结果 */
export interface StudyGrammarTransformResultVO {
  meaning?: string;
  grammarCode?: string;
  source?: string;
  variants?: StudyGrammarTransformVO[];
  explainStatus?: string;
  hint?: string;
}

/** 收藏馆条目 */
export interface StudyGrammarCollectionItemVO {
  errorCode?: string;
  contentId?: string;
  errorCause?: string;
  originalSentence?: string;
  correctedSentence?: string;
  judgeSource?: string;
  masteryState?: number;
  archived?: boolean;
  nextReviewAt?: string;
}

/** 收藏馆结果 */
export interface StudyGrammarCollectionResultVO {
  items?: StudyGrammarCollectionItemVO[];
  total?: number;
}

/** 句子变形器 */
export const transformGrammarSentence = (payload: { zhMeaning: string; grammarCode?: string }) =>
  defHttp.post<StudyGrammarTransformResultVO>({
    url: '/api/v1/study/grammar/transform',
    data: payload,
    ...SILENT,
  });

/** 收藏馆列表 */
export const fetchGrammarCollection = (errorCause?: string) =>
  defHttp.get<StudyGrammarCollectionResultVO>({
    url: '/api/v1/study/grammar/collection',
    params: { errorCause },
    ...SILENT,
  });

// ------------------------------------------------------------------ 口述确认

/** 口述反馈结果（不给答案，只给命中 / 缺失关键词与一条追问，R-01） */
export interface StudyOralResultVO {
  contentId?: string;
  motherCode?: string;
  passed?: boolean;
  matchedKeywords?: string[];
  missingKeywords?: string[];
  followUpQuestion?: string;
  motherPassed?: boolean;
}

/** 提交口述 */
export const submitPracticeOral = (contentId: string, payload: { speechText: string }) =>
  defHttp.post<StudyOralResultVO>({
    url: `/api/v1/study/practice/${contentId}/oral`,
    data: payload,
    ...SILENT,
  });
