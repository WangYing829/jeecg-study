package org.jeecg.modules.study.parent.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 提醒推送偏好更新入参（F-PAR-04）。
 *
 * <p><b>不传字段 = 不修改</b>（与《接口设计说明书》§1.5 的空值约定一致）。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "提醒推送偏好更新入参")
public class NotificationPreferenceRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 孩子业务ID（可空；为空表示「全部孩子」这一档） */
    @Schema(description = "孩子业务ID")
    private String childCode;

    /** 复习提醒开关 */
    @Schema(description = "复习提醒开关")
    private Boolean reviewRemindEnabled;

    /** 复习提醒推送时间 HH:mm */
    @Schema(description = "复习提醒推送时间")
    private String reviewRemindTime;

    /** 护眼提醒开关 */
    @Schema(description = "护眼提醒开关")
    private Boolean eyeRemindEnabled;

    /** 护眼提醒推送时间 HH:mm */
    @Schema(description = "护眼提醒推送时间")
    private String eyeRemindTime;

    /** 推送渠道 APP/WECHAT/SMS */
    @Schema(description = "推送渠道")
    private String pushChannel;
}
