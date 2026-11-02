package org.jeecg.modules.study.grammar.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 错误检测入参
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "错误检测入参")
public class GrammarDetectRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 孩子写的句子 */
    @Schema(description = "孩子写的句子")
    private String sentence;

    /** 指定语法点业务ID；为空则自动匹配 */
    @Schema(description = "指定语法点业务ID；为空则自动匹配")
    private String grammarCode;

    /** 错因（五维之一）；为空按概念不清归类 */
    @Schema(description = "错因（五维之一）；为空按概念不清归类")
    private String errorCause;

}
