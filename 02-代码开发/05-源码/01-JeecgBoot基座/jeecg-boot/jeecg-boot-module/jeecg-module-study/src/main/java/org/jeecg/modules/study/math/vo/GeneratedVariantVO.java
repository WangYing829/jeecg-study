package org.jeecg.modules.study.math.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 规则引擎算出来的一条变式草稿（<b>内部结构，不对外下发</b>）。
 *
 * <p>答案由确定性规则算出，与模型无关（R-02）。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "变式草稿")
public class GeneratedVariantVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 层级 1换数字 2换情境 3逆向 */
    @Schema(description = "层级")
    private Integer layer;

    /** 层级名称 */
    @Schema(description = "层级名称")
    private String layerName;

    /** 题干 */
    @Schema(description = "题干")
    private String stem;

    /** 答案（规则算出） */
    @Schema(description = "答案")
    private String answer;

    /** 数值容差 */
    @Schema(description = "数值容差")
    private BigDecimal answerTolerance;

    /** 生成方式 RULE / RULE+MODEL */
    @Schema(description = "生成方式")
    private String generatedBy;

    /** 参数替换快照 */
    @Schema(description = "参数替换快照")
    private Map<String, Object> paramsSnapshot = new LinkedHashMap<>();
}
