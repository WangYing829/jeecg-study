package org.jeecg.modules.study.writing.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 家长一句话反馈入参。
 *
 * <p>{@code content} 长度上限 {@link org.jeecg.modules.study.writing.constant.WritingConstants#MAX_FEEDBACK_LENGTH} 字，
 * 超出直接拒绝且不落库（TC-06-08）。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "家长一句话反馈入参")
public class WritingFeedbackRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 反馈对象类型：WRITING */
    @Schema(description = "反馈对象类型")
    private String targetType;

    /** 反馈对象业务ID（初稿的业务ID） */
    @Schema(description = "反馈对象业务ID")
    private String targetCode;

    /** 反馈内容（不超过 100 字） */
    @Schema(description = "反馈内容")
    private String content;
}
