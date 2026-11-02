package org.jeecg.modules.study.word.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 单元闯关完成结果。
 *
 * <p>F-WRD-02：每单元过关有可见奖励、连续天数累计、可切换「纯净无游戏冲刺模式」。
 * 纯净模式下 {@code gameEnabled} / {@code rewardVisible} / {@code streakVisible} 全部置 false，
 * 奖励文案置空。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "单元闯关完成结果")
public class WordUnitCompleteVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 教材版本ID */
    @Schema(description = "教材版本ID")
    private String textbookVersionId;

    /** 单元编码 */
    @Schema(description = "单元编码")
    private String unitCode;

    /** 本次完成学习的词条数 */
    @Schema(description = "完成学习的词条数")
    private Integer learnedWordCount;

    /** 连续学习天数 */
    @Schema(description = "连续学习天数")
    private Integer streakDays;

    /** 是否开启纯净模式 */
    @Schema(description = "是否开启纯净模式")
    private Boolean pureMode;

    /** 是否展示游戏化元素 */
    @Schema(description = "是否展示游戏化元素")
    private Boolean gameEnabled;

    /** 是否展示奖励文案 */
    @Schema(description = "是否展示奖励文案")
    private Boolean rewardVisible;

    /** 是否展示连续天数 */
    @Schema(description = "是否展示连续天数")
    private Boolean streakVisible;

    /** 奖励文案（纯净模式为空串） */
    @Schema(description = "奖励文案")
    private String rewardText;
}
