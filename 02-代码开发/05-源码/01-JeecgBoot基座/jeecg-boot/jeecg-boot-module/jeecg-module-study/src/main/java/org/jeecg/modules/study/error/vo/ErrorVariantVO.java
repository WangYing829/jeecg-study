package org.jeecg.modules.study.error.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 错题变式条目。
 *
 * <p><b>不含答案</b>：变式是给娃重做的题，答案只能在他作答之后给（R-01）。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "错题变式条目")
public class ErrorVariantVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 变式业务ID（降级变式为题目业务ID） */
    @Schema(description = "变式业务ID")
    private String variantCode;

    /** 生成的题目业务ID */
    @Schema(description = "题目业务ID")
    private String questionCode;

    /** 母题业务ID（无母题时为空） */
    @Schema(description = "母题业务ID")
    private String motherCode;

    /** 变式层级 1换数字 2换情境 3逆向 */
    @Schema(description = "变式层级")
    private Integer layer;

    /** 变式层级名称（换数字 / 换情境 / 逆向） */
    @Schema(description = "变式层级名称")
    private String layerName;

    /** 题干 */
    @Schema(description = "题干")
    private String stem;

    /** 变式来源：VARIANT=母题变式，QUESTION=同知识点同题型的降级变式 */
    @Schema(description = "变式来源")
    private String source;
}
