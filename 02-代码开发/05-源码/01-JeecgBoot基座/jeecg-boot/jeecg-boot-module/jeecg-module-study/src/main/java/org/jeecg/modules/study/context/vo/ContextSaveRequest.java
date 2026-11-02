package org.jeecg.modules.study.context.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 创建 / 切换上下文入参。
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "创建/切换上下文入参")
public class ContextSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 所属孩子业务ID（CHD-000001）；不传则取当前身份唯一可见的孩子 */
    @Schema(description = "所属孩子业务ID；不传则取当前身份唯一可见的孩子")
    private String childCode;

    /** 省级行政区编码（如 32 = 江苏） */
    @Schema(description = "省级行政区编码")
    private String provinceCode;

    /** 省级行政区名称；不传则按编码自动推导 */
    @Schema(description = "省级行政区名称；不传则按编码自动推导")
    private String provinceName;

    /** 学段编码 PRIMARY/JUNIOR/SENIOR */
    @Schema(description = "学段编码 PRIMARY/JUNIOR/SENIOR")
    private String stageCode;

    /** 年级编码 G1..G12，必须落在学段内 */
    @Schema(description = "年级编码 G1..G12，必须落在学段内")
    private String gradeCode;

    /** 教材版本 ID */
    @Schema(description = "教材版本 ID")
    private String textbookVersionId;

    /** 护眼提醒间隔（分钟）；不传用默认值 */
    @Schema(description = "护眼提醒间隔（分钟）；不传用默认值")
    private Integer eyeIntervalMin;

}
