package org.jeecg.modules.study.writing.vo;

import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 佳作拆解视图（一个拆解 = 六层）。
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "佳作拆解")
public class WritingDissectionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 拆解集编码 */
    @Schema(description = "拆解集编码")
    private String dissectionCode;

    /** 题目 */
    @Schema(description = "题目")
    private String title;

    /** 题材 */
    @Schema(description = "题材")
    private String genre;

    /** 学段 */
    @Schema(description = "学段")
    private String stageCode;

    /** 六层内容（按题目钩子 → 结尾回扣的顺序） */
    @Schema(description = "六层内容")
    private List<WritingDissectionLayerVO> layers;
}
