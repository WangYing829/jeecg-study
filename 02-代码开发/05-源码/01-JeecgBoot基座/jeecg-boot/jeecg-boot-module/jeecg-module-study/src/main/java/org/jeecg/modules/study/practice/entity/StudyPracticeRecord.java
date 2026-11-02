package org.jeecg.modules.study.practice.entity;

import java.io.Serializable;
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
 * 作答记录（按月分表：study_practice_record_yyyyMM）。
 *
 * <p>主键取「孩子 + 会话 + 内容」的确定性摘要，因此<b>同一会话同一题重复提交只会落一条</b>
 * （TC-08-06 的落库侧保证）。</p>
 *
 * @author jeecg-study
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "作答记录")
@TableName("study_practice_record_202610")
public class StudyPracticeRecord extends JeecgEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 所属孩子 ID（多用户隔离维度） */
    @Schema(description = "所属孩子 ID")
    @TableField(value = "child_id")
    private String childId;

    /** 全局上下文 */
    @Schema(description = "全局上下文")
    @TableField(value = "context_id")
    private String contextId;

    /** 来源模块 */
    @Schema(description = "来源模块")
    @TableField(value = "source_module")
    private String sourceModule;

    /** 内容类型 */
    @Schema(description = "内容类型")
    @TableField(value = "content_type")
    private String contentType;

    /** 内容业务ID */
    @Schema(description = "内容业务ID")
    @TableField(value = "content_id")
    private String contentId;

    /** 考点业务ID */
    @Schema(description = "考点业务ID")
    @TableField(value = "knowledge_point_code")
    private String knowledgePointCode;

    /** 孩子作答 */
    @Schema(description = "孩子作答")
    @TableField(value = "answer")
    private String answer;

    /** 是否正确 1是 0否 */
    @Schema(description = "是否正确")
    @TableField(value = "correct")
    private Integer correct;

    /** 判定来源 rule / rule+model */
    @Schema(description = "判定来源")
    @TableField(value = "judge_source")
    private String judgeSource;

    /** 错因（答错时必填） */
    @Schema(description = "错因")
    @TableField(value = "error_cause")
    private String errorCause;

    /** 生成的错题业务ID */
    @Schema(description = "错题业务ID")
    @TableField(value = "error_code")
    private String errorCode;

    /** 本题耗时（毫秒） */
    @Schema(description = "本题耗时")
    @TableField(value = "duration_ms")
    private Integer durationMs;

    /** 步骤级错因打标（JSON 文本，内含 sessionId） */
    @Schema(description = "步骤级错因打标")
    @TableField(value = "step_tags")
    private String stepTags;

    /** 练习日期 */
    @Schema(description = "练习日期")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @TableField(value = "practice_date")
    private Date practiceDate;

    /** 删除标记 0正常 1删除 */
    @TableLogic
    @TableField(value = "del_flag")
    private Integer delFlag;
}
