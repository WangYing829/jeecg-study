package org.jeecg.modules.study.error.vo;

import java.io.Serializable;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 高频错题条目。
 *
 * <p><b>只有客观计数，没有名次</b>（R-04）：顺序由错题条数决定，
 * 但响应里不出现任何名次类字段。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "高频错题条目")
public class ErrorHotStatisticVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 错题业务ID */
    @Schema(description = "错题业务ID")
    private String errorCode;

    /** 来源模块 */
    @Schema(description = "来源模块")
    private String sourceModule;

    /** 内容类型 */
    @Schema(description = "内容类型")
    private String contentType;

    /** 关联内容业务ID */
    @Schema(description = "关联内容业务ID")
    private String contentId;

    /** 考点业务ID */
    @Schema(description = "考点业务ID")
    private String knowledgePointCode;

    /** 考点名称 */
    @Schema(description = "考点名称")
    private String knowledgePointName;

    /** 错题条数 */
    @Schema(description = "错题条数")
    private Integer errorCount;

    /** 掌握状态 1未知 2学习中 3熟悉 4掌握 */
    @Schema(description = "掌握状态")
    private Integer masteryState;

    /** 最近一次错题时间 */
    @Schema(description = "最近一次错题时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastErrorAt;
}
