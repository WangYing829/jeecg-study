package org.jeecg.modules.study.grammar.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import java.util.List;

/**
 * 句子变形器输出（五态）
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "句子变形器输出（五态）")
public class GrammarTransformResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 中文意思 */
    @Schema(description = "中文意思")
    private String meaning;

    /** 命中的语法点业务ID */
    @Schema(description = "命中的语法点业务ID")
    private String grammarCode;

    /** 来源：library / unavailable */
    @Schema(description = "来源：library / unavailable")
    private String source;

    /** 五种形态 */
    @Schema(description = "五种形态")
    private List<GrammarTransformVO> variants;

    /** 讲解状态：NORMAL / UNAVAILABLE */
    @Schema(description = "讲解状态：NORMAL / UNAVAILABLE")
    private String explainStatus;

    /** 提示文案 */
    @Schema(description = "提示文案")
    private String hint;

}
