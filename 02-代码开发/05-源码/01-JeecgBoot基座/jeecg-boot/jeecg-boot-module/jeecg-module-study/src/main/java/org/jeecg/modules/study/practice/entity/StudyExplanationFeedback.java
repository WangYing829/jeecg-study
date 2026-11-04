package org.jeecg.modules.study.practice.entity;

import java.io.Serializable;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecg.common.system.base.entity.JeecgEntity;

/**
 * 讲解申诉记录（「我不同意」，R-02 / F-FAM-08）。
 *
 * <p><b>行为表</b>：按 {@code child_id} 隔离（与内容表相反）。</p>
 *
 * <p><b>家长之间互不可见</b>：记录里存了提交人 {@code submitter_user_id}，
 * 读的时候只读本人提交的那条 —— 这样同一家庭里家长 A 也不会看到家长 B 的申诉。</p>
 *
 * <p>表名 {@code study_ai_feedback} 是《数据库设计说明书》里已登记的预留表名
 * （「AI 讲解申诉记录（「我不同意」）」），由增量脚本 23 落地。</p>
 *
 * @author jeecg-study
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "讲解申诉记录")
@TableName("study_ai_feedback")
public class StudyExplanationFeedback extends JeecgEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 所属孩子 ID（多用户隔离维度） */
    @Schema(description = "所属孩子 ID")
    @TableField(value = "child_id")
    private String childId;

    /** 内容业务ID */
    @Schema(description = "内容业务ID")
    @TableField(value = "content_id")
    private String contentId;

    /** 内容类型 */
    @Schema(description = "内容类型")
    @TableField(value = "content_type")
    private String contentType;

    /** 是否表示不同意 1是 0否 */
    @Schema(description = "是否表示不同意")
    @TableField(value = "disagree")
    private Integer disagree;

    /** 申诉理由 */
    @Schema(description = "申诉理由")
    @TableField(value = "reason")
    private String reason;

    /** 处理状态 PENDING/ACCEPTED/REJECTED */
    @Schema(description = "处理状态")
    @TableField(value = "status")
    private String status;

    /** 提交人 sys_user.id（家长之间互不可见靠它） */
    @Schema(description = "提交人")
    @TableField(value = "submitter_user_id")
    private String submitterUserId;

    /** 删除标记 0正常 1删除 */
    @TableLogic
    @TableField(value = "del_flag")
    private Integer delFlag;
}
