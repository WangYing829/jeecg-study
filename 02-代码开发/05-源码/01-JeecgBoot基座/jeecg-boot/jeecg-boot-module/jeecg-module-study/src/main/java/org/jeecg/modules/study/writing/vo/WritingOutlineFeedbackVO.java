package org.jeecg.modules.study.writing.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 提纲反馈条目。
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "提纲反馈条目")
public class WritingOutlineFeedbackVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 类型：STRUCTURE / OPENING / BODY / ENDING */
    @Schema(description = "类型")
    private String type;

    /** 给孩子的说明 */
    @Schema(description = "给孩子的说明")
    private String message;
}
