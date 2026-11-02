package org.jeecg.modules.study.error.mapper;

import java.util.Date;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.jeecg.modules.study.error.entity.StudyReviewSchedule;

/**
 * 统一复习排期 Mapper。
 *
 * @author jeecg-study
 */
@Mapper
public interface StudyReviewScheduleMapper extends BaseMapper<StudyReviewSchedule> {

    /**
     * 按错题业务ID + 排期日期查排期行。
     *
     * @param errorCode    错题业务ID
     * @param scheduleDate 排期日期
     * @return 排期行；不存在返回 null
     */
    @Select("SELECT * FROM study_review_schedule WHERE error_code = #{errorCode}"
            + " AND schedule_date = #{scheduleDate} AND del_flag = 0 LIMIT 1")
    StudyReviewSchedule selectByErrorCodeAndDate(@Param("errorCode") String errorCode,
                                                  @Param("scheduleDate") Date scheduleDate);

    /**
     * 今日复习队列（含逾期项），按排序分降序。
     *
     * <p>排序分 = 落库排序分 + 逾期天数：越拖越靠前，这就是「薄弱度×遗忘风险」。</p>
     *
     * @param childId 孩子 ID
     * @param today   今天
     * @return 队列条目
     */
    @Select("SELECT s.error_code AS errorCode, e.source_module AS sourceModule, e.content_type AS contentType,"
            + " e.content_id AS contentId, e.knowledge_point_code AS knowledgePointCode,"
            + " (s.sort_score + DATEDIFF(#{today}, s.schedule_date)) AS orderWeight,"
            + " s.schedule_date AS queueDate"
            + " FROM study_review_schedule s JOIN study_error_pool e ON e.error_code = s.error_code AND e.del_flag = 0"
            + " WHERE s.child_id = #{childId} AND s.status = 1 AND s.del_flag = 0"
            + " AND s.schedule_date <= #{today} AND e.review_status = 1"
            + " ORDER BY orderWeight DESC, s.seq_no ASC, s.create_time ASC")
    List<org.jeecg.modules.study.error.vo.ReviewQueueItemVO> selectTodayQueue(@Param("childId") String childId,
                                                                              @Param("today") Date today);

    /**
     * 幂等完成排期（CAS：只有仍是待复习状态才会被置为已完成）。
     *
     * @param id          排期主键
     * @param completedAt 完成时间
     * @return 影响行数；0 表示已被其它会话完成
     */
    @Update("UPDATE study_review_schedule SET status = 2, completed_at = #{completedAt}, update_time = NOW()"
            + " WHERE id = #{id} AND status = 1 AND del_flag = 0")
    int markCompleted(@Param("id") String id, @Param("completedAt") Date completedAt);

    /**
     * 未来 7 天排期预览。
     *
     * @param childId 孩子 ID
     * @param from    起始日期（含）
     * @param to      结束日期（含）
     * @return 排期条目
     */
    @Select("SELECT s.error_code AS errorCode, e.source_module AS sourceModule, e.content_type AS contentType,"
            + " e.content_id AS contentId, e.knowledge_point_code AS knowledgePointCode,"
            + " s.sort_score AS orderWeight, s.schedule_date AS queueDate"
            + " FROM study_review_schedule s JOIN study_error_pool e ON e.error_code = s.error_code AND e.del_flag = 0"
            + " WHERE s.child_id = #{childId} AND s.status = 1 AND s.del_flag = 0"
            + " AND s.schedule_date >= #{from} AND s.schedule_date <= #{to} AND e.review_status = 1"
            + " ORDER BY s.schedule_date ASC, orderWeight DESC")
    List<org.jeecg.modules.study.error.vo.ReviewQueueItemVO> selectUpcoming(@Param("childId") String childId,
                                                                            @Param("from") Date from,
                                                                            @Param("to") Date to);
}
