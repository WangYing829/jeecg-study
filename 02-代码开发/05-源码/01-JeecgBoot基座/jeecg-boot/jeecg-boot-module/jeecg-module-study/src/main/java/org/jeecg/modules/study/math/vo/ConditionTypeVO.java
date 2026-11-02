package org.jeecg.modules.study.math.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 母题第 2 段：已知条件类型。
 *
 * <p>一个已知条件可以有好几种说法（{@code variants}），这正是「换情境」变式的素材。</p>
 *
 * <p>{@code value} 是该条件在本母题里的<b>数值</b>，变式生成据此做参数替换；
 * 没填数值的条件只作为表层描述参与换情境。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "已知条件类型")
public class ConditionTypeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 条件标识（同时是公式里的参数名，如 S / v1 / v2） */
    @Schema(description = "条件标识（公式参数名）")
    private String key;

    /** 条件名称 */
    @Schema(description = "条件名称")
    private String label;

    /** 同一条件的其它说法（换情境时取） */
    @Schema(description = "同一条件的其它说法")
    private List<String> variants;

    /** 本母题里该条件的数值（可空） */
    @Schema(description = "该条件的数值")
    private BigDecimal value;
}
