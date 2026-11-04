package org.jeecg.modules.study.error.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.jeecg.modules.study.error.entity.StudyErrorPool;

/**
 * 统一错题池 Mapper。
 *
 * @author jeecg-study
 */
@Mapper
public interface StudyErrorPoolMapper extends BaseMapper<StudyErrorPool> {

    /**
     * 按「孩子 + 内容类型 + 内容」查错题（入池幂等键）。
     *
     * @param childId    孩子 ID
     * @param contentType 内容类型
     * @param contentId  内容业务ID
     * @return 错题；不存在返回 null
     */
    @Select("SELECT * FROM study_error_pool WHERE child_id = #{childId} AND content_type = #{contentType}"
            + " AND content_id = #{contentId} AND del_flag = 0"
            + " ORDER BY update_time DESC, id DESC LIMIT 1")
    StudyErrorPool selectByChildAndContent(@Param("childId") String childId,
                                           @Param("contentType") String contentType,
                                           @Param("contentId") String contentId);

    /**
     * 按错题业务ID查错题。
     *
     * @param errorCode 错题业务ID
     * @return 错题；不存在返回 null
     */
    @Select("SELECT * FROM study_error_pool WHERE error_code = #{errorCode} AND del_flag = 0 LIMIT 1")
    StudyErrorPool selectByErrorCode(@Param("errorCode") String errorCode);

    /**
     * 列出某孩子的全部错题。
     *
     * @param childId 孩子 ID
     * @return 错题列表（按入池时间倒序）
     */
    @Select("SELECT * FROM study_error_pool WHERE child_id = #{childId} AND del_flag = 0 ORDER BY create_time DESC")
    List<StudyErrorPool> selectByChildId(@Param("childId") String childId);

    /**
     * 推进复习进度（CAS：只有连续答对次数未被并发改动时才生效）。
     *
     * @param id           错题主键
     * @param oldStreak    期望的旧连续答对次数
     * @param newStreak    新的连续答对次数
     * @param nextReviewAt 下次复习日期
     * @param masteryState 掌握度
     * @param reviewStatus 复习状态
     * @param archivedAt   移出时间，可空
     * @param lastReviewAt 上次复习时间
     * @param errorCause   错因
     * @return 影响行数；0 表示并发冲突，本次不推进
     */
    @Update("UPDATE study_error_pool SET correct_streak = #{newStreak}, next_review_at = #{nextReviewAt},"
            + " mastery_state = #{masteryState}, review_status = #{reviewStatus}, archived_at = #{archivedAt},"
            + " last_review_at = #{lastReviewAt}, error_cause = #{errorCause}, update_time = NOW()"
            + " WHERE id = #{id} AND correct_streak = #{oldStreak} AND del_flag = 0")
    int updateReviewProgress(@Param("id") String id,
                             @Param("oldStreak") int oldStreak,
                             @Param("newStreak") int newStreak,
                             @Param("nextReviewAt") java.util.Date nextReviewAt,
                             @Param("masteryState") int masteryState,
                             @Param("reviewStatus") int reviewStatus,
                             @Param("archivedAt") java.util.Date archivedAt,
                             @Param("lastReviewAt") java.util.Date lastReviewAt,
                             @Param("errorCause") String errorCause);

    /**
     * 按错因分组统计某孩子的错题条数。
     *
     * @param childId 孩子 ID
     * @return 错因 → 条数
     */
    @Select("SELECT error_cause AS errorCause, COUNT(*) AS cnt FROM study_error_pool"
            + " WHERE child_id = #{childId} AND del_flag = 0 GROUP BY error_cause ORDER BY error_cause")
    List<Map<String, Object>> countGroupByCause(@Param("childId") String childId);

    /**
     * 高频错题：按「内容」聚合同一道题的出错次数。
     *
     * <p><b>只给客观计数</b>：没有名次类字段，顺序由条数决定（R-04）。</p>
     *
     * <p>排序键 {@code errorCount} 可能并列，追加 {@code contentId} 作为唯一兜底键，
     * 保证截断时结果稳定。</p>
     *
     * @param childId 孩子 ID
     * @param limit   取前几条
     * @return 内容 → 条数
     */
    @Select("SELECT MIN(error_code) AS errorCode, source_module AS sourceModule,"
            + " content_type AS contentType, content_id AS contentId,"
            + " MIN(knowledge_point_code) AS knowledgePointCode, COUNT(*) AS errorCount,"
            + " MIN(mastery_state) AS masteryState, MAX(create_time) AS lastErrorAt"
            + " FROM study_error_pool WHERE child_id = #{childId} AND del_flag = 0"
            + " GROUP BY source_module, content_type, content_id"
            + " ORDER BY errorCount DESC, contentId ASC LIMIT #{limit}")
    List<Map<String, Object>> countGroupByContent(@Param("childId") String childId,
                                                   @Param("limit") int limit);
}
