package org.jeecg.modules.study.word.vo;

import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 词表视图（分页结果）。
 *
 * <p>{@code filteredKnownCount} 是熟词过滤条数（F-WRD-03）：已判定掌握的词
 * 不进队列，但过滤量必须在统计里可见。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "词表视图")
public class WordListVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 线别 IN课内 / OUT课外 */
    @Schema(description = "线别")
    private String lineType;

    /** 教材版本ID */
    @Schema(description = "教材版本ID")
    private String textbookVersionId;

    /** 单元编码 */
    @Schema(description = "单元编码")
    private String unitCode;

    /** 过滤前总条数 */
    @Schema(description = "过滤前总条数")
    private Integer total;

    /** 熟词过滤条数 */
    @Schema(description = "熟词过滤条数")
    private Integer filteredKnownCount;

    /** 当前页词条 */
    @Schema(description = "当前页词条")
    private List<WordListItemVO> items;
}
