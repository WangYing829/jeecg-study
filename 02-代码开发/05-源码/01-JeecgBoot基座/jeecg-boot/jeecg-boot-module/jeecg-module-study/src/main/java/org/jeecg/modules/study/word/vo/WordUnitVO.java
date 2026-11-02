package org.jeecg.modules.study.word.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 课内单元视图。
 *
 * <p>「教材版本 + 单元」是课内词表的唯一入口（F-WRD-01）：
 * 一个教材版本的一个单元对应一组词。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "课内单元视图")
public class WordUnitVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 教材版本ID */
    @Schema(description = "教材版本ID")
    private String textbookVersionId;

    /** 单元编码 */
    @Schema(description = "单元编码")
    private String unitCode;

    /** 单元内顺序 */
    @Schema(description = "单元内顺序")
    private Integer unitOrder;

    /** 单元词条数（过滤前） */
    @Schema(description = "单元词条数")
    private Integer wordCount;

    /** 线别 IN课内 / OUT课外 */
    @Schema(description = "线别")
    private String lineType;
}
