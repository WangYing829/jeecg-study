package org.jeecg.modules.study.practice.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.jeecg.modules.study.practice.entity.StudyExplanationFeedback;

/**
 * 讲解申诉记录 Mapper（行为表，按 child_id 隔离）。
 *
 * <p><b>家长之间互不可见</b>：每条查询都同时带 {@code child_id} 与
 * {@code submitter_user_id} 条件 —— 同一家庭里家长 A 只能看到自己提交的那条申诉。</p>
 *
 * @author jeecg-study
 */
@Mapper
public interface StudyExplanationFeedbackMapper extends BaseMapper<StudyExplanationFeedback> {

    /**
     * 按「孩子 + 内容」查申诉记录（幂等键：同一孩子对同一内容只留一条）。
     *
     * @param childId   孩子 ID
     * @param contentId 内容业务ID
     * @return 申诉记录；不存在返回 null
     */
    @Select("SELECT * FROM study_ai_feedback WHERE child_id = #{childId} AND content_id = #{contentId}"
            + " AND del_flag = 0 LIMIT 1")
    StudyExplanationFeedback selectByChildAndContent(@Param("childId") String childId,
                                                     @Param("contentId") String contentId);

    /**
     * 某孩子对某内容的申诉次数（只数本人提交的）。
     *
     * @param childId        孩子 ID
     * @param contentId      内容业务ID
     * @param submitterUserId 提交人
     * @return 条数
     */
    @Select("SELECT COUNT(*) FROM study_ai_feedback WHERE child_id = #{childId}"
            + " AND content_id = #{contentId} AND submitter_user_id = #{submitterUserId} AND del_flag = 0")
    Integer countBySubmitter(@Param("childId") String childId,
                              @Param("contentId") String contentId,
                              @Param("submitterUserId") String submitterUserId);
}
