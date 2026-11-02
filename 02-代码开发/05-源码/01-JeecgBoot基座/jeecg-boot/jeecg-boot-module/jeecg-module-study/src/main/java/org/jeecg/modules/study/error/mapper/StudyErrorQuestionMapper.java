package org.jeecg.modules.study.error.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import org.jeecg.modules.study.error.vo.ErrorVariantVO;

/**
 * 题目只读 Mapper（错题域只需要读题目与考点，用于定位母题与降级变式）。
 *
 * <p>题目是<b>全局内容表</b>（无 child_id），跨模块共享。</p>
 *
 * @author jeecg-study
 */
@Mapper
public interface StudyErrorQuestionMapper {

    /**
     * 按题目业务ID查母题业务ID。
     *
     * @param questionCode 题目业务ID
     * @return 母题业务ID；该题不是变式返回 null
     */
    @Select("SELECT mother_question_code FROM study_question WHERE question_code = #{questionCode}"
            + " AND del_flag = 0 LIMIT 1")
    String selectMotherCodeByQuestionCode(@Param("questionCode") String questionCode);

    /**
     * 按题目业务ID查题型。
     *
     * @param questionCode 题目业务ID
     * @return 题型；不存在返回 null
     */
    @Select("SELECT question_type FROM study_question WHERE question_code = #{questionCode}"
            + " AND del_flag = 0 LIMIT 1")
    String selectQuestionType(@Param("questionCode") String questionCode);

    /**
     * 按考点业务ID查母题业务ID（考点可以挂母题）。
     *
     * @param pointCode 考点业务ID
     * @return 母题业务ID；未挂返回 null
     */
    @Select("SELECT mother_question_code FROM study_knowledge_point WHERE point_code = #{pointCode}"
            + " AND del_flag = 0 LIMIT 1")
    String selectMotherCodeByPointCode(@Param("pointCode") String pointCode);

    /**
     * 降级变式：同知识点、同题型的「换数字」变式。
     *
     * @param knowledgePointCode 考点业务ID
     * @param questionType       题型
     * @param excludeQuestionCode 需要排除的原题业务ID
     * @return 候选变式
     */
    @Select("SELECT question_code AS questionCode, question_code AS variantCode, mother_question_code AS motherCode,"
            + " IFNULL(variant_layer, 1) AS layer, stem AS stem FROM study_question"
            + " WHERE knowledge_point_code = #{knowledgePointCode} AND question_type = #{questionType}"
            + " AND question_code <> #{excludeQuestionCode} AND del_flag = 0 AND status = 1"
            + " AND IFNULL(variant_layer, 0) = 1"
            + " ORDER BY create_time DESC")
    List<ErrorVariantVO> selectDegradedVariants(@Param("knowledgePointCode") String knowledgePointCode,
                                                @Param("questionType") String questionType,
                                                @Param("excludeQuestionCode") String excludeQuestionCode);

    /**
     * 降级变式的兜底：同知识点同题型的其它题目（没有换数字变式时用）。
     *
     * @param knowledgePointCode 考点业务ID
     * @param questionType       题型
     * @param excludeQuestionCode 需要排除的原题业务ID
     * @return 候选变式
     */
    @Select("SELECT question_code AS questionCode, question_code AS variantCode, mother_question_code AS motherCode,"
            + " IFNULL(variant_layer, 1) AS layer, stem AS stem FROM study_question"
            + " WHERE knowledge_point_code = #{knowledgePointCode} AND question_type = #{questionType}"
            + " AND question_code <> #{excludeQuestionCode} AND del_flag = 0 AND status = 1"
            + " ORDER BY create_time DESC LIMIT 5")
    List<ErrorVariantVO> selectSamePointVariants(@Param("knowledgePointCode") String knowledgePointCode,
                                                  @Param("questionType") String questionType,
                                                  @Param("excludeQuestionCode") String excludeQuestionCode);
}
