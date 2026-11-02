package org.jeecg.modules.study.grammar.gameplay.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 语法变形闯关判定入参（孩子写的句子）。
 *
 * @author jeecg-study
 */
@Data
@Schema(description = "语法变形闯关判定入参")
public class GrammarTransformJudgeRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 回合ID（取题接口下发） */
    @Schema(description = "回合ID")
    private String roundId;

    /** 孩子写的英文句子 */
    @Schema(description = "孩子写的英文句子")
    private String sentence;

    /** 本关耗时（毫秒，可空） */
    @Schema(description = "本关耗时毫秒")
    private Integer durationMs;

}
