package org.jeecg.modules.study.parent.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 写作趋势条目（按天的作文记录数）。
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "写作趋势条目")
public class ParentWritingTrendVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 日期 yyyy-MM-dd */
    @Schema(description = "日期")
    private String date;

    /** 当天作文记录数 */
    @Schema(description = "当天作文记录数")
    private Integer writingCount;
}
