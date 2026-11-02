package org.jeecg.modules.study.practice.vo;

import java.io.Serializable;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 提交作答的结果。
 *
 * <p><b>红线</b>：{@code source} 只能是 {@code rule} / {@code rule+model}（R02-3）；
 * {@code basis} 必须非空且人可读（TC-08-11）。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "提交作答的结果")
public class AnswerResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 内容业务ID */
    @Schema(description = "内容业务ID")
    private String contentId;

    /** 是否正确 */
    @Schema(description = "是否正确")
    private Boolean correct;

    /** 判定来源 rule / rule+model */
    @Schema(description = "判定来源")
    private String source;

    /** 判定依据（人可读，非空） */
    @Schema(description = "判定依据")
    private String basis;

    /** 判定明细 */
    @Schema(description = "判定明细")
    private JudgeDetailVO detail;

    /** 讲解 */
    @Schema(description = "讲解")
    private ExplainVO explain;

    /** 讲解状态：OK / DEGRADED */
    @Schema(description = "讲解状态")
    private String explainStatus;

    /** 错题业务ID（答错时） */
    @Schema(description = "错题业务ID")
    private String errorId;

    /** 下次复习日期（答错时） */
    @Schema(description = "下次复习日期")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date nextReviewAt;

    /** 掌握度 1未知 2学习中 3熟悉 4掌握 */
    @Schema(description = "掌握度")
    private Integer masteryState;

    /** 作答记录主键 */
    @Schema(description = "作答记录主键")
    private String recordId;

    /** 是否命中幂等（同一会话同一题重复提交） */
    @Schema(description = "是否命中幂等")
    private Boolean duplicated;
}
