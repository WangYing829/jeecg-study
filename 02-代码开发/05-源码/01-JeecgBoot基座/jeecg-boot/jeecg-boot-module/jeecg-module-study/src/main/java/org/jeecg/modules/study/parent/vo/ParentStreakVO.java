package org.jeecg.modules.study.parent.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 连续学习天数视图（只看这个孩子自己的连续性）。
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "连续学习天数视图")
public class ParentStreakVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 连续天数 */
    @Schema(description = "连续天数")
    private Integer days;

    /** 今日是否已有学习记录 */
    @Schema(description = "今日是否已有学习记录")
    private Boolean todayCompleted;
}
