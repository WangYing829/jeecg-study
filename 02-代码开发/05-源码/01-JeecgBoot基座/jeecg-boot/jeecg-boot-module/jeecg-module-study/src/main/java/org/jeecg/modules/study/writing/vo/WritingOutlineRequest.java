package org.jeecg.modules.study.writing.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 提交提纲入参（审题 → 提纲）。
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "提交提纲入参")
public class WritingOutlineRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 来源拆解的业务ID（形如 WRT-HOOK-0031） */
    @Schema(description = "来源拆解业务ID")
    private String writingCode;

    /** 自己的题目 */
    @Schema(description = "自己的题目")
    private String topic;

    /** 提纲结构 */
    @Schema(description = "提纲结构")
    private WritingOutlineBody outline;
}
