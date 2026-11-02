import { defHttp } from '/@/utils/http/axios';

/**
 * 语文写作 · 情境库接口封装（F-WRT-03）。
 *
 * <p>接口根统一为 {@code /api/v1/study}（见《接口设计说明书》§7.4）。</p>
 *
 * <p><b>产品口径</b>：情境库只给「处境」，<b>不给可以直接抄走的句子</b>；
 * 孩子必须写自己的经历。因此响应里只有处境、年龄段、细节线索、
 * 提示题与限制条件，没有任何答案类字段（R-01 / R-02 口径一致）。</p>
 *
 * <p>本文件是<b>情境库专用</b>的接口封装，独立成文件，
 * 避免在学习端共用接口文件里堆积无关模块的接口。</p>
 */

/** 统一的请求选项：错误提示由页面自己处理，避免弹窗打断学习流 */
const SILENT = { errorMessageMode: 'none' } as const;

// ------------------------------------------------------------------ 类型

/** 情境主题 */
export interface StudyScenarioThemeVO {
  /** 主题编码 */
  themeCode: string;
  /** 主题名称 */
  themeName?: string;
  /** 该主题下的情境条数 */
  scenarioCount?: number;
}

/** 情境 */
export interface StudyScenarioVO {
  /** 情境业务ID */
  scenarioCode: string;
  /** 主题编码 */
  themeCode?: string;
  /** 主题名称 */
  themeName?: string;
  /** 情境标题：处境一句话 */
  title?: string;
  /** 处境描述 */
  situationDesc?: string;
  /** 年龄段 */
  ageStage?: string;
  /** 年龄下限 */
  ageMin?: number;
  /** 年龄上限 */
  ageMax?: number;
  /** 细节线索：可观察的物与动作 */
  detailClues?: string[];
  /** 适用学段 */
  stageCode?: string;
  /** 适用文体 */
  genre?: string;
  /** 难度层级 */
  difficulty?: number;
  /** 写作提示题：引导填自己的经历 */
  promptQuestion?: string;
  /** 限制条件 */
  restrictionNote?: string;
  /** 禁用提示 */
  forbiddenWords?: string;
}

/** 情境主题列表 */
export interface StudyScenarioThemeListVO {
  items?: StudyScenarioThemeVO[];
  total?: number;
}

/** 情境列表 */
export interface StudyScenarioListVO {
  items?: StudyScenarioVO[];
  total?: number;
}

// ------------------------------------------------------------------ 接口

/** 情境主题清单（同一主题下有多个不同处境） */
export const fetchScenarioThemes = (stageCode?: string) =>
  defHttp.get<StudyScenarioThemeListVO>({
    url: '/api/v1/study/writing/scenario/themes',
    params: { stageCode },
    ...SILENT,
  });

/** 情境清单（可按主题 / 学段过滤） */
export const fetchScenarioList = (themeCode?: string, stageCode?: string) =>
  defHttp.get<StudyScenarioListVO>({
    url: '/api/v1/study/writing/scenario/list',
    params: { themeCode, stageCode },
    ...SILENT,
  });

/** 情境详情（训练入口：提示题 + 细节线索 + 限制条件 + 禁用提示） */
export const fetchScenarioDetail = (code: string) =>
  defHttp.get<StudyScenarioVO>({
    url: `/api/v1/study/writing/scenario/${code}`,
    ...SILENT,
  });
