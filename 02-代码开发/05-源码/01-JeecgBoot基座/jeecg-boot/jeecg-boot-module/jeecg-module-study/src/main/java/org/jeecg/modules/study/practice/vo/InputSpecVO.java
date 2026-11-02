package org.jeecg.modules.study.practice.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 作答控件规格（输入框形态，不是答案）。
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "作答控件规格")
public class InputSpecVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 输入形态：NUMERIC / EXACT / MULTI_CHOICE */
    @Schema(description = "输入形态")
    private String type;

    /** 单位 */
    @Schema(description = "单位")
    private String unit;

    /** 占位提示 */
    @Schema(description = "占位提示")
    private String placeholder;
}
