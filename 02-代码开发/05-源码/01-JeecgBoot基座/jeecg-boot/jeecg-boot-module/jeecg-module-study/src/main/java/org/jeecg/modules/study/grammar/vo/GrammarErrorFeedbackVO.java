package org.jeecg.modules.study.grammar.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 错误即入口链路的返回（最小讲解 + 同类例句 + 反例 三件套）
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "错误即入口链路的返回（最小讲解 + 同类例句 + 反例 三件套）")
public class GrammarErrorFeedbackVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 孩子刚写的句子 */
    @Schema(description = "孩子刚写的句子")
    private String originalSentence;

    /** 被点开的那个错误 */
    @Schema(description = "被点开的那个错误")
    private GrammarErrorVO error;

    /** 是否可立即改一次 */
    @Schema(description = "是否可立即改一次")
    private Boolean canRetry;

    /** 允许的即时改正次数 */
    @Schema(description = "允许的即时改正次数")
    private Integer maxRetry;

}
