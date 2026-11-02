package org.jeecg.modules.study.writing.vo;

import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 提交提纲的结果。
 *
 * <p>{@code passed} 为 true 时 {@code nextStep} 才是 {@code DRAFT} ——
 * 三步训练流靠它强制顺序（TC-06-05）。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "提交提纲的结果")
public class WritingOutlineResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 提纲是否通过 */
    @Schema(description = "提纲是否通过")
    private Boolean passed;

    /** 提纲反馈 */
    @Schema(description = "提纲反馈")
    private List<WritingOutlineFeedbackVO> feedback;

    /** 下一步：OUTLINE（还没过）/ DRAFT（过了） */
    @Schema(description = "下一步")
    private String nextStep;
}
