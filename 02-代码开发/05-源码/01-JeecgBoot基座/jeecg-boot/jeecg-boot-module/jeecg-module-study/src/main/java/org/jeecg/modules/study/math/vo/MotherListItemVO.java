package org.jeecg.modules.study.math.vo;

import java.io.Serializable;
import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 闯关首页条目（TC-04-10 / TC-04-11）。
 *
 * <p>排序口径：<b>考纲分值权重降序 + 掌握度升序</b>，同权重档内连续错的关卡前移。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "闯关首页条目")
public class MotherListItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 母题业务ID */
    @Schema(description = "母题业务ID")
    private String motherCode;

    /** 题型名 */
    @Schema(description = "题型名")
    private String title;

    /** 学科编码 */
    @Schema(description = "学科编码")
    private String subjectCode;

    /** 所属考点业务ID */
    @Schema(description = "所属考点业务ID")
    private String knowledgePointCode;

    /** 考纲分值权重（来自考点） */
    @Schema(description = "考纲分值权重")
    private BigDecimal outlineWeight;

    /** 闯关顺序（母题自带） */
    @Schema(description = "闯关顺序")
    private Integer orderNo;

    /** 该孩子在本关的掌握度（0~1，越低越该先补） */
    @Schema(description = "掌握度")
    private BigDecimal mastery;

    /** 作答次数 */
    @Schema(description = "作答次数")
    private Integer attemptCount;

    /** 答错次数 */
    @Schema(description = "答错次数")
    private Integer errorCount;

    /** 连续答错次数（错因指导优先级） */
    @Schema(description = "连续答错次数")
    private Integer consecutiveErrors;

    /** 已生成变式数 */
    @Schema(description = "已生成变式数")
    private Integer variantCount;

    /** 是否已过关 */
    @Schema(description = "是否已过关")
    private boolean passed;
}
