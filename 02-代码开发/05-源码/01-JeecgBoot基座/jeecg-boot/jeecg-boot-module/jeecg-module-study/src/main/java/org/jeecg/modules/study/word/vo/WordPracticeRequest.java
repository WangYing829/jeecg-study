package org.jeecg.modules.study.word.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 拼写作答入参。
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "拼写作答入参")
public class WordPracticeRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 孩子业务ID；孩子端可不传（取会话身份），家长端必传 */
    @Schema(description = "孩子业务ID")
    private String childCode;

    /** 单词业务ID */
    @Schema(description = "单词业务ID")
    private String wordCode;

    /** 孩子写的拼写 */
    @Schema(description = "孩子写的拼写")
    private String spelling;

    /** 错因；拼写答错时必填，缺失返回 4001 */
    @Schema(description = "错因")
    private String errorCause;

    /** 本题耗时（毫秒） */
    @Schema(description = "本题耗时")
    private Integer durationMs;
}
