package org.jeecg.modules.study.error.entity;

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
 * 统一错题池条目。
 *
 * <p><b>全局只有一个错题池</b>：六个学习模块的错误都落在这里，
 * 用 {@code sourceModule} 区分来源，用 {@code childId} 隔离不同孩子
 * （ADR-018 / 契约 §2.2：内容表全局共享、行为表按孩子隔离）。</p>
 *
 * @author jeecg-study
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "统一错题池条目")
@TableName("study_error_pool")
public class StudyErrorPool extends JeecgEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 所属孩子 ID（多用户隔离维度） */
    @Schema(description = "所属孩子 ID")
    @TableField(value = "child_id")
    private String childId;

    /** 错题业务ID ERR-000001 */
    @Schema(description = "错题业务ID")
    @TableField(value = "error_code")
    private String errorCode;

    /** 所属全局上下文 */
    @Schema(description = "所属全局上下文")
    @TableField(value = "context_id")
    private String contextId;

    /** 来源模块 ERROR/WORD/MATH/GRAMMAR/WRITING */
    @Schema(description = "来源模块")
    @TableField(value = "source_module")
    private String sourceModule;

    /** 内容类型 QUESTION/WORD/SENTENCE/PARAGRAPH */
    @Schema(description = "内容类型")
    @TableField(value = "content_type")
    private String contentType;

    /** 关联内容业务ID */
    @Schema(description = "关联内容业务ID")
    @TableField(value = "content_id")
    private String contentId;

    /** 内容快照（JSON 文本） */
    @Schema(description = "内容快照")
    @TableField(value = "content_snapshot")
    private String contentSnapshot;

    /** 所属考点业务ID */
    @Schema(description = "所属考点业务ID")
    @TableField(value = "knowledge_point_code")
    private String knowledgePointCode;

    /** 学科编码 MA/CH/EN/CN */
    @Schema(description = "学科编码")
    @TableField(value = "subject_code")
    private String subjectCode;

    /** 错因（五维之一） */
    @Schema(description = "错因")
    @TableField(value = "error_cause")
    private String errorCause;

    /** 孩子原始作答 */
    @Schema(description = "孩子原始作答")
    @TableField(value = "raw_answer")
    private String rawAnswer;

    /** 正确答案 */
    @Schema(description = "正确答案")
    @TableField(value = "correct_answer")
    private String correctAnswer;

    /** 判定来源 rule / rule+model（R-02：不允许 model） */
    @Schema(description = "判定来源")
    @TableField(value = "judge_source")
    private String judgeSource;

    /** 掌握度 1未知 2学习中 3熟悉 4掌握 */
    @Schema(description = "掌握度")
    @TableField(value = "mastery_state")
    private Integer masteryState;

    /** 连续做对次数 */
    @Schema(description = "连续做对次数")
    @TableField(value = "correct_streak")
    private Integer correctStreak;

    /** 复习状态 1待复习 2已移出 */
    @Schema(description = "复习状态")
    @TableField(value = "review_status")
    private Integer reviewStatus;

    /** 下次复习日期 */
    @Schema(description = "下次复习日期")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @TableField(value = "next_review_at")
    private Date nextReviewAt;

    /** 上次复习时间 */
    @Schema(description = "上次复习时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "last_review_at")
    private Date lastReviewAt;

    /** 移出时间（连续答对 3 次后置值） */
    @Schema(description = "移出时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "archived_at")
    private Date archivedAt;

    /** 跨线升级标记（课内→课外） */
    @Schema(description = "跨线升级标记")
    @TableField(value = "upgrade_flag")
    private Integer upgradeFlag;

    /** 版权来源说明/出处（非自编必填） */
    @Schema(description = "出处")
    @TableField(value = "source_ref")
    private String sourceRef;

    /** 版权类型 SELF/PUBLIC/LICENSE */
    @Schema(description = "版权类型")
    @TableField(value = "copyright_type")
    private String copyrightType;

    /** 删除标记 0正常 1删除 */
    @TableLogic
    @TableField(value = "del_flag")
    private Integer delFlag;
}
