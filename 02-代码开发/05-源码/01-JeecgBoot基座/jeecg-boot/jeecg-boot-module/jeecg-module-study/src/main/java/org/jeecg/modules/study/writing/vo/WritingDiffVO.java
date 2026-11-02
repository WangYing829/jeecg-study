package org.jeecg.modules.study.writing.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 对照式批改的一条差异。
 *
 * <p>一条差异 = <b>维度 + 说明 + 建议</b>，不含任何分数或等级（R-04 / TC-06-04）。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "批改差异条目")
public class WritingDiffVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 维度：STRUCTURE / MATERIAL / HAND / CONNECTOR / SENTENCE_LENGTH / LENGTH */
    @Schema(description = "维度")
    private String dimension;

    /** 这条差异的标题 */
    @Schema(description = "标题")
    private String title;

    /** 这条差异的说明（对照同题材佳作差在哪） */
    @Schema(description = "说明")
    private String detail;

    /** 怎么改 */
    @Schema(description = "建议")
    private String suggestion;
}
