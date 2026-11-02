package org.jeecg.modules.study.parent.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 家长端只读报表 Mapper。
 *
 * <p><b>只读</b>：本 Mapper 只有查询，没有任何写入 —— 家长端不改任何业务数据。</p>
 *
 * <p><b>隔离口径</b>：每条语句都强制带 {@code child_id} 条件，
 * 且 {@code child_id} 只能由服务端从关系表解析出来（不接受前端直接传值）。</p>
 *
 * <p><b>不碰答案字段</b>：错题池里的 {@code raw_answer} / {@code correct_answer} /
 * {@code content_snapshot} 一律不出现在本 Mapper 的查询列里，家长端拿不到答案（R-02 / R-04）。</p>
 *
 * @author jeecg-study
 */
@Mapper
public interface ParentReportMapper {

    /**
     * 数据库当前日期。
     *
     * <p>日期口径统一取数据库的 {@code CURDATE()}，避免应用与数据库时区不一致时日期漂移。</p>
     *
     * @return 当前日期
     */
    @Select("SELECT CURDATE()")
    java.sql.Date currentDate();

    /**
     * 某天的累计有效用眼分钟。
     *
     * @param childId 孩子 ID
     * @param date    日期（yyyy-MM-dd）
     * @return 分钟数
     */
    @Select("SELECT CAST(COALESCE(SUM(active_minutes), 0) AS SIGNED) FROM study_eye_usage"
            + " WHERE child_id = #{childId} AND del_flag = 0 AND usage_date = #{date}")
    Integer sumEyeMinutes(@Param("childId") String childId, @Param("date") String date);

    /**
     * 一段时间内的累计有效用眼分钟。
     *
     * @param childId 孩子 ID
     * @param from    起（含）
     * @param to      止（含）
     * @return 分钟数
     */
    @Select("SELECT CAST(COALESCE(SUM(active_minutes), 0) AS SIGNED) FROM study_eye_usage"
            + " WHERE child_id = #{childId} AND del_flag = 0 AND usage_date BETWEEN #{from} AND #{to}")
    Integer sumEyeMinutesBetween(@Param("childId") String childId,
                                 @Param("from") String from,
                                 @Param("to") String to);

    /**
     * 一段时间内每次会话的平均用眼分钟。
     *
     * @param childId 孩子 ID
     * @param from    起（含）
     * @param to      止（含）
     * @return 平均分钟数
     */
    @Select("SELECT CAST(COALESCE(ROUND(AVG(active_minutes)), 0) AS SIGNED) FROM study_eye_usage"
            + " WHERE child_id = #{childId} AND del_flag = 0 AND usage_date BETWEEN #{from} AND #{to}")
    Integer avgSessionMinutesBetween(@Param("childId") String childId,
                                     @Param("from") String from,
                                     @Param("to") String to);

    /**
     * 一段时间内的休息提醒触发次数。
     *
     * @param childId 孩子 ID
     * @param from    起（含）
     * @param to      止（含）
     * @return 次数
     */
    @Select("SELECT CAST(COALESCE(SUM(rest_count), 0) AS SIGNED) FROM study_eye_usage"
            + " WHERE child_id = #{childId} AND del_flag = 0 AND usage_date BETWEEN #{from} AND #{to}")
    Integer sumRestCountBetween(@Param("childId") String childId,
                                 @Param("from") String from,
                                 @Param("to") String to);

    /**
     * 一段时间内的休息确认次数。
     *
     * @param childId 孩子 ID
     * @param from    起（含）
     * @param to      止（含）
     * @return 次数
     */
    @Select("SELECT CAST(COALESCE(SUM(rest_confirmed), 0) AS SIGNED) FROM study_eye_usage"
            + " WHERE child_id = #{childId} AND del_flag = 0 AND usage_date BETWEEN #{from} AND #{to}")
    Integer sumRestConfirmedBetween(@Param("childId") String childId,
                                     @Param("from") String from,
                                     @Param("to") String to);

    /**
     * 有有效用眼记录的日期（倒序）。
     *
     * @param childId 孩子 ID
     * @param to      截止日期（含）
     * @return 日期列表
     */
    @Select("SELECT DISTINCT usage_date FROM study_eye_usage"
            + " WHERE child_id = #{childId} AND del_flag = 0 AND active_minutes > 0 AND usage_date <= #{to}"
            + " ORDER BY usage_date DESC LIMIT 120")
    List<java.sql.Date> listActiveUsageDates(@Param("childId") String childId,
                                              @Param("to") String to);

    /**
     * 每天的用眼分钟与休息次数。
     *
     * @param childId 孩子 ID
     * @param from    起（含）
     * @param to      止（含）
     * @return 每天一行
     */
    @Select("SELECT usage_date AS usageDate, CAST(COALESCE(SUM(active_minutes), 0) AS SIGNED) AS eyeMinutes,"
            + " CAST(COALESCE(SUM(rest_count), 0) AS SIGNED) AS restCount FROM study_eye_usage"
            + " WHERE child_id = #{childId} AND del_flag = 0 AND usage_date BETWEEN #{from} AND #{to}"
            + " GROUP BY usage_date ORDER BY usage_date")
    List<Map<String, Object>> listDailyEyeUsage(@Param("childId") String childId,
                                                @Param("from") String from,
                                                @Param("to") String to);

