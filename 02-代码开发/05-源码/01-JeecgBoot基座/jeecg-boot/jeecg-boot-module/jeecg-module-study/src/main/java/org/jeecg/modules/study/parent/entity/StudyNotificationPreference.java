package org.jeecg.modules.study.parent.entity;

import java.io.Serializable;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecg.common.system.base.entity.JeecgEntity;

/**
 * 家长提醒推送偏好（F-PAR-04）。
 *
 * <p><b>维度</b>：家长账号 + 孩子（{@code childCode} 为空串 = 该家长的全部孩子）。
 * {@code parentUserId} 来自会话，不接受前端传值。</p>
 *
 * @author jeecg-study
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "家长提醒推送偏好")
@TableName("study_notification_preference")
public class StudyNotificationPreference extends JeecgEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 家长账号 sys_user.id */
    @Schema(description = "家长账号 ID")
    @TableField(value = "parent_user_id")
    private String parentUserId;

    /** 孩子业务ID（空串 = 全部孩子） */
    @Schema(description = "孩子业务ID")
    @TableField(value = "child_code")
    private String childCode;

    /** 复习提醒开关 1开 0关 */
    @Schema(description = "复习提醒开关")
    @TableField(value = "review_remind_enabled")
    private Integer reviewRemindEnabled;

    /** 复习提醒推送时间 */
    @Schema(description = "复习提醒推送时间")
    @TableField(value = "review_remind_time")
    private String reviewRemindTime;

    /** 护眼提醒开关 1开 0关 */
    @Schema(description = "护眼提醒开关")
    @TableField(value = "eye_remind_enabled")
    private Integer eyeRemindEnabled;

    /** 护眼提醒推送时间 */
    @Schema(description = "护眼提醒推送时间")
    @TableField(value = "eye_remind_time")
    private String eyeRemindTime;

    /** 推送渠道 APP/WECHAT/SMS */
    @Schema(description = "推送渠道")
    @TableField(value = "push_channel")
    private String pushChannel;

    /** 删除标记 0正常 1删除 */
    @TableLogic
    @TableField(value = "del_flag")
    private Integer delFlag;
}
