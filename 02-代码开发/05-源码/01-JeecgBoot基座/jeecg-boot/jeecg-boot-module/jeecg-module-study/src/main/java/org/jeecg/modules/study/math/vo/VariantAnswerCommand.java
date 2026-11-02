package org.jeecg.modules.study.math.vo;

import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 变式作答入参（TC-04-09）。
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "变式作答入参")
public class VariantAnswerCommand implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 孩子的答案 */
    @Schema(description = "孩子的答案")
    private String answer;

    /** 错因（答错时必填，缺失返回 4001） */
    @Schema(description = "错因")
    private String errorCause;

    /** 步骤级错因打标（定位到第几步） */
    @Schema(description = "步骤级错因打标")
    private List<StepTagVO> stepTags;

    /** 本题耗时（毫秒） */
    @Schema(description = "本题耗时")
    private Integer durationMs;
}
