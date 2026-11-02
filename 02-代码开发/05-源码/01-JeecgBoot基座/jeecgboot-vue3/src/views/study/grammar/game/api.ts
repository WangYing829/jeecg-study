import { defHttp } from '/@/utils/http/axios';

/**
 * 英语语法 · 趣味玩法接口封装（F-GRM-04）+ 语境例句库（F-GRM-05）。
 *
 * <p>接口根统一为 {@code /api/v1/study}（见《接口设计说明书》§3.4，接口编号 100–106）。</p>
 *
 * <p><b>产品口径</b>：</p>
 * <ul>
 *   <li><b>取题不下发答案</b>：句子侦探只给四句话，变形闯关只给中文意思，
 *       讲解三件套（规则 / 正例 / 反例 / 改法）一律等孩子提交判定之后才由服务端下发（R-01）；</li>
 *   <li><b>判定由服务端给出</b>：{@code judgeSource} 恒为 {@code rule}，
 *       前端不自己判断对错，也不做任何外部请求（R-02）；</li>
 *   <li><b>身份来自会话</b>：本组端点不接受前端传来的 {@code childId}，
 *       数据范围由服务端按会话身份强制隔离（R-04）。</li>
 * </ul>
 *
 * <p>本文件是<b>语法玩法域专用</b>的接口封装，独立成文件，
 * 避免在学习端共用接口文件里堆积无关模块的接口。</p>
 */

/** 统一的请求选项：错误提示由页面自己处理，避免弹窗打断学习流 */
const SILENT = { errorMessageMode: 'none' } as const;

// ------------------------------------------------------------------ 语境例句库（F-GRM-05）

/** 语境库里的一个例句 */
export interface StudyGrammarContextItemVO {
  /** 例句业务ID */
  sentenceCode?: string;
  /** 例句正文 */
  sentence?: string;
  /** 中文意思 */
  zhMeaning?: string;
  /** 语境类型：TEXTBOOK / EXAM / ORIGINAL */
  contextType?: string;
  /** 语境类型中文名 */
  contextTypeName?: string;
  /** 关联类型：MAIN 主例句 / EXTEND 拓展用法 */
  relationType?: string;
  /** 用法说明 */
  usageNote?: string;
  /** 出处 */
  sourceRef?: string;
  /** 关联语法点业务ID */
  grammarCode?: string;
}

/** 语境例句查询结果 */
export interface StudyGrammarContextResultVO {
  /** 词条业务ID（按词查时有值） */
  wordCode?: string;
  /** 拼写（按词查时有值） */
  spelling?: string;
  /** 释义（按词查时有值） */
  meaning?: string;
  /** 语法点业务ID（按语法点查时有值） */
  grammarCode?: string;
  /** 背单词域那一条例句 */
  wordExampleSentence?: string;
  /** 例句总数 */
  total?: number;
  /** 例句列表 */
  items?: StudyGrammarContextItemVO[];
}

/** 语境查询入参：{@code wordCode} 与 {@code grammarCode} 至少给一个 */
export interface StudyGrammarContextQuery {
  /** 词条业务ID */
  wordCode?: string;
  /** 语法点业务ID */
  grammarCode?: string;
  /** 语境类型：TEXTBOOK / EXAM / ORIGINAL */
  contextType?: string;
  /** 返回条数 */
  limit?: number;
}

// ------------------------------------------------------------------ 句子侦探（F-GRM-04）

/** 句子侦探的一回合里的第 N 句 */
export interface StudyGrammarDetectiveSentenceVO {
  /** 位置（1 起） */
  position?: number;
  /** 句子正文 */
  text?: string;
  /** 例句业务ID（错句没有） */
  sentenceCode?: string;
}

/** 句子侦探取题结果：只给四句话，不给哪句错 */
export interface StudyGrammarDetectiveRoundVO {
  /** 回合ID（题目业务ID） */
  roundId?: string;
  /** 关联语法点业务ID */
  grammarCode?: string;
  /** 本回合的句子 */
  sentences?: StudyGrammarDetectiveSentenceVO[];
  /** 句子总数 */
  total?: number;
}

/** 句子侦探判定结果 */
export interface StudyGrammarDetectiveJudgeVO {
  /** 回合ID */
  roundId?: string;
  /** 是否找对了 */
  correct?: boolean;
  /** 孩子指出的是第几句 */
  pickedIndex?: number;
  /** 错的是第几句 */
  wrongPosition?: number;
  /** 错句正文 */
  wrongSentence?: string;
  /** 最小讲解 */
  rule?: string;
  /** 同类例句 */
  example?: string;
  /** 反例 */
  counterExample?: string;
  /** 改正后的句子 */
  fix?: string;
  /** 判定来源（恒为 rule） */
  judgeSource?: string;
  /** 进入统一错题池后的错题业务ID */
  errorCode?: string;
}

/** 句子侦探判定入参 */
export interface StudyGrammarDetectiveJudgeRequest {
  /** 回合ID */
  roundId?: string;
  /** 孩子指出错的是第几句（1 起） */
  pickedIndex?: number;
  /** 本关耗时（毫秒，可空） */
  durationMs?: number;
}

// ------------------------------------------------------------------ 变形闯关（F-GRM-04）

