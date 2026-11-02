package org.jeecg.modules.study.grammar.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 语法点视图
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "语法点视图")
public class GrammarPointVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务ID */
    @Schema(description = "业务ID")
    private String grammarCode;

    /** 语法点名称 */
    @Schema(description = "语法点名称")
    private String title;

    /** 最小讲解 */
    @Schema(description = "最小讲解")
    private String rule;

    /** 同类例句 */
    @Schema(description = "同类例句")
    private String example;

    /** 反例 */
    @Schema(description = "反例")
    private String counterExample;

    /** 对应中考题型 */
    @Schema(description = "对应中考题型")
    private String examType;

    /** 所属考点业务ID */
    @Schema(description = "所属考点业务ID")
    private String knowledgePointCode;

}
