package org.jeecg.modules.study.math.service;

import java.util.List;

import org.jeecg.modules.study.math.vo.MathProgressVO;
import org.jeecg.modules.study.math.vo.MotherDetailVO;
import org.jeecg.modules.study.math.vo.MotherListItemVO;
import org.jeecg.modules.study.math.vo.MotherPassCommand;
import org.jeecg.modules.study.math.vo.MotherPassResultVO;
import org.jeecg.modules.study.math.vo.MotherSaveCommand;
import org.jeecg.modules.study.math.vo.NarrativeBriefVO;
import org.jeecg.modules.study.math.vo.NarrativeCommand;
import org.jeecg.modules.study.math.vo.NarrativeResultVO;
import org.jeecg.modules.study.math.vo.StepTagVO;
import org.jeecg.modules.study.math.vo.VariantAnswerCommand;
import org.jeecg.modules.study.math.vo.VariantAnswerResultVO;
import org.jeecg.modules.study.math.vo.VariantGenerateResultVO;
import org.jeecg.modules.study.math.vo.VariantVO;

/**
 * 数学母题服务（F-MTH-01…F-MTH-06）。
 *
 * <p><b>三条硬口径</b>：</p>
 * <ul>
 *   <li><b>五段齐全</b>：母题缺任一段都返回 4007，并提示缺哪一段；</li>
 *   <li><b>答案由规则产出</b>：变式答案由母题的算式算出，不依赖任何模型（R-02），
 *       且<b>任何响应体都不下发答案</b>（R-01）；</li>
 *   <li><b>过关双判据</b>：变式全对 <b>且</b> 口述讲出关键关系，缺一不过关；
 *       口述不过关只给苏格拉底式追问与关键词反馈，<b>绝不给答案</b>。</li>
 * </ul>
 *
 * @author jeecg-study
 */
public interface StudyMathService {

    /**
     * 新建母题（五段齐全校验，幂等：同一「学科 + 考点 + 题型名」只更新不新建）。
     *
     * @param command 母题入参
     * @return 母题详情
     */
    MotherDetailVO saveMother(MotherSaveCommand command);

    /**
     * 修改母题（五段齐全校验）。
     *
     * @param motherCode 母题业务ID
     * @param command    母题入参
     * @return 母题详情
     */
    MotherDetailVO updateMother(String motherCode, MotherSaveCommand command);

    /**
     * 母题详情（五段结构）。
     *
     * @param motherCode 母题业务ID
     * @return 母题详情
     */
    MotherDetailVO motherDetail(String motherCode);

    /**
     * 闯关顺序列表（考纲权重降序 + 掌握度升序）。
     *
     * @param childId 目标孩子 ID；孩子身份下可空（按会话取自己）
     * @return 关卡列表
     */
    List<MotherListItemVO> motherList(String childId);

    /**
     * 关卡进度（以母题为单位：「这类题我会了 N 类」）。
     *
     * @param childId 目标孩子 ID；孩子身份下可空
     * @return 进度
     */
    MathProgressVO progress(String childId);

    /**
     * 生成三层变式（幂等键：孩子 + 母题 + 层级）。
     *
     * @param motherCode 母题业务ID
     * @param childId    目标孩子 ID；孩子身份下可空
     * @return 三层变式
     */
    VariantGenerateResultVO generateVariants(String motherCode, String childId);

    /**
     * 变式列表（<b>不含答案</b>）。
     *
     * @param motherCode 母题业务ID
     * @param childId    目标孩子 ID；孩子身份下可空
     * @return 变式列表
     */
    List<VariantVO> listVariants(String motherCode, String childId);

    /**
     * 变式作答：判定 + 步骤级错因打标 + 答错入统一错题池（幂等）。
     *
     * @param motherCode  母题业务ID
     * @param variantCode 变式业务ID
     * @param command     作答入参
     * @return 作答结果
     */
    VariantAnswerResultVO answerVariant(String motherCode, String variantCode, VariantAnswerCommand command);

    /**
     * 提交过关判定：变式全对 <b>且</b> 口述通过才过关。
     *
     * @param motherCode 母题业务ID
     * @param command    过关入参
     * @return 过关判定结果
     */
    MotherPassResultVO submitPass(String motherCode, MotherPassCommand command);

    /**
     * 提交口述：返回命中 / 缺失关键词，口述不全不判对，只给追问。
     *
     * @param motherCode 母题业务ID
     * @param command    口述入参
     * @return 口述反馈
     */
    NarrativeResultVO submitNarrative(String motherCode, NarrativeCommand command);

    /**
     * 关卡说明（口述任务；<b>不列关键词</b>）。
     *
     * @param motherCode 母题业务ID
     * @return 关卡说明
     */
    NarrativeBriefVO narrativeBrief(String motherCode);

    /**
     * 步骤级错因打标明细（可查询）。
     *
     * @param motherCode 母题业务ID
     * @param childId    目标孩子 ID；孩子身份下可空
     * @return 打标明细
     */
    List<StepTagVO> listStepTags(String motherCode, String childId);
}
