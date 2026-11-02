package org.jeecg.modules.study.writing.vo;

import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 提纲结构（开—中—末三段）。
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "提纲结构")
public class WritingOutlineBody implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 开头写什么 */
    @Schema(description = "开头写什么")
    private String opening;

    /** 正文分段写什么 */
    @Schema(description = "正文分段写什么")
    private List<String> body;

    /** 结尾怎么回扣 */
    @Schema(description = "结尾怎么回扣")
    private String ending;
}
