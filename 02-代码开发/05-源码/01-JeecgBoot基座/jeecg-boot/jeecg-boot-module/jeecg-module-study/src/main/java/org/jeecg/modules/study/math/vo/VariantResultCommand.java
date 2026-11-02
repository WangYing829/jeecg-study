package org.jeecg.modules.study.math.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 单条变式的作答（过关判定用）。
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "单条变式的作答")
public class VariantResultCommand implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 变式业务ID */
    @Schema(description = "变式业务ID")
    private String variantCode;

    /** 孩子的答案（服务端据此判定） */
    @Schema(description = "孩子的答案")
    private String answer;
}
