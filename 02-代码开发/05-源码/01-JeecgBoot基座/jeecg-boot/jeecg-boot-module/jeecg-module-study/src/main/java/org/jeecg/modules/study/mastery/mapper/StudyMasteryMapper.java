package org.jeecg.modules.study.mastery.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 考点掌握度 Mapper（只读）。
 *
 * <p><b>数据来源</b>：{@code study_error_pool}（行为表，按 {@code child_id} 隔离）
 * + {@code study_knowledge_point}（内容表，全局共享，只取名称与学科）。
 * 考点级掌握度<b>不另建权威表</b>：错题池里的 {@code mastery_state} 已经是权威值，
 * 与家长端报表（{@code ParentReportMapper}）取的是同一口径。</p>
 *
 * <p><b>隔离口径</b>：每条语句都强制带 {@code child_id} 条件，
 * 且 {@code child_id} 只能由服务端从关系表解析出来（不接受前端直接传值）。</p>
 *
 * @author jeecg-study
 */
@Mapper
public interface StudyMasteryMapper {

    /**
     * 薄弱考点：按错题条数从多到少。
     *
     * <p>{@code masteryState} 取该考点上<b>最弱</b>的掌握状态；
     * 排序键可能并列，追加 {@code knowledge_point_code} 作为唯一兜底键，
     * 保证分页 / 截断时结果稳定。</p>
     *
     * @param childId 孩子 ID
     * @param limit   取前几条
     * @return 考点 → 条数
     */
    @Select("SELECT knowledge_point_code AS pointCode, MIN(mastery_state) AS masteryState,"
            + " COUNT(*) AS errorCount, MAX(create_time) AS lastErrorAt"
            + " FROM study_error_pool WHERE child_id = #{childId} AND del_flag = 0"
            + " AND knowledge_point_code IS NOT NULL AND knowledge_point_code <> ''"
            + " GROUP BY knowledge_point_code ORDER BY errorCount DESC, pointCode ASC LIMIT #{limit}")
    List<Map<String, Object>> weakPoints(@Param("childId") String childId, @Param("limit") int limit);

    /**
     * 某个考点的错题汇总（该孩子在该考点下的错题条数与最弱掌握状态）。
     *
     * @param childId   孩子 ID
     * @param pointCode 考点业务ID
     * @return 汇总行；该孩子在这个考点下没有错题时返回 null
     */
    @Select("SELECT knowledge_point_code AS pointCode, MIN(mastery_state) AS masteryState,"
            + " COUNT(*) AS errorCount, MAX(create_time) AS lastErrorAt"
            + " FROM study_error_pool WHERE child_id = #{childId} AND del_flag = 0"
            + " AND knowledge_point_code = #{pointCode} GROUP BY knowledge_point_code"
            + " ORDER BY knowledge_point_code")
    Map<String, Object> pointStat(@Param("childId") String childId, @Param("pointCode") String pointCode);

    /**
     * 某个考点的错因分布（只取维度与条数）。
     *
     * @param childId   孩子 ID
     * @param pointCode 考点业务ID
     * @return 错因 → 条数
     */
    @Select("SELECT error_cause AS cause, COUNT(*) AS cnt FROM study_error_pool"
            + " WHERE child_id = #{childId} AND del_flag = 0 AND knowledge_point_code = #{pointCode}"
            + " GROUP BY error_cause ORDER BY cnt DESC, error_cause")
    List<Map<String, Object>> causeCounts(@Param("childId") String childId,
                                          @Param("pointCode") String pointCode);

    /**
     * 考点名称 / 学科（内容表，全局共享，无 child_id）。
     *
     * @param pointCodes 考点业务ID 集合
     * @return 考点 → 名称与学科
     */
    @Select("<script>SELECT point_code AS pointCode, name AS pointName, subject_code AS subjectCode"
            + " FROM study_knowledge_point WHERE del_flag = 0 AND point_code IN"
            + " <foreach collection='pointCodes' item='it' open='(' separator=',' close=')'>#{it}</foreach>"
            + "</script>")
    List<Map<String, Object>> pointInfos(@Param("pointCodes") List<String> pointCodes);
}
