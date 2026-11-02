package org.jeecg.modules.study.math.vo;

import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 变式作答结果。
 *
 * <p><b>不下发答案</b>：只回判定结论、判定依据与错因落点（R-01）。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "变式作答结果")
public class VariantAnswerResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 变式业务ID */
    @Schema(description = "变式业务ID")
    private String variantCode;

    /** 是否正确 */
    @Schema(description = "是否正确")
    private boolean correct;

    /** 判定依据（人可读） */
    @Schema(description = "判定依据")
    private String basis;

    /** 判定来源 rule / rule+model */
    @Schema(description = "判定来源")
    private String judgeSource;

    /** 错题业务ID（答错时） */
    @Schema(description = "错题业务ID")
    private String errorCode;

    /** 本次记录的步骤级错因打标 */
    @Schema(description = "步骤级错因打标")
    private List<StepTagVO> stepTags;

    /** 是否命中幂等（重复提交未产生新记录） */
    @Schema(description = "是否命中幂等")
    private boolean duplicated;
}