    /**
     * 掌握度分布（1未知 2学习中 3熟悉 4掌握）。
     *
     * @param childId 孩子 ID
     * @return 掌握度 → 条数
     */
    @Select("SELECT mastery_state AS masteryState, COUNT(*) AS cnt FROM study_error_pool"
            + " WHERE child_id = #{childId} AND del_flag = 0 GROUP BY mastery_state ORDER BY mastery_state")
    List<Map<String, Object>> masteryDistribution(@Param("childId") String childId);

    /**
     * 错因 Top3（只取维度名与条数，不含任何作答内容）。
     *
     * @param childId 孩子 ID
     * @return 错因 → 条数
     */
    @Select("SELECT error_cause AS cause, COUNT(*) AS cnt FROM study_error_pool"
            + " WHERE child_id = #{childId} AND del_flag = 0 GROUP BY error_cause"
            + " ORDER BY cnt DESC, error_cause LIMIT 3")
    List<Map<String, Object>> topErrorCauses(@Param("childId") String childId);

    /**
     * 错因全量分布。
     *
     * @param childId 孩子 ID
     * @return 错因 → 条数
     */
    @Select("SELECT error_cause AS cause, COUNT(*) AS cnt FROM study_error_pool"
            + " WHERE child_id = #{childId} AND del_flag = 0 GROUP BY error_cause ORDER BY cnt DESC, error_cause")
    List<Map<String, Object>> errorCauseDistribution(@Param("childId") String childId);

    /**
     * 错题总数。
     *
     * @param childId 孩子 ID
     * @return 条数
     */
    @Select("SELECT COUNT(*) FROM study_error_pool WHERE child_id = #{childId} AND del_flag = 0")
    Integer countErrors(@Param("childId") String childId);

    /**
     * 薄弱考点（按错题条数排序）。
     *
     * <p>{@code masteryState} 取该考点上<b>最弱</b>的掌握度状态。</p>
     *
     * <p>排序键 {@code cnt} 可能并列，截断前 N 条时结果不确定，
     * 因此追加 {@code knowledge_point_code} 作为唯一兜底键。</p>
     *
     * @param childId 孩子 ID
     * @param limit   取前几条
     * @return 考点 → 条数
     */
    @Select("SELECT knowledge_point_code AS pointCode, MIN(mastery_state) AS masteryState, COUNT(*) AS cnt"
            + " FROM study_error_pool WHERE child_id = #{childId} AND del_flag = 0"
            + " AND knowledge_point_code IS NOT NULL AND knowledge_point_code <> ''"
            + " GROUP BY knowledge_point_code ORDER BY cnt DESC, knowledge_point_code LIMIT #{limit}")
    List<Map<String, Object>> weakPoints(@Param("childId") String childId, @Param("limit") int limit);

    /**
     * 考点名称（内容表，全局共享）。
     *
     * @param pointCodes 考点业务ID 集合
     * @return 考点业务ID → 名称
     */
    @Select("<script>SELECT point_code AS pointCode, name AS pointName FROM study_knowledge_point"
            + " WHERE point_code IN"
            + " <foreach collection='pointCodes' item='it' open='(' separator=',' close=')'>#{it}</foreach>"
            + "</script>")
    List<Map<String, Object>> knowledgePointNames(@Param("pointCodes") List<String> pointCodes);

    /**
     * 每天的错题条数。
     *
     * @param childId 孩子 ID
     * @param from    起（含）
     * @param to      止（含）
     * @return 每天一行
     */
    @Select("SELECT DATE(create_time) AS day, COUNT(*) AS cnt FROM study_error_pool"
            + " WHERE child_id = #{childId} AND del_flag = 0"
            + " AND create_time >= #{from} AND create_time < DATE_ADD(#{to}, INTERVAL 1 DAY)"
            + " GROUP BY DATE(create_time) ORDER BY DATE(create_time)")
    List<Map<String, Object>> listDailyErrorCount(@Param("childId") String childId,
                                                  @Param("from") String from,
                                                  @Param("to") String to);

    /**
     * 每天的作文记录数。
     *
     * @param childId 孩子 ID
     * @param from    起（含）
     * @param to      止（含）
     * @return 每天一行
     */
    @Select("SELECT DATE(create_time) AS day, COUNT(*) AS cnt FROM study_writing"
            + " WHERE child_id = #{childId} AND del_flag = 0"
            + " AND create_time >= #{from} AND create_time < DATE_ADD(#{to}, INTERVAL 1 DAY)"
            + " GROUP BY DATE(create_time) ORDER BY DATE(create_time)")
    List<Map<String, Object>> listDailyWritingCount(@Param("childId") String childId,
                                                     @Param("from") String from,
                                                     @Param("to") String to);
}
