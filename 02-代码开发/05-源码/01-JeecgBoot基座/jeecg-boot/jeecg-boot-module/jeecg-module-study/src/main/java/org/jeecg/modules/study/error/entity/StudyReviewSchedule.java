package org.jeecg.modules.study.error.entity;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecg.common.system.base.entity.JeecgEntity;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 统一复习排期。
 *
 * <p>一条队列管全部模块：按 {@code errorCode + scheduleDate} 唯一，
 * {@code sortScore} 决定队列排序，{@code status} 表达排期状态机
 * （1待复习 / 2已完成 / 3已顺延）。</p>
 *
 * @author jeecg-study
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "统一复习排期")
@TableName("study_review_schedule")
public class StudyReviewSchedule extends JeecgEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 所属孩子 ID（多用户隔离维度） */
    @Schema(description = "所属孩子 ID")
    @TableField(value = "child_id")
    private String childId;

    /** 错题业务ID */
    @Schema(description = "错题业务ID")
    @TableField(value = "error_code")
    private String errorCode;

    /** 全局上下文 */
    @Schema(description = "全局上下文")
    @TableField(value = "context_id")
    private String contextId;

    /** 来源模块 */
    @Schema(description = "来源模块")
    @TableField(value = "source_module")
    private String sourceModule;

    /** 计划复习日期 */
    @Schema(description = "计划复习日期")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @TableField(value = "schedule_date")
    private Date scheduleDate;

    /** 排序分（薄弱度×遗忘风险） */
    @Schema(description = "排序分")
    @TableField(value = "sort_score")
    private BigDecimal sortScore;

    /** 物化队列日期（NULL=未物化） */
    @Schema(description = "物化队列日期")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @TableField(value = "queue_date")
    private Date queueDate;

    /** 队列内序号 */
    @Schema(description = "队列内序号")
    @TableField(value = "seq_no")
    private Integer seqNo;

    /** 状态 1待复习 2已完成 3已顺延 */
    @Schema(description = "状态")
    @TableField(value = "status")
    private Integer status;

    /** 完成时间 */
    @Schema(description = "完成时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "completed_at")
    private Date completedAt;

    /** 删除标记 0正常 1删除 */
    @TableLogic
    @TableField(value = "del_flag")
    private Integer delFlag;
}
