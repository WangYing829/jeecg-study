package org.jeecg.modules.study.family.vo;

import java.io.Serializable;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;
import java.util.List;

/**
 * 孩子概览（家长视角：护眼 / 错题 / 掌握度）
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "孩子概览（家长视角：护眼 / 错题 / 掌握度）")
public class OverviewVO implements Serializable {

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

    /** 默认年级 */
    @Schema(description = "默认年级")
    private String gradeCode;

    /** 累计有效用眼时长（分钟） */
    @Schema(description = "累计有效用眼时长（分钟）")
    private Integer eyeActiveMinutes;

    /** 休息次数 */
    @Schema(description = "休息次数")
    private Integer eyeRestCount;

    /** 错题总数 */
    @Schema(description = "错题总数")
    private Integer errorTotal;

    /** 待复习错题数 */
    @Schema(description = "待复习错题数")
    private Integer errorPending;

    /** 错因 Top3（仅维度名，不做人与人的比较） */
    @Schema(description = "错因 Top3（仅维度名，不做人与人的比较）")
    private List<String> errorCauseTop;

    /** 掌握度分布 */
    @Schema(description = "掌握度分布")
    private List<MasteryBucketVO> masteryDistribution;

}
