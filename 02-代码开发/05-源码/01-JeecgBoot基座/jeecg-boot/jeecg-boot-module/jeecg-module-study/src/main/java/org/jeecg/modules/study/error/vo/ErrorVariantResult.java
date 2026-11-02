package org.jeecg.modules.study.error.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 变式作答结果。
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "变式作答结果")
public class ErrorVariantResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 变式业务ID */
    @Schema(description = "变式业务ID")
    private String variantCode;

    /** 该变式是否做对 */
    @Schema(description = "该变式是否做对")
    private Boolean correct;
}
