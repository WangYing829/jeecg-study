package org.jeecg.modules.study.registry.service;

import java.util.List;

import org.jeecg.modules.study.registry.vo.KnowledgePointContentStatVO;
import org.jeecg.modules.study.registry.vo.KnowledgePointImportRequest;
import org.jeecg.modules.study.registry.vo.KnowledgePointTreeVO;
import org.jeecg.modules.study.registry.vo.KnowledgePointUpdateRequest;
import org.jeecg.modules.study.registry.vo.KnowledgePointVO;
import org.jeecg.modules.study.registry.vo.KnowledgePointVersionVO;
import org.jeecg.modules.study.registry.vo.MotherQuestionOptionVO;

/**
 * 坐标树（考点树）服务。
 *
 * <p>四层结构：1考纲 → 2学科 → 3单元 → 4考点；全局内容表，家庭内共享。</p>
 *
 * <p><b>版本化</b>：生成新版本时旧节点只失效不删除，
 * 历史错题仍能解析到旧坐标名（TC-01-12 / TC-01-13 / TC-08-23）。</p>
 *
 * @author jeecg-study
 */
public interface StudyKnowledgePointService {

    /**
     * 坐标树全树。
     *
     * @param subjectCode 学科编码；可空，空则返回整棵树
     * @return 坐标树
     */
    KnowledgePointTreeVO tree(String subjectCode);

    /**
     * 考点详情。
     *
     * <p><b>已失效节点也能查到</b> —— 历史错题要能解析出旧坐标名（TC-01-13）。</p>
     *
     * @param code 考点业务ID
     * @return 节点详情
     */
    KnowledgePointVO detail(String code);

    /**
     * 维护考点四属性（题型 / 分值权重 / 常见错因 / 母题）。
     *
     * <p>母题编码不存在时返回 4004 且<b>不落库</b>（TC-01-10）。</p>
     *
     * @param code    考点业务ID
     * @param request 四属性入参
     * @return 更新后的节点
     */
    KnowledgePointVO updateAttributes(String code, KnowledgePointUpdateRequest request);

    /**
     * 考点下挂内容统计（题目 / 单词 / 母题 / 语法点 / 写作 五类数量）。
     *
     * @param code 考点业务ID
     * @return 统计结果
     */
    KnowledgePointContentStatVO contentStat(String code);

    /**
     * 考纲版本列表。
     *
     * @return 版本列表
     */
    List<KnowledgePointVersionVO> versions();

    /**
     * 某节点的历史版本。
     *
     * @param code 考点业务ID
     * @return 历史版本列表（含当前版本）
     */
    List<KnowledgePointVO> history(String code);

    /**
     * 按名称 / 编码检索考点。
     *
     * @param keyword     关键字
     * @param subjectCode 学科编码；可空
     * @return 命中的节点列表
     */
    List<KnowledgePointVO> search(String keyword, String subjectCode);

    /**
     * 母题下拉：只列确实存在的母题。
     *
     * @param subjectCode 学科编码；可空
     * @return 母题候选列表
     */
    List<MotherQuestionOptionVO> motherQuestionOptions(String subjectCode);

    /**
     * 导入坐标树（幂等）。
     *
     * <p>导入一个<b>新版本</b>时，该学科的旧节点全部置失效
     * （{@code status=0} + {@code invalid_at}），<b>不删除</b>；新节点 {@code version} 递增。</p>
     *
     * @param request 导入入参
     * @return 导入后的节点列表
     */
    List<KnowledgePointVO> importTree(KnowledgePointImportRequest request);
}
