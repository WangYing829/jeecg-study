package org.jeecg.modules.study.error.vo;

import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 错题变式清单。
 *
 * <p>{@code variantAvailable=false} 表示「暂无可用变式」——
 * 此时错题**仍然留在复习队列**里，只是不给变式（TC-02-15）。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "错题变式清单")
public class ErrorVariantListVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 错题业务ID */
    @Schema(description = "错题业务ID")
    private String errorCode;

    /** 母题业务ID（无母题时为空） */
    @Schema(description = "母题业务ID")
    private String motherCode;

    /** 是否有可用变式 */
    @Schema(description = "是否有可用变式")
    private Boolean variantAvailable;

    /** 降级类型：MOTHER=母题变式、SAME_POINT=同知识点同题型降级、NONE=暂无可用变式 */
    @Schema(description = "降级类型")
    private String degradeType;

    /** 变式清单 */
    @Schema(description = "变式清单")
    private List<ErrorVariantVO> variants;
}
