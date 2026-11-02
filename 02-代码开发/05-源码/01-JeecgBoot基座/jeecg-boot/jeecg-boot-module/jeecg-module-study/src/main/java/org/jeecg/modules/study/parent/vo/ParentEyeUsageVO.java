package org.jeecg.modules.study.parent.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 家长端用眼情况视图。
 *
 * <p>家长端<b>可见</b>用眼时长与休息次数（R-03 / R03-6），
 * 这与孩子端「不显示用眼时长数字」的口径相反且互不影响。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "家长端用眼情况视图")
public class ParentEyeUsageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 今日有效用眼分钟 */
    @Schema(description = "今日有效用眼分钟")
    private Integer todayMinutes;

    /** 近 7 天有效用眼分钟 */
    @Schema(description = "近 7 天有效用眼分钟")
    private Integer weekMinutes;

    /** 近 7 天单次会话平均用眼分钟 */
    @Schema(description = "近 7 天单次会话平均用眼分钟")
    private Integer avgSessionMinutes;

    /** 近 7 天休息提醒触发次数 */
    @Schema(description = "近 7 天休息提醒触发次数")
    private Integer restCount;

    /** 近 7 天休息确认次数 */
    @Schema(description = "近 7 天休息确认次数")
    private Integer restConfirmedCount;
}
