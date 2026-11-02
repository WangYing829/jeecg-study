package org.jeecg.modules.study.parent.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 周报逐日明细条目。
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "周报逐日明细条目")
public class ParentDailyItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 日期 yyyy-MM-dd */
    @Schema(description = "日期")
    private String date;

    /** 当天有效用眼分钟 */
    @Schema(description = "当天有效用眼分钟")
    private Integer eyeMinutes;

    /** 当天休息提醒触发次数 */
    @Schema(description = "当天休息提醒触发次数")
    private Integer restCount;

    /** 当天新增错题条数 */
    @Schema(description = "当天新增错题条数")
    private Integer errorCount;

    /** 当天作文记录数 */
    @Schema(description = "当天作文记录数")
    private Integer writingCount;
}
