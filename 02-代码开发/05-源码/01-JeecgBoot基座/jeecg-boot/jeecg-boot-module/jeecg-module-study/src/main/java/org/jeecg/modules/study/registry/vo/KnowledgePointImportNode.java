package org.jeecg.modules.study.registry.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 导入坐标树的单个节点。
 *
 * <p>父子关系用 <b>请求内自引用键</b>表达：{@code refKey} 是本次导入内唯一的临时键，
 * {@code parentRefKey} 指向父节点的 {@code refKey}。
 * 这样父节点的业务ID（由统一分配器生成）无需提前知道。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "导入坐标树的单个节点")
public class KnowledgePointImportNode implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 本次导入内的唯一临时键 */
    @Schema(description = "本次导入内的唯一临时键")
    private String refKey;

    /** 父节点的临时键；空表示根节点 */
    @Schema(description = "父节点的临时键；空表示根节点")
    private String parentRefKey;

    /** 层级 1考纲 2学科 3单元 4考点 */
    @Schema(description = "层级 1考纲 2学科 3单元 4考点")
    private Integer level;

    /** 学科编码 */
    @Schema(description = "学科编码")
    private String subjectCode;

    /** 名称 */
    @Schema(description = "名称")
    private String name;

    /** 排序号 */
    @Schema(description = "排序号")
    private Integer sortNo;

    /** 题型 */
    @Schema(description = "题型")
    private String questionType;

    /** 分值权重 */
    @Schema(description = "分值权重")
    private BigDecimal scoreWeight;

    /** 常见错因 */
    @Schema(description = "常见错因")
    private List<String> errorTypes;

    /** 母题业务ID */
    @Schema(description = "母题业务ID")
    private String motherQuestionCode;

}
