package org.jeecg.modules.study.mastery.vo;

import java.io.Serializable;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 单个考点的掌握度。
 *
 * <p><b>只有客观状态，没有名次</b>（R-04）。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "考点掌握度")
public class MasteryPointVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 考点业务ID */
    @Schema(description = "考点业务ID")
    private String pointCode;

    /** 考点名称 */
    @Schema(description = "考点名称")
    private String pointName;

    /** 学科编码 */
    @Schema(description = "学科编码")
    private String subjectCode;

    /** 掌握状态 1未知 2学习中 3熟悉 4掌握 */
    @Schema(description = "掌握状态")
    private Integer masteryState;

    /** 该考点下的错题条数 */
    @Schema(description = "错题条数")
    private Integer errorCount;

    /** 最近一次错题时间 */
    @Schema(description = "最近一次错题时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastErrorAt;

    /** 错因分布（只给维度与条数，不含任何作答内容） */
    @Schema(description = "错因分布")
    private java.util.List<MasteryCauseCountVO> causeCounts;
}
