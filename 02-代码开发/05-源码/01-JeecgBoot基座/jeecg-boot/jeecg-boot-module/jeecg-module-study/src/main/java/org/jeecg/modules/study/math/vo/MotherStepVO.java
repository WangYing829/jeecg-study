package org.jeecg.modules.study.math.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 母题第 4 段：解题步骤链的一步。
 *
 * <p>{@code formula} 是这一步的算式（形如 {@code S / (v1 + v2)}），
 * 变量名必须与第 2 段「已知条件类型」的 {@code key} 对得上；
 * <b>变式答案就是按这条算式算出来的</b>，不依赖任何模型。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "解题步骤链的一步")
public class MotherStepVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 步骤序号（从 1 开始） */
    @Schema(description = "步骤序号")
    private Integer index;

    /** 步骤标题 */
    @Schema(description = "步骤标题")
    private String title;

    /** 该步的算式 */
    @Schema(description = "该步的算式")
    private String formula;

    /** 备注 */
    @Schema(description = "备注")
    private String note;
}
