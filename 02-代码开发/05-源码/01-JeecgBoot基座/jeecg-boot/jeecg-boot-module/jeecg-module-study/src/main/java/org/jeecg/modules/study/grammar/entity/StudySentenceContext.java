package org.jeecg.modules.study.grammar.entity;

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
 * 例句语境库（F-GRM-05）。
 *
 * <p>对应表 {@code study_sentence_context}，是**内容表**（全局共享，不含 {@code child_id}）：
 * 这一批例句由背单词域与语法域共用，一次建库两处受益。</p>
 *
 * <p>主例句的正文与背单词域 {@code study_word.context_sentence} <b>是同一句</b>，
 * 不是两份拷贝——两边的对应关系落在 {@code study_word_context}。</p>
 *
 * @author jeecg-study
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "例句语境库（背单词与语法共用）")
@TableName("study_sentence_context")
public class StudySentenceContext extends JeecgEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务ID SCT-000001 */
    @Schema(description = "业务ID SCT-000001")
    @TableField(value = "sentence_code")
    private String sentenceCode;

    /** 例句正文（唯一语料） */
    @Schema(description = "例句正文")
    @TableField(value = "sentence")
    private String sentence;

    /** 中文意思 */
    @Schema(description = "中文意思")
    @TableField(value = "zh_meaning")
    private String zhMeaning;

    /** 语境类型 TEXTBOOK课文 / EXAM真题 / ORIGINAL原声素材 */
    @Schema(description = "语境类型 TEXTBOOK / EXAM / ORIGINAL")
    @TableField(value = "context_type")
    private String contextType;

    /** 关联词条业务ID（可空：纯语法例句没有对应词条） */
    @Schema(description = "关联词条业务ID")
    @TableField(value = "word_code")
    private String wordCode;

    /** 关联语法点业务ID（可空） */
    @Schema(description = "关联语法点业务ID")
    @TableField(value = "grammar_code")
    private String grammarCode;

    /** 用法说明（同一个词在不同语境下的差别） */
    @Schema(description = "用法说明")
    @TableField(value = "usage_note")
    private String usageNote;

    /** 出处（非自编内容必填） */
    @Schema(description = "出处")
    @TableField(value = "source_ref")
    private String sourceRef;

    /** 版权类型 SELF / PUBLIC / LICENSE */
    @Schema(description = "版权类型")
    @TableField(value = "copyright_type")
    private String copyrightType;

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
