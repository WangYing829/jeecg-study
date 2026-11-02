package org.jeecg.modules.study.writing.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 提交初稿入参（提纲 → 初稿）。
 *
 * <p>服务端会用 {@code writingCode + topic} 找到孩子自己的提纲行；
 * 提纲没通过就返回 4002，且不会落任何库（TC-06-06）。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "提交初稿入参")
public class WritingDraftRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 来源拆解的业务ID（形如 WRT-HOOK-0031） */
    @Schema(description = "来源拆解业务ID")
    private String writingCode;

    /** 自己的题目 */
    @Schema(description = "自己的题目")
    private String topic;

    /** 作文正文 */
    @Schema(description = "作文正文")
    private String content;
}
