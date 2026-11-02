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
 * 英语语法点（内容表，全局共享，<b>不按孩子隔离</b>）。
 *
 * <p>表：{@code study_grammar_point}。</p>
 *
 * @author jeecg-study
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "英语语法点")
@TableName("study_grammar_point")
public class StudyGrammarPoint extends JeecgEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务ID GRP-000001 */
    @Schema(description = "业务ID")
    @TableField(value = "grammar_code")
    private String grammarCode;

    /** 语法点名称（第 1 段） */
    @Schema(description = "语法点名称")
    @TableField(value = "title")
    private String title;

    /** 最小讲解：一句话规则 */
    @Schema(description = "最小讲解")
    @TableField(value = "rule")
    private String rule;

    /** 同类例句 */
    @Schema(description = "同类例句")
    @TableField(value = "example")
    private String example;

    /** 反例 */
    @Schema(description = "反例")
    @TableField(value = "counter_example")
    private String counterExample;

    /** 对应中考题型：单选 / 完形 / 语法填空 / 书面表达 */
    @Schema(description = "对应中考题型")
    @TableField(value = "exam_type")
    private String examType;

    /** 学科编码 EN */
    @Schema(description = "学科编码")
    @TableField(value = "subject_code")
    private String subjectCode;

    /** 所属考点业务ID */
    @Schema(description = "所属考点业务ID")
    @TableField(value = "knowledge_point_code")
    private String knowledgePointCode;

    /** 句子变形器语料：JSON 数组 */
    @Schema(description = "句子变形器语料")
    @TableField(value = "transform_variants")
    private String transformVariants;

    /** 状态 1启用 0停用 */
    @Schema(description = "状态")
    @TableField(value = "status")
    private Integer status;

    /** 删除标记 */
    @Schema(description = "删除标记")
    @TableLogic
    @TableField(value = "del_flag")
    private Integer delFlag;
}
