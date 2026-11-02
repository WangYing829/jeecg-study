package org.jeecg.modules.study.error.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 修改错因入参。
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "修改错因入参")
public class ErrorCauseUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 新错因（五维之一） */
    @Schema(description = "新错因")
    private String errorCause;
}
