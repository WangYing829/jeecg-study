package org.jeecg.modules.study.parent.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 掌握度分布视图（1未知 2学习中 3熟悉 4掌握 的计数）。
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "掌握度分布视图")
public class ParentMasteryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 已掌握条数 */
    @Schema(description = "已掌握条数")
    private Integer mastered;

    /** 已熟悉条数 */
    @Schema(description = "已熟悉条数")
    private Integer familiar;

    /** 学习中条数 */
    @Schema(description = "学习中条数")
    private Integer learning;

    /** 未知条数 */
    @Schema(description = "未知条数")
    private Integer unknown;
}
