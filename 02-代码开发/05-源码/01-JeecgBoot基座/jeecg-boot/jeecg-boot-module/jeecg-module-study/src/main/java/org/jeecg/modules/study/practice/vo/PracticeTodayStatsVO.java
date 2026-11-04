package org.jeecg.modules.study.practice.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 今日练习统计。
 *
 * <p><b>只有客观计数</b>，没有任何名次类字段（R-04）。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "今日练习统计")
public class PracticeTodayStatsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 统计日期 yyyy-MM-dd */
    @Schema(description = "统计日期")
    private String date;

    /** 作答总题数 */
    @Schema(description = "作答总题数")
    private Integer totalCount = 0;

    /** 做对题数 */
    @Schema(description = "做对题数")
    private Integer correctCount = 0;

    /** 做错题数 */
    @Schema(description = "做错题数")
    private Integer wrongCount = 0;

    /** 累计用时（分钟） */
    @Schema(description = "累计用时（分钟）")
    private Integer durationMinutes = 0;

    /** 涉及的模块数 */
    @Schema(description = "涉及的模块数")
    private Integer moduleCount = 0;
}
