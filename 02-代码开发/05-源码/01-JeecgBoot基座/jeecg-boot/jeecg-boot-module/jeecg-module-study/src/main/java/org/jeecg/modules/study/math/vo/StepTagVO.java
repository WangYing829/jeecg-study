package org.jeecg.modules.study.math.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 步骤级错因打标（TC-04-09）。
 *
 * <p>错因沿用<b>全局五维口径</b>（概念不清 / 审题偏差 / 计算失误 / 方法不会 / 时间不够），
 * 这样步骤级错因能直接汇进统一错题池的错因统计。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "步骤级错因打标")
public class StepTagVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 步骤序号（母题第 4 段步骤链里的第几步，从 1 开始） */
    @Schema(description = "步骤序号")
    private Integer stepIndex;

    /** 该步的错因（五维之一） */
    @Schema(description = "该步的错因")
    private String errorCause;
}
