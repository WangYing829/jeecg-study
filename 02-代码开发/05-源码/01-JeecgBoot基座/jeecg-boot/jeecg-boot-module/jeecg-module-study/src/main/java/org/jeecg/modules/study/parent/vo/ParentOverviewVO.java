package org.jeecg.modules.study.parent.vo;

import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 家长端总览视图。
 *
 * <p><b>只读</b>：家长端只看不改，这里全部是孩子<b>自身历史趋势</b>，
 * 不含任何与其他孩子、其他家长的横向对比字段（R-04）。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "家长端总览视图")
public class ParentOverviewVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 孩子 ID */
    @Schema(description = "孩子 ID")
    private String childId;

    /** 孩子业务ID */
    @Schema(description = "孩子业务ID")
    private String childCode;

    /** 昵称 */
    @Schema(description = "昵称")
    private String nickname;

    /** 用眼情况 */
    @Schema(description = "用眼情况")
    private ParentEyeUsageVO eyeUsage;

    /** 连续学习天数 */
    @Schema(description = "连续学习天数")
    private ParentStreakVO streak;

    /** 掌握度分布 */
    @Schema(description = "掌握度分布")
    private ParentMasteryVO mastery;

    /** 错因 Top3 */
    @Schema(description = "错因 Top3")
    private List<ParentErrorCauseVO> errorTop;

    /** 薄弱考点 */
    @Schema(description = "薄弱考点")
    private List<ParentWeakPointVO> weakPoints;

    /** 写作趋势 */
    @Schema(description = "写作趋势")
    private List<ParentWritingTrendVO> writingTrend;
}
