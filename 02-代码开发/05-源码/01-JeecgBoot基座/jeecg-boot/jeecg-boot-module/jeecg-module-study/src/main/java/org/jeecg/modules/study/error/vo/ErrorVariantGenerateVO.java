package org.jeecg.modules.study.error.vo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 错题变式生成结果（F-WRG-03）。
 *
 * <p><b>不含答案</b>：变式是给孩子重做的题，答案只在服务端参与判定（R-01）。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "错题变式生成结果")
public class ErrorVariantGenerateVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 错题业务ID */
    @Schema(description = "错题业务ID")
    private String errorCode;

    /** 母题业务ID（无母题时为空） */
    @Schema(description = "母题业务ID")
    private String motherCode;

    /** 本次是否新生成变式（命中幂等时为 false） */
    @Schema(description = "本次是否新生成变式")
    private Boolean generated = Boolean.FALSE;

    /** 是否有可用变式 */
    @Schema(description = "是否有可用变式")
    private Boolean variantAvailable = Boolean.FALSE;

    /** 降级类型：MOTHER=母题变式、SAME_POINT=同知识点同题型降级、NONE=暂无可用变式 */
    @Schema(description = "降级类型")
    private String degradeType;

    /** 变式清单 */
    @Schema(description = "变式清单")
    private List<ErrorVariantVO> variants = new ArrayList<>();

    /** 提示文案 */
    @Schema(description = "提示文案")
    private String message;
}
