package org.jeecg.modules.study.word.vo;

import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 到期复习词视图。
 *
 * <p>{@code upgradeItems} 是「课内背错的词升级成的课外线优先复习项」
 * （F-WRD-05 / TC-03-07）。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "到期复习词视图")
public class WordDueVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 线别 IN课内 / OUT课外 */
    @Schema(description = "线别")
    private String lineType;

    /** 到期词总数 */
    @Schema(description = "到期词总数")
    private Integer totalDue;

    /** 熟词过滤条数 */
    @Schema(description = "熟词过滤条数")
    private Integer filteredKnownCount;

    /** 到期复习词 */
    @Schema(description = "到期复习词")
    private List<WordListItemVO> items;

    /** 课内错词升级来的课外优先复习项 */
    @Schema(description = "课外优先复习项")
    private List<WordListItemVO> upgradeItems;
}
