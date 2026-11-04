package org.jeecg.modules.study.practice.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 连续学习天数（F-WRD-02）。
 *
 * <p>只跟自己比：连续天数是<b>这个孩子自己的</b>累计，与任何其他人无关（R-04）。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "连续学习天数")
public class PracticeStreakVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 连续天数 */
    @Schema(description = "连续天数")
    private Integer days = 0;

    /** 今天是否已完成练习 */
    @Schema(description = "今天是否已完成练习")
    private Boolean todayCompleted = Boolean.FALSE;
}
