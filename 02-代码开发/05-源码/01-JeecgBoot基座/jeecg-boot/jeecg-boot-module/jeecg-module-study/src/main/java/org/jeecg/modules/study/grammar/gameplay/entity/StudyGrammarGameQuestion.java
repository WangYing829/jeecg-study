package org.jeecg.modules.study.grammar.gameplay.entity;

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
 * 语法玩法题库（F-GRM-04）。
 *
 * <p>对应表 {@code study_grammar_game_question}，是**内容表**（全局共享，不含 {@code child_id}）。</p>
 *
 * <p><b>题库只存组合关系，不存例句正文</b>：正例句指向语境库
 * {@code study_sentence_context}（与背单词域共用同一批），
 * 只有「这一关的错句」是玩法自己的题目素材——性质同 {@code study_question.answer}，
 * 服务端持有，<b>取题接口不下发</b>（R-01）。</p>
 *
 * @author jeecg-study
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "语法玩法题库")
@TableName("study_grammar_game_question")
public class StudyGrammarGameQuestion extends JeecgEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务ID GAM-000001 */
    @Schema(description = "业务ID GAM-000001")
    @TableField(value = "question_code")
    private String questionCode;

    /** 玩法类型 DETECTIVE / TRANSFORM */
    @Schema(description = "玩法类型")
    @TableField(value = "game_type")
    private String gameType;

    /** 关联语法点业务ID（可空） */
    @Schema(description = "关联语法点业务ID")
    @TableField(value = "grammar_code")
    private String grammarCode;

    /** 目标表达所在的语境例句业务ID（变形闯关用） */
    @Schema(description = "目标例句业务ID")
    @TableField(value = "sentence_code")
    private String sentenceCode;

    /** 正例句业务ID 列表 JSON（句子侦探用，指向语境库） */
    @Schema(description = "正例句业务ID列表JSON")
    @TableField(value = "clean_sentence_codes")
    private String cleanSentenceCodes;

    /** 这一关的错句（服务端专用，取题接口不下发） */
    @Schema(description = "错句（服务端专用）")
    @TableField(value = "wrong_sentence")
    private String wrongSentence;

    /** 错句在第几句（1 起） */
    @Schema(description = "错句位置")
    @TableField(value = "wrong_position")
    private Integer wrongPosition;

    /** 难度 1 起 */
    @Schema(description = "难度")
    @TableField(value = "difficulty")
    private Integer difficulty;

    /** 判定来源，恒为 rule（R-02） */
    @Schema(description = "判定来源")
    @TableField(value = "judge_source")
    private String judgeSource;

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
