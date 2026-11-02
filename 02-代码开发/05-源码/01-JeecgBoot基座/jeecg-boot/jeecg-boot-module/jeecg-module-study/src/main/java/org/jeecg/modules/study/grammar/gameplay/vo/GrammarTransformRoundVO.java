package org.jeecg.modules.study.grammar.gameplay.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 语法变形闯关取题结果（F-GRM-04）。
 *
 * <p>只给中文意思与任务说明，<b>不给目标表达</b>——写出来才算（R-01）。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "语法变形闯关取题结果")
public class GrammarTransformRoundVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 回合ID（题目业务ID） */
    @Schema(description = "回合ID")
    private String roundId;

    /** 关联语法点业务ID */
    @Schema(description = "关联语法点业务ID")
    private String grammarCode;

    /** 中文意思（这一关要写什么） */
    @Schema(description = "中文意思")
    private String meaning;

    /** 任务说明 */
    @Schema(description = "任务说明")
    private String prompt;

    /** 目标表达所在的语境例句业务ID */
    @Schema(description = "目标例句业务ID")
    private String targetSentenceCode;

}
