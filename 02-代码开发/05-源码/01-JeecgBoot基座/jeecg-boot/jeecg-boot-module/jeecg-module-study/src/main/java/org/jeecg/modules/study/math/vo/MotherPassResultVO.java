package org.jeecg.modules.study.math.vo;

import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 过关判定结果（TC-04-06 / TC-04-07 / TC-04-08）。
 *
 * <p><b>绝不含答案</b>：口述不过关时给的是<b>苏格拉底式追问</b>与关键词反馈，
 * 不是答案（A-05 / R-01）。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "过关判定结果")
public class MotherPassResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 母题业务ID */
    @Schema(description = "母题业务ID")
    private String motherCode;

    /** 是否过关（变式全对 且 口述通过） */
    @Schema(description = "是否过关")
    private boolean passed;

    /** 变式判据是否通过 */
    @Schema(description = "变式判据是否通过")
    private boolean variantPassed;

    /** 变式答对条数 */
    @Schema(description = "变式答对条数")
    private Integer variantCorrectCount;

    /** 过关所需变式全对数 */
    @Schema(description = "过关所需变式全对数")
    private Integer variantRequiredCount;

    /** 口述判据是否通过 */
    @Schema(description = "口述判据是否通过")
    private boolean narrativePassed;

    /** 口述命中的关键词 */
    @Schema(description = "命中的关键词")
    private List<String> hitKeywords;

    /** 口述缺失的关键词 */
    @Schema(description = "缺失的关键词")
    private List<String> missingKeywords;

    /** 苏格拉底式追问（口述不过关时给，不给答案） */
    @Schema(description = "苏格拉底式追问")
    private String followUpQuestion;

    /** 还差什么（提示语） */
    @Schema(description = "还差什么")
    private String hint;
}
