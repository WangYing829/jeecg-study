package org.jeecg.modules.study.math.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import org.jeecg.modules.study.math.entity.StudyMathRecord;

/**
 * 数学作答记录 Mapper（写入统一作答记录分表）。
 *
 * <p>表名来自服务端时钟（{@code study_practice_record_yyyyMM}），
 * 不接受任何外部输入，因此 {@code ${tableName}} 拼接是安全的。</p>
 *
 * <p><b>建表不在这里</b>：运行期建表 DDL 已提到
 * {@link org.jeecg.modules.study.common.record.mapper.StudyRecordTableMapper}（唯一来源），
 * 表名一律经 {@link org.jeecg.modules.study.common.record.StudyRecordTableGuard} 拿
 * —— 兜底挂在「拿表名」那一刻，任何入口都漏不掉（ADR-045）。</p>
 *
 * @author jeecg-study
 */
@Mapper
public interface StudyMathRecordMapper {

    /**
     * 回写作答记录上的错题业务ID。
     *
     * @param tableName 表名
     * @param id        主键
     * @param errorCode 错题业务ID
     * @return 影响行数
     */
    @Update("UPDATE ${tableName} SET error_code = #{errorCode}, update_time = NOW()"
            + " WHERE id = #{id} AND del_flag = 0 AND error_code IS NULL")
    int updateErrorCode(@Param("tableName") String tableName,
                        @Param("id") String id,
                        @Param("errorCode") String errorCode);

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
    int insertRecord(@Param("tableName") String tableName, @Param("record") StudyMathRecord record);

    /**
     * 查某孩子对某内容的一条作答记录（幂等重放用）。
     *
     * @param tableName 月表名
     * @param id        主键
     * @return 记录；不存在返回 null
     */
    @Select("SELECT * FROM ${tableName} WHERE id = #{id} AND del_flag = 0 LIMIT 1")
    StudyMathRecord selectById(@Param("tableName") String tableName, @Param("id") String id);

    /**
     * 某孩子在母题域的全部作答（用于掌握度、连续错、进度统计）。
     *
     * @param tableName    月表名
     * @param childId      孩子 ID
     * @param sourceModule 来源模块
     * @return 作答明细行
     */
    @Select("SELECT v.mother_code AS motherCode, r.content_id AS contentId, r.correct AS correct,"
            + " r.error_cause AS errorCause, r.create_time AS createTime"
            + " FROM ${tableName} r JOIN study_question_variant v"
            + "   ON v.variant_code = r.content_id AND v.del_flag = 0"
            + " WHERE r.child_id = #{childId} AND r.source_module = #{sourceModule} AND r.del_flag = 0"
            + " ORDER BY r.create_time ASC, r.id ASC")
    List<Map<String, Object>> selectAttempts(@Param("tableName") String tableName,
                                             @Param("childId") String childId,
                                             @Param("sourceModule") String sourceModule);

    /**
     * 某孩子在某母题下的步骤级错因打标明细。
     *
     * @param tableName    月表名
     * @param childId      孩子 ID
     * @param motherCode   母题业务ID
     * @param sourceModule 来源模块
     * @return 打标明细行
     */
    @Select("SELECT r.content_id AS contentId, r.step_tags AS stepTags, r.error_cause AS errorCause,"
            + " r.correct AS correct, r.create_time AS createTime"
            + " FROM ${tableName} r JOIN study_question_variant v"
            + "   ON v.variant_code = r.content_id AND v.del_flag = 0"
            + " WHERE r.child_id = #{childId} AND r.source_module = #{sourceModule} AND r.del_flag = 0"
            + " AND v.mother_code = #{motherCode}"
            + " ORDER BY r.create_time ASC, r.id ASC")
    List<Map<String, Object>> selectStepTagsByMother(@Param("tableName") String tableName,
                                                     @Param("childId") String childId,
                                                     @Param("motherCode") String motherCode,
                                                     @Param("sourceModule") String sourceModule);
}
