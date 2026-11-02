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
 * 词条 ↔ 例句关联（F-GRM-05）。
 *
 * <p>对应表 {@code study_word_context}，是**内容表**（全局共享，不含 {@code child_id}）。
 * 这一张表就是「同一批例句」的连接处：背单词域与语法域都通过它找例句，
 * 因此两边看到的例句编码与数量必然一致。</p>
 *
 * @author jeecg-study
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "词条与例句关联")
@TableName("study_word_context")
public class StudyWordContext extends JeecgEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 词条业务ID */
    @Schema(description = "词条业务ID")
    @TableField(value = "word_code")
    private String wordCode;

    /** 例句业务ID */
    @Schema(description = "例句业务ID")
    @TableField(value = "sentence_code")
    private String sentenceCode;

    /** 关联类型 MAIN主例句 / EXTEND拓展用法 */
    @Schema(description = "关联类型 MAIN / EXTEND")
    @TableField(value = "relation_type")
    private String relationType;

    /** 展示顺序 */
    @Schema(description = "展示顺序")
    @TableField(value = "sort_no")
    private Integer sortNo;

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
