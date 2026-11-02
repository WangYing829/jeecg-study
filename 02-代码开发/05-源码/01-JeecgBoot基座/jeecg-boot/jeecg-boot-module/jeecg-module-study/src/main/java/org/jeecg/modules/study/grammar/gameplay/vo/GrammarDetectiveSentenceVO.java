package org.jeecg.modules.study.grammar.gameplay.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 句子侦探的一回合里的第 N 句。
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "句子侦探的单句")
public class GrammarDetectiveSentenceVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 位置（1 起） */
    @Schema(description = "位置")
    private Integer position;

    /** 句子正文 */
    @Schema(description = "句子正文")
    private String text;

    /** 例句业务ID（错句没有） */
    @Schema(description = "例句业务ID")
    private String sentenceCode;

}
