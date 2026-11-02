package org.jeecg.modules.study.word.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 反馈档位选项。
 *
 * <p>TC-03-04：反馈按钮组<b>恰好 4 档</b>——认识 / 模糊 / 忘记 / 熟知，
 * <b>不是对/错</b>。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "反馈档位选项")
public class WordFeedbackOptionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 档位码 */
    @Schema(description = "档位码")
    private String feedback;

    /** 档位名称 */
    @Schema(description = "档位名称")
    private String label;

    /** 是否为「没记住」档（忘记） */
    @Schema(description = "是否为没记住档")
    private Boolean forgotten;
}
