package org.jeecg.modules.study.grammar.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 检出的语法错误
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "检出的语法错误")
public class GrammarErrorVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 错误标记ID */
    @Schema(description = "错误标记ID")
    private String errorId;

    /** 错误片段起始位置 */
    @Schema(description = "错误片段起始位置")
    private Integer index;

    /** 错误片段长度 */
    @Schema(description = "错误片段长度")
    private Integer length;

    /** 错误片段（原文） */
    @Schema(description = "错误片段（原文）")
    private String fragment;

    /** 对应语法点业务ID */
    @Schema(description = "对应语法点业务ID")
    private String grammarCode;

    /** 最小讲解 */
    @Schema(description = "最小讲解")
    private String rule;

    /** 同类例句 */
    @Schema(description = "同类例句")
    private String example;

    /** 反例 */
    @Schema(description = "反例")
    private String counterExample;

    /** 改正后的句子 */
    @Schema(description = "改正后的句子")
    private String fix;

}
