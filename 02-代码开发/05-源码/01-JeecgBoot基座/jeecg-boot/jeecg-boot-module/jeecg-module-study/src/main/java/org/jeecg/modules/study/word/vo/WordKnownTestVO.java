package org.jeecg.modules.study.word.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 熟词测试结果。
 *
 * <p>F-WRD-03 / TC-03-06：首次进入课外线先做词汇量测试，已掌握词直接剔除出队列；
 * 过滤量必须在统计里可见。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "熟词测试结果")
public class WordKnownTestVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 线别 IN课内 / OUT课外 */
    @Schema(description = "线别")
    private String lineType;

    /** 本次标记为熟词的条数 */
    @Schema(description = "本次标记为熟词的条数")
    private Integer markedCount;

    /** 该线别累计被过滤的熟词条数 */
    @Schema(description = "累计被过滤的熟词条数")
    private Integer filteredKnownCount;

    /** 过滤后剩余的队列词条数 */
    @Schema(description = "过滤后剩余的队列词条数")
    private Integer queueWordCount;
}
