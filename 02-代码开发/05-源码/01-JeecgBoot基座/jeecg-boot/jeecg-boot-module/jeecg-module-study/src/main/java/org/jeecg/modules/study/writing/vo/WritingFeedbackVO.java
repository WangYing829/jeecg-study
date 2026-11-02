package org.jeecg.modules.study.writing.vo;

import java.io.Serializable;
import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 家长反馈视图。
 *
 * <p><b>只给家长看</b>：孩子的任何接口都不会返回这个视图，
 * 家长反馈不进入孩子的练习数据流（TC-06-09 / R-04）。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "家长反馈")
public class WritingFeedbackVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 反馈业务ID */
    @Schema(description = "反馈业务ID")
    private String feedbackCode;

    /** 反馈对象类型 */
    @Schema(description = "反馈对象类型")
    private String targetType;

    /** 反馈对象业务ID */
    @Schema(description = "反馈对象业务ID")
    private String targetCode;

    /** 反馈内容 */
    @Schema(description = "反馈内容")
    private String content;

    /** 提交时间 */
    @Schema(description = "提交时间")
    private Date createTime;
}
