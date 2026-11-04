package org.jeecg.modules.study.parent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.jeecg.modules.study.parent.entity.StudyNotificationPreference;

/**
 * 家长提醒推送偏好 Mapper。
 *
 * <p><b>隔离口径</b>：每条查询都带 {@code parent_user_id} 条件，
 * 且 {@code parent_user_id} 只能由服务端从会话取（不接受前端传值）。</p>
 *
 * @author jeecg-study
 */
@Mapper
public interface ParentNotificationPreferenceMapper extends BaseMapper<StudyNotificationPreference> {

    /**
     * 按「家长 + 孩子维度」查偏好。
     *
     * @param parentUserId 家长账号 ID
     * @param childCode    孩子业务ID（空串 = 全部孩子）
     * @return 偏好行；不存在返回 null
     */
    @Select("SELECT * FROM study_notification_preference WHERE parent_user_id = #{parentUserId}"
            + " AND child_code = #{childCode} AND del_flag = 0 LIMIT 1")
    StudyNotificationPreference selectByParentAndChild(@Param("parentUserId") String parentUserId,
                                                      @Param("childCode") String childCode);
}
