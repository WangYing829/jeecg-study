package org.jeecg.modules.study.grammar.gameplay.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 句子侦探判定结果（F-GRM-04）。
 *
 * <p>判定完全由规则算出（{@code judgeSource} 恒为 {@code rule}），
 * 答完才给讲解——顺序不能颠倒（R-01）。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "句子侦探判定结果")
public class GrammarDetectiveJudgeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 回合ID */
    @Schema(description = "回合ID")
    private String roundId;

    /** 是否找对了 */
    @Schema(description = "是否找对了")
    private Boolean correct;

    /** 孩子指出的是第几句 */
    @Schema(description = "孩子指出的是第几句")
    private Integer pickedIndex;

    /** 错的是第几句 */
    @Schema(description = "错的是第几句")
    private Integer wrongPosition;

    /** 错句正文 */
    @Schema(description = "错句正文")
    private String wrongSentence;

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

    /** 判定来源（恒为 rule） */
    @Schema(description = "判定来源")
    private String judgeSource;

    /** 进入统一错题池后的错题业务ID（没入池时为空） */
    @Schema(description = "错题业务ID")
    private String errorCode;

}
