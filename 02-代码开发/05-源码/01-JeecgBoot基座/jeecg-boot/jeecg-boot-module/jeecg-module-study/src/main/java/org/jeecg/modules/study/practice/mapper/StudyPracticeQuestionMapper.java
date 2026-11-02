package org.jeecg.modules.study.practice.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import org.jeecg.modules.study.practice.entity.StudyQuestion;

/**
 * 题目 Mapper（只读；题目是全局内容表，无 child_id）。
 *
 * @author jeecg-study
 */
@Mapper
public interface StudyPracticeQuestionMapper {

    /**
     * 按题目业务ID查题目。
     *
     * @param questionCode 题目业务ID
     * @return 题目；不存在返回 null
     */
    @Select("SELECT * FROM study_question WHERE question_code = #{questionCode} AND del_flag = 0 LIMIT 1")
    StudyQuestion selectByQuestionCode(@Param("questionCode") String questionCode);

    /**
     * 按主键查题目。
     *
     * @param id 主键
     * @return 题目；不存在返回 null
     */
    @Select("SELECT * FROM study_question WHERE id = #{id} AND del_flag = 0 LIMIT 1")
    StudyQuestion selectById(@Param("id") String id);

    /**
     * 取一道<b>这个孩子还没做过</b>的题。
     *
     * @param tableName    作答记录月表名
     * @param childId      孩子 ID
     * @param questionType 题型，可空
     * @param subjectCode  学科编码，可空
     * @return 题目；没有可用题目返回 null
     */
    @Select("<script>SELECT * FROM study_question q WHERE q.del_flag = 0 AND q.status = 1"
            + "<if test='questionType != null and questionType != \"\"'> AND q.question_type = #{questionType}</if>"
            + "<if test='subjectCode != null and subjectCode != \"\"'> AND q.subject_code = #{subjectCode}</if>"
            + " AND NOT EXISTS (SELECT 1 FROM ${tableName} r WHERE r.child_id = #{childId}"
            + " AND r.content_id = q.question_code AND r.del_flag = 0)"
            + " ORDER BY q.difficulty ASC, q.create_time DESC, q.id ASC LIMIT 1</script>")
    StudyQuestion selectNextUnanswered(@Param("tableName") String tableName,
                                        @Param("childId") String childId,
                                        @Param("questionType") String questionType,
                                        @Param("subjectCode") String subjectCode);

    /**
     * 取一道该类型的题（兜底：孩子已经做完了所有题）。
     *
     * @param questionType 题型，可空
     * @param subjectCode  学科编码，可空
     * @return 题目；没有可用题目返回 null
     */
    @Select("<script>SELECT * FROM study_question q WHERE q.del_flag = 0 AND q.status = 1"
            + "<if test='questionType != null and questionType != \"\"'> AND q.question_type = #{questionType}</if>"
            + "<if test='subjectCode != null and subjectCode != \"\"'> AND q.subject_code = #{subjectCode}</if>"
            + " ORDER BY q.difficulty ASC, q.create_time DESC, q.id ASC LIMIT 1</script>")
    StudyQuestion selectFirst(@Param("questionType") String questionType,
                               @Param("subjectCode") String subjectCode);

    /**
     * 同考点的其它题目（解析里的「相关题目」）。
     *
     * @param knowledgePointCode 考点业务ID
     * @param excludeQuestionCode 需要排除的题目业务ID
     * @return 题目业务ID 列表
     */
    @Select("SELECT question_code FROM study_question WHERE knowledge_point_code = #{knowledgePointCode}"
            + " AND question_code <> #{excludeQuestionCode} AND del_flag = 0"
            + " ORDER BY difficulty ASC, question_code ASC LIMIT 5")
    List<String> selectRelatedQuestionCodes(@Param("knowledgePointCode") String knowledgePointCode,
                                            @Param("excludeQuestionCode") String excludeQuestionCode);
}
