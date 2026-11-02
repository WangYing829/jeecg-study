package org.jeecg.modules.study.math.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 口述提交入参。
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "口述提交入参")
public class NarrativeCommand implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 口述内容 */
    @Schema(description = "口述内容")
    private String narrative;
}
