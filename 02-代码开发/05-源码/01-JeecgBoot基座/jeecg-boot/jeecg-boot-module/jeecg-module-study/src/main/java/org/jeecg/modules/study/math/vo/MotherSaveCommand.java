package org.jeecg.modules.study.math.vo;

import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 母题保存入参（五段齐全校验）。
 *
 * <p><b>五段缺任一段都返回 4007</b>，提示缺哪一段。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "母题保存入参")
public class MotherSaveCommand implements Serializable {

    private static final long serialVersionUID = 1L;

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

    /** 学科编码（默认 MA） */
    @Schema(description = "学科编码")
    private String subjectCode;

    /** 所属考点业务ID */
    @Schema(description = "所属考点业务ID")
    private String knowledgePointCode;

    /** 过关所需变式全对数（默认 3） */
    @Schema(description = "过关所需变式全对数")
    private Integer passVariantCount;
}
