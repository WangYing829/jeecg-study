package org.jeecg.modules.study.parent.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 错因分布条目（只有维度名与条数）。
 *
 * <p><b>不含任何作答内容</b>：没有题目、没有作答、没有解析（R-02 / R-04）。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "错因分布条目")
public class ParentErrorCauseVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 错因（与错题池里的取值一致） */
    @Schema(description = "错因")
    private String cause;

    /** 错因展示名 */
    @Schema(description = "错因展示名")
    private String causeName;

    /** 条数 */
    @Schema(description = "条数")
    private Integer count;
}
