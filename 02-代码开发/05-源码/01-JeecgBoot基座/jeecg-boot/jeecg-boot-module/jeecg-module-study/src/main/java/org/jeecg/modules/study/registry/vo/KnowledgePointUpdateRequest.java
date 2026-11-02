package org.jeecg.modules.study.registry.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 维护考点四属性入参。
 *
 * <p>四属性 = 题型 + 分值权重 + 常见错因 + 母题（TC-01-09）。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "维护考点四属性入参")
public class KnowledgePointUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 题型 */
    @Schema(description = "题型")
    private String questionType;

    /** 分值权重 */
    @Schema(description = "分值权重")
    private BigDecimal scoreWeight;

    /** 常见错因 */
    @Schema(description = "常见错因")
    private List<String> errorTypes;

    /** 母题业务ID；指向不存在的母题返回 4004（TC-01-10） */
    @Schema(description = "母题业务ID")
    private String motherQuestionCode;

}
