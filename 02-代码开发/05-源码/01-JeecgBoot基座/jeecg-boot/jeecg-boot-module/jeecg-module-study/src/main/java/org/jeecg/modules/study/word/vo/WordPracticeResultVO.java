package org.jeecg.modules.study.word.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 拼写作答判定结果。
 *
 * <p>这是 R-01 允许下发答案的<b>唯一</b>位置：孩子在 {@code POST /word/practice}
 * 提交自己的拼写之后，服务端才回传正确拼写。</p>
 *
 * <p>判定来源固定为 {@code rule} / {@code rule+model}（R-02），
 * 判定走 {@code JudgeEngine} 的 EXACT 策略，不依赖任何模型。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "拼写作答判定结果")
public class WordPracticeResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 单词业务ID */
    @Schema(description = "单词业务ID")
    private String wordCode;

    /** 是否拼写正确 */
    @Schema(description = "是否拼写正确")
    private Boolean correct;

    /** 判定来源 rule / rule+model */
    @Schema(description = "判定来源")
    private String judgeSource;

    /** 判定依据（人可读，非空） */
    @Schema(description = "判定依据")
    private String basis;

    /** 正确拼写（仅在孩子作答之后下发） */
    @Schema(description = "正确拼写")
    private String correctSpelling;

    /** 错题业务ID（答错并入池时给出） */
    @Schema(description = "错题业务ID")
    private String errorCode;

    /** 是否触发课内→课外跨线升级 */
    @Schema(description = "是否跨线升级")
    private Boolean upgraded;

    /** 记忆强度 S */
    @Schema(description = "记忆强度S")
    private BigDecimal memoryStrength;

    /** 衰减系数 λ */
    @Schema(description = "衰减系数λ")
    private BigDecimal decayLambda;

    /** 下次复习日期 */
    @Schema(description = "下次复习日期")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    private Date nextReviewAt;
}
