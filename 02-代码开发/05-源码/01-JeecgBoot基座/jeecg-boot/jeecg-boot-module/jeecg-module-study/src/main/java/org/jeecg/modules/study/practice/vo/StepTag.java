package org.jeecg.modules.study.practice.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 步骤级错因打标。
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "步骤级错因打标")
public class StepTag implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 步骤序号 */
    @Schema(description = "步骤序号")
    private Integer stepIndex;

    /** 该步骤的错因 */
    @Schema(description = "该步骤的错因")
    private String cause;
}
