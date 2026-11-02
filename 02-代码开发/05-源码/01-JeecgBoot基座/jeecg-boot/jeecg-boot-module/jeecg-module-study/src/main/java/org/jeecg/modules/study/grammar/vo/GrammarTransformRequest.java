package org.jeecg.modules.study.grammar.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 句子变形器入参
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "句子变形器入参")
public class GrammarTransformRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 中文意思 */
    @Schema(description = "中文意思")
    private String zhMeaning;

    /** 指定语法点业务ID；为空则自动匹配 */
    @Schema(description = "指定语法点业务ID；为空则自动匹配")
    private String grammarCode;

}
