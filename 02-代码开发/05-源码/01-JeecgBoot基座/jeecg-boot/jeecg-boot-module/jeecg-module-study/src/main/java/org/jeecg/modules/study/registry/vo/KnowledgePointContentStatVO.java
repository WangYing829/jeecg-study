package org.jeecg.modules.study.registry.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 考点下挂内容统计（五类）。
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "考点下挂内容统计（题目/单词/母题/语法点/写作）")
public class KnowledgePointContentStatVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 题目数 */
    @Schema(description = "题目数")
    private int question;

    /** 单词数 */
    @Schema(description = "单词数")
    private int word;

    /** 母题数 */
    @Schema(description = "母题数")
    private int mother;

    /** 语法点数 */
    @Schema(description = "语法点数")
    private int grammar;

    /** 写作数 */
    @Schema(description = "写作数")
    private int writing;

}
