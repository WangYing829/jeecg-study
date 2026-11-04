package org.jeecg.modules.study.mastery.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 错因维度计数（只有维度与条数）。
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "错因维度计数")
public class MasteryCauseCountVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 错因码 CONCEPT/READING/CALC/METHOD/TIME */
    @Schema(description = "错因码")
    private String cause;

    /** 错因展示名 */
    @Schema(description = "错因展示名")
    private String causeName;

    /** 条数 */
    @Schema(description = "条数")
    private Integer count;
}
