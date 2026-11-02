package org.jeecg.modules.study.parent.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 薄弱考点条目。
 *
 * <p>只给考点、掌握状态与错题条数，<b>不给讲解、也不给教学建议</b>（R-04 / TC-07-03）。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "薄弱考点条目")
public class ParentWeakPointVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 考点业务ID */
    @Schema(description = "考点业务ID")
    private String pointCode;

    /** 考点名称 */
    @Schema(description = "考点名称")
    private String pointName;

    /** 掌握状态 1未知 2学习中 3熟悉 4掌握 */
    @Schema(description = "掌握状态 1未知 2学习中 3熟悉 4掌握")
    private Integer masteryState;

    /** 错题条数 */
    @Schema(description = "错题条数")
    private Integer errorCount;
}
