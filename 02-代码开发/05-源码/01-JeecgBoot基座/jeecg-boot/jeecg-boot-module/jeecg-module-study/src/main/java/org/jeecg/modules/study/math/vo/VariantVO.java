package org.jeecg.modules.study.math.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 变式条目（<b>不含答案</b>，R-01 反搜题）。
 *
 * <p>答案存在库里，只在服务端参与判定；下发时不给，
 * 孩子没作答之前拿不到答案。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "变式条目")
public class VariantVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 变式业务ID */
    @Schema(description = "变式业务ID")
    private String variantCode;

    /** 母题业务ID */
    @Schema(description = "母题业务ID")
    private String motherCode;

    /** 层级 1换数字 2换情境 3逆向 */
    @Schema(description = "层级")
    private Integer layer;

    /** 层级名称 */
    @Schema(description = "层级名称")
    private String layerName;

    /** 题干 */
    @Schema(description = "题干")
    private String stem;

    /** 生成方式 RULE / RULE+MODEL */
    @Schema(description = "生成方式")
    private String generatedBy;

    /** 数值容差 */
    @Schema(description = "数值容差")
    private BigDecimal answerTolerance;

    /** 参数替换快照（参数名 → 数值） */
    @Schema(description = "参数替换快照")
    private Map<String, Object> params;
}
