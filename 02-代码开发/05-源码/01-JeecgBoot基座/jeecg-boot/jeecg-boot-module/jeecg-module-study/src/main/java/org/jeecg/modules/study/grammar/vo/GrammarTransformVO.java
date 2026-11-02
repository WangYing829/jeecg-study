package org.jeecg.modules.study.grammar.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 句子变形的单一形态
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "句子变形的单一形态")
public class GrammarTransformVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 形态标识 */
    @Schema(description = "形态标识")
    private String kind;

    /** 形态名称 */
    @Schema(description = "形态名称")
    private String name;

    /** 变形后的句子 */
    @Schema(description = "变形后的句子")
    private String text;

    /** 使用场合说明 */
    @Schema(description = "使用场合说明")
    private String usage;

    /** 是否为错误示范 */
    @Schema(description = "是否为错误示范")
    private Boolean wrong;

}
