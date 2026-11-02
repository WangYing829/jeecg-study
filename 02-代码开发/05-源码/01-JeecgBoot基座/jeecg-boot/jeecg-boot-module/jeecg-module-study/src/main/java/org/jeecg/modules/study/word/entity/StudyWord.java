package org.jeecg.modules.study.word.entity;

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
 * 单词（课内 / 课外双线，含遗忘参数）。
 *
 * <p>对应表 {@code study_word}，是**内容表**（全局共享，不含 {@code child_id}）：
 * 课本单元词表与课外高频词由全体孩子共用。</p>
 *
 * <p>遗忘参数（{@code memory_strength} / {@code decay_lambda}）直接落在本行，
 * 是《数据库设计说明书》§5.4 给定的结构，本模块不改表。</p>
 *
 * @author jeecg-study
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "单词（课内/课外双线，含遗忘参数）")
@TableName("study_word")
public class StudyWord extends JeecgEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务ID WRD-000001 */
    @Schema(description = "业务ID WRD-000001")
    @TableField(value = "word_code")
    private String wordCode;

    /** 拼写（拼写题的答案，不随取词接口下发） */
    @Schema(description = "拼写")
    @TableField(value = "spelling")
    private String spelling;

    /** 音标 */
    @Schema(description = "音标")
    @TableField(value = "phonetic")
    private String phonetic;

    /** 释义 */
    @Schema(description = "释义")
    @TableField(value = "meaning")
    private String meaning;

    /** 线别 IN课内 / OUT课外 */
    @Schema(description = "线别 IN课内 / OUT课外")
    @TableField(value = "line_type")
    private String lineType;

    /** 教材版本ID（课内必填） */
    @Schema(description = "教材版本ID（课内必填）")
    @TableField(value = "textbook_version_id")
    private String textbookVersionId;

    /** 教材单元编码（课内） */
    @Schema(description = "教材单元编码（课内）")
    @TableField(value = "unit_code")
    private String unitCode;

    /** 单元内顺序 */
    @Schema(description = "单元内顺序")
    @TableField(value = "unit_order")
    private Integer unitOrder;

    /** 所属考点业务ID */
    @Schema(description = "所属考点业务ID")
    @TableField(value = "knowledge_point_code")
    private String knowledgePointCode;

    /** 词性 */
    @Schema(description = "词性")
    @TableField(value = "pos")
    private String pos;

    /** 例句（与语境库共用） */
    @Schema(description = "例句")
    @TableField(value = "context_sentence")
    private String contextSentence;

    /** 词频 */
    @Schema(description = "词频")
    @TableField(value = "frequency")
    private Integer frequency;

    /** 记忆强度 S */
    @Schema(description = "记忆强度S")
    @TableField(value = "memory_strength")
    private BigDecimal memoryStrength;

    /** 衰减系数 λ */
    @Schema(description = "衰减系数λ")
    @TableField(value = "decay_lambda")
    private BigDecimal decayLambda;

    /** 反馈次数 */
    @Schema(description = "反馈次数")
    @TableField(value = "feedback_count")
    private Integer feedbackCount;

    /** 最近反馈档 KNOWN/VAGUE/FORGOT/FAMILIAR */
    @Schema(description = "最近反馈档")
    @TableField(value = "last_feedback")
    private String lastFeedback;

    /** 熟词标记（熟词过滤结果） */
    @Schema(description = "熟词标记")
    @TableField(value = "known_flag")
    private Integer knownFlag;

    /** 下次复习日期 */
    @Schema(description = "下次复习日期")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @TableField(value = "next_review_at")
    private Date nextReviewAt;

    /** 状态 1启用 0停用 */
    @Schema(description = "状态 1启用 0停用")
    @TableField(value = "status")
    private Integer status;

    /** 删除标记 0正常 1删除 */
    @Schema(description = "删除标记")
    @TableLogic
    @TableField(value = "del_flag")
    private Integer delFlag;

}
