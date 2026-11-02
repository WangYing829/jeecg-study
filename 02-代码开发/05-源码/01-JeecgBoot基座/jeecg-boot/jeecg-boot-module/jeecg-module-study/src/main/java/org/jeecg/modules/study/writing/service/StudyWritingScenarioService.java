package org.jeecg.modules.study.writing.service;

import org.jeecg.modules.study.writing.vo.WritingScenarioListVO;
import org.jeecg.modules.study.writing.vo.WritingScenarioThemeListVO;
import org.jeecg.modules.study.writing.vo.WritingScenarioVO;

/**
 * 写作情境库服务（F-WRT-03《素材改情境库》）。
 *
 * <p><b>产品口径</b>：同一主题（如「坚持」）下给<b>不同处境、不同年龄、不同细节</b>的情境，
 * <b>不给金句</b>；孩子必须写自己的经历。</p>
 *
 * <p><b>数据范围</b>：情境库是<b>全局共享的内容表</b>（没有 child_id），
 * 因此本服务不涉及孩子维度，也不做按孩子的隔离。</p>
 *
 * <p><b>R-02</b>：本服务<b>没有任何 AI 客户端依赖</b>，只做确定性的拼装与分组。</p>
 *
 * @author jeecg-study
 */
public interface StudyWritingScenarioService {

    /**
     * 情境主题清单（同一主题下有多个情境）。
     *
     * @param stageCode 学段；为空不过滤
     * @return 主题列表
     */
    WritingScenarioThemeListVO listThemes(String stageCode);

    /**
     * 情境列表（可按主题 / 学段过滤；主题为空时返回全部）。
     *
     * @param themeCode 主题编码；为空不过滤
     * @param stageCode 学段；为空不过滤
     * @return 情境列表
     */
    WritingScenarioListVO listScenarios(String themeCode, String stageCode);

    /**
     * 情境详情（训练入口：提示题 + 细节线索 + 限制条件 + 禁用提示）。
     *
     * @param scenarioCode 情境业务ID
     * @return 情境详情
     */
    WritingScenarioVO getScenario(String scenarioCode);
}
