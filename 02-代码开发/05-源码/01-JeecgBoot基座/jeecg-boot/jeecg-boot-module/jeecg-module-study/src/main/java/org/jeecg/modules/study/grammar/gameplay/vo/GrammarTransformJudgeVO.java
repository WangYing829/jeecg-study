package org.jeecg.modules.study.grammar.gameplay.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 语法变形闯关判定结果（F-GRM-04）。
 *
 * <p>判定口径固定为：<b>规则没查出语法错误</b> + <b>说到了目标表达的实词上</b>，
 * 全程确定性，不调用模型（R-02）。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "语法变形闯关判定结果")
public class GrammarTransformJudgeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 回合ID */
    @Schema(description = "回合ID")
    private String roundId;

    /** 是否过关 */
    @Schema(description = "是否过关")
    private Boolean correct;

    /** 判定来源（恒为 rule） */
    @Schema(description = "判定来源")
    private String judgeSource;

    /** 最小讲解（答错时给） */
    @Schema(description = "最小讲解")
    private String rule;

    /** 同类例句（答错时给） */
    @Schema(description = "同类例句")
    private String example;

    /** 反例（答错时给） */
    @Schema(description = "反例")
    private String counterExample;

    /** 改正后的句子（答错时给） */
    @Schema(description = "改正后的句子")
    private String fix;

    /** 参考表达（过关后给） */
    @Schema(description = "参考表达")
    private String referenceText;

    /** 提示（没过关时给，一句话说明差在哪） */
    @Schema(description = "提示")
    private String hint;

    /** 进入统一错题池后的错题业务ID（没入池时为空） */
    @Schema(description = "错题业务ID")
    private String errorCode;

}
