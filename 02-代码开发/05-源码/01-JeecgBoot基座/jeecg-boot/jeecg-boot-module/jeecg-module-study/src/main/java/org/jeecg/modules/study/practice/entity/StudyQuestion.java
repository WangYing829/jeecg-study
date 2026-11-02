package org.jeecg.modules.study.practice.entity;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecg.common.system.base.entity.JeecgEntity;

/**
 * 题目（全局内容表，无 child_id）。
 *
 * <p>练习取题只读这张表；<b>答案与解析不允许出现在取题响应里</b>（R-01）。</p>
 *
 * @author jeecg-study
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "题目")
@TableName("study_question")
public class StudyQuestion extends JeecgEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务ID QST-{来源}-{年份}-{序号} */
    @Schema(description = "业务ID")
    @TableField(value = "question_code")
    private String questionCode;

    /** 题型 SINGLE/FILL/NUMERIC/EXPRESSION/WORD/SENTENCE */
    @Schema(description = "题型")
    @TableField(value = "question_type")
    private String questionType;

    /** 学科编码 */
    @Schema(description = "学科编码")
    @TableField(value = "subject_code")
    private String subjectCode;

    /** 所属考点业务ID */
    @Schema(description = "所属考点业务ID")
    @TableField(value = "knowledge_point_code")
    private String knowledgePointCode;

    /** 题干 */
    @Schema(description = "题干")
    @TableField(value = "stem")
    private String stem;

    /** 选项（JSON 文本） */
    @Schema(description = "选项")
    @TableField(value = "options")
    private String options;

    /** 正确答案——**只允许在作答后返回** */
    @Schema(description = "正确答案")
    @TableField(value = "answer")
    private String answer;

    /** 数值容差 */
    @Schema(description = "数值容差")
    @TableField(value = "answer_tolerance")
    private BigDecimal answerTolerance;

    /** 解析——**只允许在作答后返回** */
    @Schema(description = "解析")
    @TableField(value = "analysis")
    private String analysis;

    /** 难度 1-5 */
    @Schema(description = "难度")
    @TableField(value = "difficulty")
    private Integer difficulty;

    /** 来源 SELF/PUBLIC/LICENSE */
    @Schema(description = "来源")
    @TableField(value = "source_type")
    private String sourceType;

    /** 出处 */
    @Schema(description = "出处")
    @TableField(value = "source_ref")
    private String sourceRef;

    /** 版权类型 */
    @Schema(description = "版权类型")
    @TableField(value = "copyright_type")
    private String copyrightType;

    /** 若为变式：所属母题业务ID */
    @Schema(description = "所属母题业务ID")
    @TableField(value = "mother_question_code")
    private String motherQuestionCode;

    /** 变式层级 1换数字 2换情境 3逆向 */
    @Schema(description = "变式层级")
    @TableField(value = "variant_layer")
    private Integer variantLayer;

    /** 状态 1启用 0停用 */
    @Schema(description = "状态")
    @TableField(value = "status")
    private Integer status;

    /** 删除标记 0正常 1删除 */
    @TableLogic
    @TableField(value = "del_flag")
    private Integer delFlag;
}
