package org.jeecg.modules.study.context.vo;

import java.io.Serializable;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 当前上下文响应。
 *
 * <p>{@code configured=false} 表示尚未配置，前端应引导到配置页而**不是报错**（TC-01-01）。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "当前上下文响应")
public class ContextVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 上下文主键 ID */
    @Schema(description = "上下文主键 ID")
    private String contextId;

    /** 所属孩子 ID */
    @Schema(description = "所属孩子 ID")
    private String childId;

    /** 业务ID CTX-000001 */
    @Schema(description = "业务ID CTX-000001")
    private String contextCode;

    /** 省级行政区编码 */
    @Schema(description = "省级行政区编码")
    private String provinceCode;

    /** 省级行政区名称 */
    @Schema(description = "省级行政区名称")
    private String provinceName;

    /** 学段编码 */
    @Schema(description = "学段编码")
    private String stageCode;

    /** 学段名称 */
    @Schema(description = "学段名称")
    private String stageName;

    /** 年级编码 */
    @Schema(description = "年级编码")
    private String gradeCode;

    /** 年级名称 */
    @Schema(description = "年级名称")
    private String gradeName;

    /** 教材版本 ID */
    @Schema(description = "教材版本 ID")
    private String textbookVersionId;

    /** 教材版本名称 */
    @Schema(description = "教材版本名称")
    private String textbookVersionName;

    /** 护眼提醒间隔（分钟） */
    @Schema(description = "护眼提醒间隔（分钟）")
    private Integer eyeIntervalMin;

    /** 是否已配置 */
    @Schema(description = "是否已配置")
    private Boolean configured;

    /** 生效时间 */
    @Schema(description = "生效时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date effectiveFrom;

}
