package org.jeecg.modules.study.word.mapper;

import java.util.Date;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 背单词作答记录 Mapper。
 *
 * <p>写入的是<b>统一的作答记录表</b> {@code study_practice_record_YYYYMM}
 * （按月分表，见 {@code 05-partition.sql}）：背单词的每次作答与单元闯关
 * 都属于「一次练习」，因此与其它模块共用同一张记录表，
 * 连续学习天数即由这张表的 {@code practice_date} 推出。</p>
 *
 * <p>表名按当前月份拼出（{@code study_practice_record_yyyyMM}），
 * 只允许本模块按固定格式生成，不接受任何外部输入。</p>
 *
 * @author jeecg-study
 */
@Mapper
public interface StudyWordPracticeRecordMapper {

    /**
     * 写入一条作答记录。
     *
     * @param tableName        分表名（study_practice_record_yyyyMM）
     * @param id               主键
     * @param childId          孩子ID
     * @param contextId        所属上下文ID
     * @param sourceModule     来源模块
     * @param contentType      内容类型
     * @param contentId        内容业务ID
     * @param knowledgePointCode 考点业务ID
     * @param answer           孩子作答
     * @param correct          是否正确
     * @param judgeSource      判定来源
     * @param errorCause       错因
     * @param errorCode        错题业务ID
     * @param durationMs       耗时
     * @param practiceDate     练习日期
     * @param createBy         创建人
     * @return 影响行数
     */
    @Insert("INSERT INTO ${tableName} (id, child_id, context_id, source_module, content_type, "
            + "content_id, knowledge_point_code, answer, correct, judge_source, error_cause, "
            + "error_code, duration_ms, practice_date, del_flag, create_by, create_time, "
            + "update_by, update_time) VALUES (#{id}, #{childId}, #{contextId}, #{sourceModule}, "
            + "#{contentType}, #{contentId}, #{knowledgePointCode,jdbcType=VARCHAR}, "
            + "#{answer,jdbcType=VARCHAR}, #{correct}, #{judgeSource}, "
            + "#{errorCause,jdbcType=VARCHAR}, #{errorCode,jdbcType=VARCHAR}, "
            + "#{durationMs,jdbcType=INTEGER}, #{practiceDate}, 0, "
            + "#{createBy}, NOW(), #{createBy}, NOW())")
    int insertRecord(@Param("tableName") String tableName,
                     @Param("id") String id,
                     @Param("childId") String childId,
                     @Param("contextId") String contextId,
                     @Param("sourceModule") String sourceModule,
                     @Param("contentType") String contentType,
                     @Param("contentId") String contentId,
                     @Param("knowledgePointCode") String knowledgePointCode,
                     @Param("answer") String answer,
                     @Param("correct") int correct,
                     @Param("judgeSource") String judgeSource,
                     @Param("errorCause") String errorCause,
                     @Param("errorCode") String errorCode,
                     @Param("durationMs") Integer durationMs,
                     @Param("practiceDate") Date practiceDate,
                     @Param("createBy") String createBy);

    /**
     * 某孩子的背单词练习日期（倒序，去重）。
     *
     * @param tableName 分表名
     * @param childId   孩子ID
     * @return 练习日期列表
     */
    @Select("SELECT DISTINCT practice_date FROM ${tableName} WHERE child_id = #{childId} "
            + "AND source_module = 'WORD' AND del_flag = 0 ORDER BY practice_date DESC")
    List<Date> selectPracticeDates(@Param("tableName") String tableName,
                                    @Param("childId") String childId);
}
