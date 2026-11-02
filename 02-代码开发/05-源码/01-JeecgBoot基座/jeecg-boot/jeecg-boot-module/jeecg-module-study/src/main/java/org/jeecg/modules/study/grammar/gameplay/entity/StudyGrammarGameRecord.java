package org.jeecg.modules.study.grammar.gameplay.entity;

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
import org.springframework.format.annotation.DateTimeFormat;

import org.jeecg.common.system.base.entity.JeecgEntity;

/**
 * 语法玩法作答记录（F-GRM-04）。
 *
 * <p>对应表 {@code study_grammar_game_record}，是<b>行为表</b>：
 * {@code child_id} <b>必填</b>，孩子只看得到自己的闯关记录（R-04 / ADR-018）。</p>
 *
 * @author jeecg-study
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "语法玩法作答记录")
@TableName("study_grammar_game_record")
public class StudyGrammarGameRecord extends JeecgEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 所属孩子（多用户隔离，必填） */
    @Schema(description = "所属孩子")
    @TableField(value = "child_id")
    private String childId;

    /** 玩法类型 DETECTIVE / TRANSFORM */
    @Schema(description = "玩法类型")
    @TableField(value = "game_type")
    private String gameType;

    /** 题目业务ID */
    @Schema(description = "题目业务ID")
    @TableField(value = "question_code")
    private String questionCode;

    /** 关联语法点业务ID */
    @Schema(description = "关联语法点业务ID")
    @TableField(value = "grammar_code")
    private String grammarCode;

    /** 孩子的作答 */
    @Schema(description = "孩子的作答")
    @TableField(value = "submitted")
    private String submitted;

    /** 是否通过 1是 0否 */
    @Schema(description = "是否通过")
    @TableField(value = "correct")
    private Integer correct;

    /** 判定来源，恒为 rule（R-02） */
    @Schema(description = "判定来源")
    @TableField(value = "judge_source")
    private String judgeSource;

    /** 错因（答错时必填） */
    @Schema(description = "错因")
    @TableField(value = "error_cause")
    private String errorCause;

    /** 进入统一错题池后的错题业务ID */
    @Schema(description = "错题业务ID")
    @TableField(value = "error_code")
    private String errorCode;

    /** 本关耗时（毫秒） */
    @Schema(description = "耗时毫秒")
    @TableField(value = "duration_ms")
    private Integer durationMs;

    /** 作答日期（由数据库 CURDATE() 落库） */
    @Schema(description = "作答日期")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @TableField(value = "play_date")
    private Date playDate;

    /** 删除标记 0正常 1删除 */
    @Schema(description = "删除标记")
    @TableLogic
    @TableField(value = "del_flag")
    private Integer delFlag;

}
