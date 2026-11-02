package org.jeecg.modules.study.registry.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 坐标树节点响应。
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "坐标树节点响应")
public class KnowledgePointVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 节点 ID */
    @Schema(description = "节点 ID")
    private String id;

    /** 业务ID KPT-{学科码}-{序号} */
    @Schema(description = "业务ID KPT-{学科码}-{序号}")
    private String code;

    /** 父节点业务ID */
    @Schema(description = "父节点业务ID")
    private String parentCode;

    /** 层级 1考纲 2学科 3单元 4考点 */
    @Schema(description = "层级 1考纲 2学科 3单元 4考点")
    private Integer level;

    /** 学科编码 */
    @Schema(description = "学科编码")
    private String subjectCode;

    /** 名称 */
    @Schema(description = "名称")
    private String name;

    /** 物化路径 */
    @Schema(description = "物化路径")
    private String path;

    /** 题型 */
    @Schema(description = "题型")
    private String questionType;

    /** 分值权重 */
    @Schema(description = "分值权重")
    private BigDecimal scoreWeight;

    /** 常见错因 */
    @Schema(description = "常见错因")
    private List<String> errorTypes = new ArrayList<>();

    /** 母题业务ID */
    @Schema(description = "母题业务ID")
    private String motherQuestionCode;

    /** 版本 */
    @Schema(description = "版本")
    private String version;

    /** 生效日期 */
    @Schema(description = "生效日期")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    private Date effectiveFrom;

    /** 失效日期（NULL = 现行） */
    @Schema(description = "失效日期（NULL = 现行）")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    private Date invalidAt;

    /** 状态 1现行 0失效 */
    @Schema(description = "状态 1现行 0失效")
    private Integer status;

    /** 排序号 */
    @Schema(description = "排序号")
    private Integer sortNo;

    /** 下挂内容统计 */
    @Schema(description = "下挂内容统计")
    private KnowledgePointContentStatVO childCount;

    /** 子节点 */
    @Schema(description = "子节点")
    private List<KnowledgePointVO> children = new ArrayList<>();

}
