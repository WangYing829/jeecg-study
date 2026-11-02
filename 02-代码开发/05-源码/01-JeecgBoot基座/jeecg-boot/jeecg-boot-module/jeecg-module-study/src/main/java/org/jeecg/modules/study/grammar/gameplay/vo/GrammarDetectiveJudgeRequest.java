package org.jeecg.modules.study.grammar.gameplay.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 句子侦探判定入参（孩子指出错的是第几句）。
 *
 * @author jeecg-study
 */
@Data
@Schema(description = "句子侦探判定入参")
public class GrammarDetectiveJudgeRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 回合ID（取题接口下发） */
    @Schema(description = "回合ID")
    private String roundId;

    /** 孩子指出错的是第几句（1 起） */
    @Schema(description = "孩子指出错的是第几句")
    private Integer pickedIndex;

    /** 本关耗时（毫秒，可空） */
    @Schema(description = "本关耗时毫秒")
    private Integer durationMs;

}
