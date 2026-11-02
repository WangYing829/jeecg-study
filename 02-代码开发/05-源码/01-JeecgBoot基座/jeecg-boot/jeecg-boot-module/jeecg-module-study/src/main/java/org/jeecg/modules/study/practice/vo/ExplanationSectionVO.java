package org.jeecg.modules.study.practice.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 解析分段。
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "解析分段")
public class ExplanationSectionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 段标识：QUESTION / IDEA / STEP / ANSWER */
    @Schema(description = "段标识")
    private String key;

    /** 段标题 */
    @Schema(description = "段标题")
    private String title;

    /** 段正文 */
    @Schema(description = "段正文")
    private String text;
}
