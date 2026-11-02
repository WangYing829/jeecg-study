package org.jeecg.modules.study.grammar.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import java.util.List;

/**
 * 错误检测结果
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "错误检测结果")
public class GrammarDetectResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 孩子刚写的句子 */
    @Schema(description = "孩子刚写的句子")
    private String originalSentence;

    /** 是否检出错误 */
    @Schema(description = "是否检出错误")
    private Boolean hasError;

    /** 来源：rule */
    @Schema(description = "来源：rule")
    private String source;

    /** 检出的错误 */
    @Schema(description = "检出的错误")
    private List<GrammarErrorVO> errors;

    /** 是否可立即改一次 */
    @Schema(description = "是否可立即改一次")
    private Boolean canRetry;

    /** 允许的即时改正次数 */
    @Schema(description = "允许的即时改正次数")
    private Integer maxRetry;

    /** 统一错题池里的错句业务ID */
    @Schema(description = "统一错题池里的错句业务ID")
    private String errorCode;

    /** 讲解状态：NORMAL / UNAVAILABLE */
    @Schema(description = "讲解状态：NORMAL / UNAVAILABLE")
    private String explainStatus;

}