/** 语法变形闯关取题结果：只给中文意思与任务说明 */
export interface StudyGrammarTransformRoundVO {
  /** 回合ID（题目业务ID） */
  roundId?: string;
  /** 关联语法点业务ID */
  grammarCode?: string;
  /** 中文意思（这一关要写什么） */
  meaning?: string;
  /** 任务说明 */
  prompt?: string;
  /** 目标表达所在的语境例句业务ID */
  targetSentenceCode?: string;
}

/** 语法变形闯关判定结果 */
export interface StudyGrammarTransformJudgeVO {
  /** 回合ID */
  roundId?: string;
  /** 是否过关 */
  correct?: boolean;
  /** 判定来源（恒为 rule） */
  judgeSource?: string;
  /** 最小讲解（答错时给） */
  rule?: string;
  /** 同类例句（答错时给） */
  example?: string;
  /** 反例（答错时给） */
  counterExample?: string;
  /** 改正后的句子（答错时给） */
  fix?: string;
  /** 参考表达（过关后给） */
  referenceText?: string;
  /** 提示（没过关时给） */
  hint?: string;
  /** 进入统一错题池后的错题业务ID */
  errorCode?: string;
}

/** 语法变形闯关判定入参 */
export interface StudyGrammarTransformJudgeRequest {
  /** 回合ID */
  roundId?: string;
  /** 孩子写的英文句子 */
  sentence?: string;
  /** 本关耗时（毫秒，可空） */
  durationMs?: number;
}

// ------------------------------------------------------------------ 错句收藏馆与进度（F-GRM-04）

/** 错句收藏馆条目 */
export interface StudyGrammarGameCollectionItemVO {
  /** 错题业务ID */
  errorCode?: string;
  /** 错句原文 */
  originalSentence?: string;
  /** 改正后的句子 */
  correctedSentence?: string;
  /** 错因 */
  errorCause?: string;
  /** 判定来源（恒为 rule） */
  judgeSource?: string;
  /** 是否已移出复习队列 */
  archived?: boolean;
  /** 下次复习日期 */
  nextReviewAt?: string;
}

/** 错句收藏馆 */
export interface StudyGrammarGameCollectionResultVO {
  /** 错句列表 */
  items?: StudyGrammarGameCollectionItemVO[];
  /** 错句总数 */
  total?: number;
}

/** 一个玩法的闯关进度：只跟自己比 */
export interface StudyGrammarGameTypeProgressVO {
  /** 玩法类型 */
  gameType?: string;
  /** 玩法类型中文名 */
  gameTypeName?: string;
  /** 题库里的关卡总数 */
  totalCount?: number;
  /** 已通过的关卡数 */
  passedCount?: number;
  /** 作答次数 */
  answeredCount?: number;
  /** 最近一次作答时间 */
  lastPlayedAt?: string;
}

/** 闯关进度总览 */
export interface StudyGrammarGameProgressVO {
  /** 各玩法的进度 */
  items?: StudyGrammarGameTypeProgressVO[];
}

// ------------------------------------------------------------------ 接口

/** 语境例句库（与背单词共用同一批例句：课文 / 真题 / 原声素材） */
export const fetchGrammarContext = (query: StudyGrammarContextQuery) =>
  defHttp.get<StudyGrammarContextResultVO>({
    url: '/api/v1/study/grammar/context',
    params: query,
    ...SILENT,
  });

/** 句子侦探取题（四句话，只给句子不给答案） */
export const fetchGrammarDetectiveNext = () =>
  defHttp.get<StudyGrammarDetectiveRoundVO>({
    url: '/api/v1/study/grammar/game/detective/next',
    ...SILENT,
  });

/** 句子侦探判定（孩子指出错的是第几句） */
export const judgeGrammarDetective = (payload: StudyGrammarDetectiveJudgeRequest) =>
  defHttp.post<StudyGrammarDetectiveJudgeVO>({
    url: '/api/v1/study/grammar/game/detective/judge',
    data: payload,
    ...SILENT,
  });

/** 语法变形闯关取题（只给中文意思） */
export const fetchGrammarTransformNext = () =>
  defHttp.get<StudyGrammarTransformRoundVO>({
    url: '/api/v1/study/grammar/game/transform/next',
    ...SILENT,
  });

/** 语法变形闯关判定（孩子写的句子对不对） */
export const judgeGrammarTransform = (payload: StudyGrammarTransformJudgeRequest) =>
  defHttp.post<StudyGrammarTransformJudgeVO>({
    url: '/api/v1/study/grammar/game/transform/judge',
    data: payload,
    ...SILENT,
  });

/** 我的错句收藏馆（统一错题池里自己的错句，可按错因过滤） */
export const fetchGrammarGameCollection = (errorCause?: string) =>
  defHttp.get<StudyGrammarGameCollectionResultVO>({
    url: '/api/v1/study/grammar/game/collection',
    params: { errorCause },
    ...SILENT,
  });

/** 闯关进度（只跟自己比） */
export const fetchGrammarGameProgress = () =>
  defHttp.get<StudyGrammarGameProgressVO>({
    url: '/api/v1/study/grammar/game/progress',
    ...SILENT,
  });
