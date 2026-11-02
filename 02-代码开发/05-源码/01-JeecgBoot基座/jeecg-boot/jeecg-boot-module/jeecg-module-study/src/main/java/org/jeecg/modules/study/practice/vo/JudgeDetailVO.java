package org.jeecg.modules.study.practice.vo;

import java.io.Serializable;
import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 判定明细。
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "判定明细")
public class JudgeDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 标准答案 */
    @Schema(description = "标准答案")
    private String expected;

    /** 孩子作答 */
    @Schema(description = "孩子作答")
    private String actual;

    /** 实际使用的容差 */
    @Schema(description = "实际使用的容差")
    private BigDecimal tolerance;
}
