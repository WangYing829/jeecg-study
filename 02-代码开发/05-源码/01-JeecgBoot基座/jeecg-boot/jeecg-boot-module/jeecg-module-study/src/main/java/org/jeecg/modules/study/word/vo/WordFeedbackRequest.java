package org.jeecg.modules.study.word.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 四档反馈入参。
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "四档反馈入参")
public class WordFeedbackRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 孩子业务ID；孩子端可不传（取会话身份），家长端必传 */
    @Schema(description = "孩子业务ID")
    private String childCode;

    /** 单词业务ID */
    @Schema(description = "单词业务ID")
    private String wordCode;

    /** 反馈档 KNOWN / VAGUE / FORGOT / FAMILIAR */
    @Schema(description = "反馈档")
    private String feedback;

    /** 错因；「忘记」档缺省按「概念不清」处理 */
    @Schema(description = "错因")
    private String errorCause;
}
