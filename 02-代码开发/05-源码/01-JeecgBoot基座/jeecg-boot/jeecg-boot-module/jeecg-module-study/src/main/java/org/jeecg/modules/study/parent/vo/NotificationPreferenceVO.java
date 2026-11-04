package org.jeecg.modules.study.parent.vo;

import java.io.Serializable;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 提醒推送偏好（F-PAR-04）。
 *
 * <p>家长端资源域里唯一的写入口就是「提醒偏好」，
 * 但它<b>不含任何孩子的学习数据</b>，也不含名次类字段（R-04）。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "提醒推送偏好")
public class NotificationPreferenceVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 孩子业务ID（空串 = 全部孩子） */
    @Schema(description = "孩子业务ID")
    private String childCode;

    /** 复习提醒开关 */
    @Schema(description = "复习提醒开关")
    private Boolean reviewRemindEnabled = Boolean.TRUE;

    /** 复习提醒推送时间 HH:mm */
    @Schema(description = "复习提醒推送时间")
    private String reviewRemindTime;

    /** 护眼提醒开关 */
    @Schema(description = "护眼提醒开关")
    private Boolean eyeRemindEnabled = Boolean.TRUE;

    /** 护眼提醒推送时间 HH:mm */
    @Schema(description = "护眼提醒推送时间")
    private String eyeRemindTime;

    /** 推送渠道 APP/WECHAT/SMS */
    @Schema(description = "推送渠道")
    private String pushChannel;

    /** 最后更新时间 */
    @Schema(description = "最后更新时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
}
