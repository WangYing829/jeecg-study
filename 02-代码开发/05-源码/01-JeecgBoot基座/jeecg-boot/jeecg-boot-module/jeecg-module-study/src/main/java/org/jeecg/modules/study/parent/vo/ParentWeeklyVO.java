package org.jeecg.modules.study.parent.vo;

import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 家长端周报视图（近 7 天，逐日展开）。
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "家长端周报视图")
public class ParentWeeklyVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 孩子业务ID */
    @Schema(description = "孩子业务ID")
    private String childCode;

    /** 起始日期 yyyy-MM-dd */
    @Schema(description = "起始日期")
    private String fromDate;

    /** 截止日期 yyyy-MM-dd */
    @Schema(description = "截止日期")
    private String toDate;

    /** 有学习记录的天数 */
    @Schema(description = "有学习记录的天数")
    private Integer activeDays;

    /** 有效用眼分钟合计 */
    @Schema(description = "有效用眼分钟合计")
    private Integer totalEyeMinutes;

    /** 休息提醒触发次数合计 */
    @Schema(description = "休息提醒触发次数合计")
    private Integer totalRestCount;

    /** 错题条数合计 */
    @Schema(description = "错题条数合计")
    private Integer totalErrorCount;

    /** 作文记录数合计 */
    @Schema(description = "作文记录数合计")
    private Integer totalWritingCount;

    /** 逐日明细 */
    @Schema(description = "逐日明细")
    private List<ParentDailyItemVO> days;
}
