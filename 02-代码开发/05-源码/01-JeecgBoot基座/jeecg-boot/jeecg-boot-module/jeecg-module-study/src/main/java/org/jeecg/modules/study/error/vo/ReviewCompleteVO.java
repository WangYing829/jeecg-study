package org.jeecg.modules.study.error.vo;

import java.io.Serializable;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 完成一项复习的结果。
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "完成一项复习的结果")
public class ReviewCompleteVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 错题业务ID */
    @Schema(description = "错题业务ID")
    private String errorCode;

    /** 连续答对次数 */
    @Schema(description = "连续答对次数")
    private Integer correctStreak;

    /** 掌握度 1未知 2学习中 3熟悉 4掌握 */
    @Schema(description = "掌握度")
    private Integer masteryState;

    /** 是否已移出队列 */
    @Schema(description = "是否已移出队列")
    private Boolean archived;

    /** 下次复习日期 */
    @Schema(description = "下次复习日期")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date nextReviewAt;

    /** 本次是否计入「做对」（变式未全对时为 false） */
    @Schema(description = "本次是否计入做对")
    private Boolean counted;

    /** 提示文案 */
    @Schema(description = "提示文案")
    private String message;
}
