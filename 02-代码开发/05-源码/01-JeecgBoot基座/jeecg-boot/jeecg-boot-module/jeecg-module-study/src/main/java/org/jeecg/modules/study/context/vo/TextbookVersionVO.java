package org.jeecg.modules.study.context.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 教材版本下拉项。
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "教材版本下拉项")
public class TextbookVersionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 教材版本 ID（写入上下文时使用） */
    @Schema(description = "教材版本 ID")
    private String versionId;

    /** 教材版本编码 */
    @Schema(description = "教材版本编码")
    private String versionCode;

    /** 教材版本名称（人教版…） */
    @Schema(description = "教材版本名称")
    private String versionName;

    /** 出版社（人教/苏教/北师大/浙教/沪教/京教/鲁教） */
    @Schema(description = "出版社")
    private String publisher;

    /** 适用地区（逗号分隔） */
    @Schema(description = "适用地区（逗号分隔）")
    private String regionScope;

    /** 适用学段 */
    @Schema(description = "适用学段")
    private String stageCode;

    /** 排序号 */
    @Schema(description = "排序号")
    private Integer sortNo;

}
