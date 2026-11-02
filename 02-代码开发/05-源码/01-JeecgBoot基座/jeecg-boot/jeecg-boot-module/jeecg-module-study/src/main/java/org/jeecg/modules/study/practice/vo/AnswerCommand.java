package org.jeecg.modules.study.practice.vo;

import java.io.Serializable;
import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 提交作答入参。
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "提交作答入参")
public class AnswerCommand implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 练习会话 ID */
    @Schema(description = "练习会话 ID")
    private String sessionId;

    /** 内容业务ID */
    @Schema(description = "内容业务ID")
    private String contentId;

    /** 孩子作答 */
    @Schema(description = "孩子作答")
    private String answer;

    /** 本题耗时（毫秒） */
    @Schema(description = "本题耗时")
    private Integer durationMs;

    /** 错因（答错时必填，缺失返回 4001） */
    @Schema(description = "错因")
    private String errorCause;

    /** 步骤级错因打标 */
    @Schema(description = "步骤级错因打标")
    private java.util.List<StepTag> stepTags;

    /** 数值容差覆盖值（不填取题目配置，再不填取规则默认值） */
    @Schema(description = "数值容差覆盖值")
    private BigDecimal tolerance;

    /** 判定题型覆盖值（不填按题目题型推导） */
    @Schema(description = "判定题型覆盖值")
    private String contentType;
}
