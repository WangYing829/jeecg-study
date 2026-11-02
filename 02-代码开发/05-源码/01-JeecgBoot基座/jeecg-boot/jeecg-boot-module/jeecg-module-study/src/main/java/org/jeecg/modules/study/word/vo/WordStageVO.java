package org.jeecg.modules.study.word.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 背单词环节视图（词 → 句 → 篇 三层）。
 *
 * <p>PRD §8.3 坑一「只记图不记词」：课内词条<b>必须</b>含一个不依赖图片的回忆环节，
 * 图片只能作辅助。因此每个环节都带 {@code imageRequired} 标记，
 * 其中释义环节恒为 {@code false}——它就是那个无图回忆环节。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "背单词环节视图")
public class WordStageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 环节码 MEANING / SENTENCE / PASSAGE */
    @Schema(description = "环节码")
    private String stageCode;

    /** 环节名 释义 / 造句 / 短文填词 */
    @Schema(description = "环节名")
    private String stageName;

    /** 层级 1释义 2造句 3短文 */
    @Schema(description = "层级")
    private Integer layer;

    /** 是否必须看图才能作答；无图回忆环节恒为 false */
    @Schema(description = "是否必须看图")
    private Boolean imageRequired;

    /** 引导语（不含拼写） */
    @Schema(description = "引导语")
    private String prompt;

    /** 该环节的内容（目标词已挖空） */
    @Schema(description = "环节内容")
    private String content;
}
