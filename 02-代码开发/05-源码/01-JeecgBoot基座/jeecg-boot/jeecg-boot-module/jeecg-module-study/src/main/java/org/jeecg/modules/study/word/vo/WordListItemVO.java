package org.jeecg.modules.study.word.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 词表条目视图。
 *
 * <p><b>刻意不含拼写</b>：拼写就是拼写题的答案，词表接口提前下发等于替孩子作答，
 * 违反红线 R-01。判定接口在孩子作答之后才回传正确拼写。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "词表条目视图（不含拼写）")
public class WordListItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 单词业务ID */
    @Schema(description = "单词业务ID")
    private String wordCode;

    /** 线别 IN课内 / OUT课外 */
    @Schema(description = "线别")
    private String lineType;

    /** 教材版本ID */
    @Schema(description = "教材版本ID")
    private String textbookVersionId;

    /** 单元编码 */
    @Schema(description = "单元编码")
    private String unitCode;

    /** 单元内顺序 */
    @Schema(description = "单元内顺序")
    private Integer unitOrder;

    /** 释义 */
    @Schema(description = "释义")
    private String meaning;

    /** 音标 */
    @Schema(description = "音标")
    private String phonetic;

    /** 词性 */
    @Schema(description = "词性")
    private String pos;

    /** 例句（目标词已挖空，避免提前泄露拼写） */
    @Schema(description = "例句")
    private String contextSentence;

    /** 词频 */
    @Schema(description = "词频")
    private Integer frequency;

    /** 记忆强度 S */
    @Schema(description = "记忆强度S")
    private BigDecimal memoryStrength;

    /** 衰减系数 λ */
    @Schema(description = "衰减系数λ")
    private BigDecimal decayLambda;

    /** 反馈次数 */
    @Schema(description = "反馈次数")
    private Integer feedbackCount;

    /** 最近反馈档 */
    @Schema(description = "最近反馈档")
    private String lastFeedback;

    /** 是否为跨线升级来的课外优先复习项 */
    @Schema(description = "是否跨线升级优先项")
    private Boolean upgradePriority;

    /** 下次复习日期 */
    @Schema(description = "下次复习日期")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    private Date nextReviewAt;
}
