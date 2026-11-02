package org.jeecg.modules.study.grammar.gameplay.vo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 句子侦探取题结果（F-GRM-04）。
 *
 * <p><b>只给四句话，不给哪句错</b>：错句位置与讲解都在服务端，
 * 孩子先作答、作答后才给讲解（R-01）。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "句子侦探取题结果")
public class GrammarDetectiveRoundVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 回合ID（题目业务ID） */
    @Schema(description = "回合ID")
    private String roundId;

    /** 关联语法点业务ID */
    @Schema(description = "关联语法点业务ID")
    private String grammarCode;

    /** 本回合的句子 */
    @Schema(description = "本回合的句子")
    private List<GrammarDetectiveSentenceVO> sentences = new ArrayList<>();

    /** 句子总数 */
    @Schema(description = "句子总数")
    private Integer total;

}
