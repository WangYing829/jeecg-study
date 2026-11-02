package org.jeecg.modules.study.word.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 背诵统计。
 *
 * <p>R-04：不含任何排名 / 百分位 / 等级字段，也不做家庭内或家长之间的对照。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "背诵统计")
public class WordStatsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 线别 IN课内 / OUT课外 */
    @Schema(description = "线别")
    private String lineType;

    /** 该线别启用词总数 */
    @Schema(description = "启用词总数")
    private Integer totalWords;

    /** 进队列的词数（已过滤熟词） */
    @Schema(description = "队列词数")
    private Integer queueWords;

    /** 熟词过滤条数 */
    @Schema(description = "熟词过滤条数")
    private Integer filteredKnownCount;

    /** 学过的词数（有过反馈） */
    @Schema(description = "学过的词数")
    private Integer learnedWords;

    /** 到期复习词数 */
    @Schema(description = "到期复习词数")
    private Integer dueWords;

    /** 今日待复习条目数（来自统一复习队列） */
    @Schema(description = "今日待复习条目数")
    private Integer reviewPending;

    /** 连续学习天数 */
    @Schema(description = "连续学习天数")
    private Integer streakDays;
}
