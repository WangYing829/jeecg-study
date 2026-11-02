package org.jeecg.modules.study.practice.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import org.jeecg.modules.study.practice.entity.StudyPracticeRecord;

/**
 * 作答记录 Mapper（按月分表，表名由服务端按当前月份拼出）。
 *
 * <p>表名来自服务端时钟（{@code study_practice_record_yyyyMM}），
 * 不接受任何外部输入，因此 {@code ${tableName}} 拼接是安全的。</p>
 *
 * @author jeecg-study
 */
@Mapper
public interface StudyPracticeRecordMapper {

    /**
     * 落一条作答记录。
     *
     * @param tableName 月表名
     * @param record    作答记录
     * @return 影响行数
     */
    @Insert("INSERT INTO ${tableName} (id, child_id, context_id, source_module, content_type, content_id,"
            + " knowledge_point_code, answer, correct, judge_source, error_cause, error_code, duration_ms,"
            + " step_tags, practice_date, del_flag, create_time, update_time)"
            + " VALUES (#{record.id}, #{record.childId}, #{record.contextId}, #{record.sourceModule},"
            + " #{record.contentType}, #{record.contentId}, #{record.knowledgePointCode}, #{record.answer},"
            + " #{record.correct}, #{record.judgeSource}, #{record.errorCause}, #{record.errorCode},"
            + " #{record.durationMs}, #{record.stepTags}, #{record.practiceDate}, 0, NOW(), NOW())")
    int insertRecord(@Param("tableName") String tableName, @Param("record") StudyPracticeRecord record);

    /**
     * 按主键查作答记录。
     *
     * @param tableName 月表名
     * @param id        主键
     * @return 作答记录；不存在返回 null
     */
    @Select("SELECT * FROM ${tableName} WHERE id = #{id} AND del_flag = 0 LIMIT 1")
    StudyPracticeRecord selectById(@Param("tableName") String tableName, @Param("id") String id);

    /**
     * 查某孩子对某内容的最近一次作答。
     *
     * @param tableName 月表名
     * @param childId   孩子 ID
     * @param contentId 内容业务ID
     * @param sessionId 会话 ID，可空（空则不限会话）
     * @return 作答记录；不存在返回 null
     */
    @Select("<script>SELECT * FROM ${tableName} WHERE child_id = #{childId} AND content_id = #{contentId}"
            + " AND del_flag = 0"
            + "<if test='sessionId != null and sessionId != \"\"'> AND JSON_UNQUOTE(JSON_EXTRACT(step_tags, '$.sessionId')) = #{sessionId}</if>"
            + " ORDER BY create_time DESC, id DESC LIMIT 1</script>")
    StudyPracticeRecord selectLatestByContent(@Param("tableName") String tableName,
                                              @Param("childId") String childId,
                                              @Param("contentId") String contentId,
                                              @Param("sessionId") String sessionId);

    /**
     * 列出某会话的全部作答。
     *
     * @param tableName 月表名
     * @param childId   孩子 ID
     * @param sessionId 会话 ID
     * @return 作答记录
     */
    @Select("SELECT * FROM ${tableName} WHERE child_id = #{childId} AND del_flag = 0"
            + " AND JSON_UNQUOTE(JSON_EXTRACT(step_tags, '$.sessionId')) = #{sessionId}"
            + " ORDER BY create_time ASC")
    List<StudyPracticeRecord> selectBySession(@Param("tableName") String tableName,
                                              @Param("childId") String childId,
                                              @Param("sessionId") String sessionId);

    /**
     * 会话汇总。
     *
     * @param tableName 月表名
     * @param childId   孩子 ID
     * @param sessionId 会话 ID
     * @return 汇总行；无数据返回 null
     */
    @Select("SELECT COUNT(*) AS total, SUM(correct) AS correctCount, SUM(duration_ms) AS durationMs"
            + " FROM ${tableName} WHERE child_id = #{childId} AND del_flag = 0"
            + " AND JSON_UNQUOTE(JSON_EXTRACT(step_tags, '$.sessionId')) = #{sessionId}")
    Map<String, Object> summarizeSession(@Param("tableName") String tableName,
                                          @Param("childId") String childId,
                                          @Param("sessionId") String sessionId);

    /**
     * 更新作答记录上的错题业务ID（幂等重放时补写）。
     *
     * @param tableName 月表名
     * @param id        主键
     * @param errorCode 错题业务ID
     * @return 影响行数
     */
    @Update("UPDATE ${tableName} SET error_code = #{errorCode}, update_time = NOW()"
            + " WHERE id = #{id} AND del_flag = 0 AND error_code IS NULL")
    int fillErrorCode(@Param("tableName") String tableName,
                      @Param("id") String id,
                      @Param("errorCode") String errorCode);
}
