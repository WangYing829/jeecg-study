package org.jeecg.modules.study.practice.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 会话汇总。
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "会话汇总")
public class SessionSummaryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 会话 ID */
    @Schema(description = "会话 ID")
    private String sessionId;

    /** 作答总数 */
    @Schema(description = "作答总数")
    private Integer total;

    /** 做对数 */
    @Schema(description = "做对数")
    private Integer correct;

    /** 做错数 */
    @Schema(description = "做错数")
    private Integer wrong;

    /** 总耗时（毫秒） */
    @Schema(description = "总耗时")
    private Integer durationMs;
}
