package org.jeecg.modules.study.registry.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import org.jeecg.modules.study.registry.vo.KnowledgePointCountVO;
import org.jeecg.modules.study.registry.vo.MotherQuestionOptionVO;

/**
 * 坐标树引用的内容表<b>只读</b>统计 Mapper。
 *
 * <p>本 Mapper 只做 SELECT，不写题目 / 单词 / 母题 / 语法点 / 写作五张内容表
 * （内容归各自域管，坐标树只统计）。</p>
 *
 * @author jeecg-study
 */
@Mapper
public interface StudyContentReferenceMapper {

    /**
     * 统计某母题业务ID是否存在。
     *
     * @param motherCode 母题业务ID
     * @return 存在返回大于 0
     */
    @Select("SELECT COUNT(*) FROM study_mother_question WHERE mother_code = #{motherCode} AND del_flag = 0")
    int countMotherQuestion(@Param("motherCode") String motherCode);

    /**
     * 母题下拉：只列<b>确实存在</b>的母题（TC-01-09）。
     *
     * @param subjectCode 学科编码；可空，空则不限学科
     * @return 母题候选列表
     */
    @Select("<script>SELECT mother_code, title, subject_code FROM study_mother_question "
            + "WHERE del_flag = 0 AND status = 1 "
            + "<if test='subjectCode != null and subjectCode != \"\"'> AND subject_code = #{subjectCode}</if> "
            + "ORDER BY order_no, mother_code</script>")
    List<MotherQuestionOptionVO> selectMotherQuestions(@Param("subjectCode") String subjectCode);

    /**
     * 考点下挂题目数。
     *
     * @return 考点 → 数量
     */
    @Select("SELECT knowledge_point_code, COUNT(*) AS total FROM study_question "
            + "WHERE del_flag = 0 AND status = 1 AND knowledge_point_code IS NOT NULL "
            + "GROUP BY knowledge_point_code ORDER BY knowledge_point_code")
    List<KnowledgePointCountVO> countQuestions();

    /**
     * 考点下挂单词数。
     *
     * @return 考点 → 数量
     */
    @Select("SELECT knowledge_point_code, COUNT(*) AS total FROM study_word "
            + "WHERE del_flag = 0 AND status = 1 AND knowledge_point_code IS NOT NULL "
            + "GROUP BY knowledge_point_code ORDER BY knowledge_point_code")
    List<KnowledgePointCountVO> countWords();

    /**
     * 考点下挂母题数。
     *
     * @return 考点 → 数量
     */
    @Select("SELECT knowledge_point_code, COUNT(*) AS total FROM study_mother_question "
            + "WHERE del_flag = 0 AND status = 1 AND knowledge_point_code IS NOT NULL "
            + "GROUP BY knowledge_point_code ORDER BY knowledge_point_code")
    List<KnowledgePointCountVO> countMotherQuestions();

    /**
     * 考点下挂语法点数。
     *
     * @return 考点 → 数量
     */
    @Select("SELECT knowledge_point_code, COUNT(*) AS total FROM study_grammar_point "
            + "WHERE del_flag = 0 AND status = 1 AND knowledge_point_code IS NOT NULL "
            + "GROUP BY knowledge_point_code ORDER BY knowledge_point_code")
    List<KnowledgePointCountVO> countGrammarPoints();

    /**
     * 考点下挂写作数。
     *
     * @return 考点 → 数量
     */
    @Select("SELECT knowledge_point_code, COUNT(*) AS total FROM study_writing "
            + "WHERE del_flag = 0 AND status = 1 AND knowledge_point_code IS NOT NULL "
            + "GROUP BY knowledge_point_code ORDER BY knowledge_point_code")
    List<KnowledgePointCountVO> countWritings();
}
