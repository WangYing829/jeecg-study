package org.jeecg.modules.study.word.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 四档反馈结果。
 *
 * <p>字段口径与《接口设计说明书》§7.2 的 {@code POST /word/feedback} 响应一致。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "四档反馈结果")
public class WordFeedbackVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 单词业务ID */
    @Schema(description = "单词业务ID")
    private String wordCode;

    /** 本次反馈档 */
    @Schema(description = "本次反馈档")
    private String feedback;

    /** 反馈档名称 */
    @Schema(description = "反馈档名称")
    private String feedbackLabel;

    /** 记忆强度 S */
    @Schema(description = "记忆强度S")
    private BigDecimal memoryStrength;

    /** 衰减系数 λ */
    @Schema(description = "衰减系数λ")
    private BigDecimal decayLambda;

    /** 反馈累计次数 */
    @Schema(description = "反馈累计次数")
    private Integer feedbackCount;

    /** 下次复习日期 */
    @Schema(description = "下次复习日期")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    private Date nextReviewAt;

    /** 线别 IN课内 / OUT课外 */
    @Schema(description = "线别")
    private String lineType;

    /** 是否触发课内→课外跨线升级 */
    @Schema(description = "是否跨线升级")
    private Boolean upgraded;

    /** 错题业务ID（忘记档入池时给出） */
    @Schema(description = "错题业务ID")
    private String errorCode;
}
