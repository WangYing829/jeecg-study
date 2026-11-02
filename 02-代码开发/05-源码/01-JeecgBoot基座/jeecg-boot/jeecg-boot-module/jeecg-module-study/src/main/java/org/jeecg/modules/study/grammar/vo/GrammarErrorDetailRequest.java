package org.jeecg.modules.study.grammar.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 查看单条错误讲解的入参
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "查看单条错误讲解的入参")
public class GrammarErrorDetailRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 孩子刚写的句子 */
    @Schema(description = "孩子刚写的句子")
    private String sentence;

    /** 错误标记ID */
    @Schema(description = "错误标记ID")
    private String errorId;

}
