package org.jeecg.modules.study.practice.vo;

import java.io.Serializable;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 解析申诉的当前状态（R-02 / F-FAM-08）。
 *
 * <p><b>不暴露提交人身份</b>：家长之间互不可见，响应里也不出现任何家长标识。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "解析申诉状态")
public class ExplanationFeedbackVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 内容业务ID */
    @Schema(description = "内容业务ID")
    private String contentId;

    /** 是否已表示不同意 */
    @Schema(description = "是否已表示不同意")
    private Boolean disagreed = Boolean.FALSE;

    /** 申诉次数（只数本人提交的） */
    @Schema(description = "申诉次数")
    private Integer feedbackCount = 0;

    /** 最近一次申诉理由 */
    @Schema(description = "最近一次申诉理由")
    private String latestReason;

    /** 最近一次处理状态 NONE/PENDING/ACCEPTED/REJECTED */
    @Schema(description = "最近一次处理状态")
    private String latestStatus = "NONE";

    /** 最近一次申诉时间 */
    @Schema(description = "最近一次申诉时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date latestFeedbackTime;
}
