package org.jeecg.modules.study.word.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 取词（下一词）视图。
 *
 * <p><b>红线 R-01</b>：本视图不含 {@code answer} / {@code correctAnswer}，
 * 也<b>不含拼写</b>——拼写是拼写题的答案，提前下发等于替孩子作答。
 * 例句与短文中的目标词一律挖空。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "取词（下一词）视图")
public class WordPracticeVO implements Serializable {

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

    /** 例句（目标词已挖空） */
    @Schema(description = "例句")
    private String contextSentence;

    /** 是否存在不依赖图片的回忆环节（恒为 true，PRD §8.3 坑一） */
    @Schema(description = "是否存在无图回忆环节")
    private Boolean recallWithoutImage;

    /** 环节清单：释义 → 造句 → 短文填词 */
    @Schema(description = "环节清单")
    private List<WordStageVO> stages;

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
