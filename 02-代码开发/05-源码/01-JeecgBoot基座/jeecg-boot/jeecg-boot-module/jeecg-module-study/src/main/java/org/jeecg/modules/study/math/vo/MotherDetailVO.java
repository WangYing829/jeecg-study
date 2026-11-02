package org.jeecg.modules.study.math.vo;

import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 母题详情（五段结构，TC-04-02）。
 *
 * <p>{@code sectionOrder} 给出五段的<b>固定展示顺序</b>：
 * 题型名 → 已知条件类型 → 关键关系 → 解题步骤链 → 易错点清单；
 * {@code keyRelationHighlighted} 标明关键关系需要高亮。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "母题详情（五段结构）")
public class MotherDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 母题业务ID */
    @Schema(description = "母题业务ID")
    private String motherCode;

    /** 第 1 段：题型名 */
    @Schema(description = "第1段 题型名")
    private String title;

    /** 第 2 段：已知条件类型 */
    @Schema(description = "第2段 已知条件类型")
    private List<ConditionTypeVO> conditionTypes;

    /** 第 3 段：关键关系 */
    @Schema(description = "第3段 关键关系")
    private String keyRelation;

    /** 第 4 段：解题步骤链 */
    @Schema(description = "第4段 解题步骤链")
    private List<MotherStepVO> steps;

    /** 第 5 段：易错点清单 */
    @Schema(description = "第5段 易错点清单")
    private List<String> pitfalls;

    /** 学科编码 */
    @Schema(description = "学科编码")
    private String subjectCode;

    /** 所属考点业务ID */
    @Schema(description = "所属考点业务ID")
    private String knowledgePointCode;

    /** 过关所需变式全对数 */
    @Schema(description = "过关所需变式全对数")
    private Integer passVariantCount;

    /** 闯关顺序（考纲优先级） */
    @Schema(description = "闯关顺序")
    private Integer orderNo;

    /** 状态 1启用 0停用 */
    @Schema(description = "状态")
    private Integer status;

    /** 五段的固定展示顺序 */
    @Schema(description = "五段展示顺序")
    private List<String> sectionOrder;

    /** 关键关系是否需要高亮 */
    @Schema(description = "关键关系是否高亮")
    private boolean keyRelationHighlighted;

    /** 需要高亮的段落码 */
    @Schema(description = "高亮段落码")
    private String highlightSection;
}
