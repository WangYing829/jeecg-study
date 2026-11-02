package org.jeecg.modules.study.math.entity;

import java.io.Serializable;
import java.math.BigDecimal;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecg.common.system.base.entity.JeecgEntity;

/**
 * 母题变式（换数字 / 换情境 / 逆向三层）。
 *
 * <p><b>行为表，按 child_id 隔离</b>：一个孩子生成的变式别人看不到。
 * 母题本身是内容表（全局共享），变式是「某个孩子的这一关」。</p>
 *
 * <p><b>answer 只在服务端参与判定</b>：R-01 反搜题，任何响应体都不下发该字段。</p>
 *
 * @author jeecg-study
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "母题变式")
@TableName("study_question_variant")
public class StudyQuestionVariant extends JeecgEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 所属孩子 ID（多用户隔离维度） */
    @Schema(description = "所属孩子 ID")
    @TableField(value = "child_id")
    private String childId;

    /** 业务ID QST-VAR-{母题码}-{层级}-{序号} */
    @Schema(description = "业务ID")
    @TableField(value = "variant_code")
    private String variantCode;

    /** 母题业务ID */
    @Schema(description = "母题业务ID")
    @TableField(value = "mother_code")
    private String motherCode;

    /** 变式层级 1换数字 2换情境 3逆向 */
    @Schema(description = "变式层级")
    @TableField(value = "layer")
    private Integer layer;

    /** 生成的题目业务ID */
    @Schema(description = "题目业务ID")
    @TableField(value = "question_code")
    private String questionCode;

    /** 参数替换快照（JSON 文本） */
    @Schema(description = "参数替换快照")
    @TableField(value = "params_snapshot")
    private String paramsSnapshot;

    /** 题干 */
    @Schema(description = "题干")
    @TableField(value = "stem")
    private String stem;

    /** 答案（确定性计算所得，<b>只用于服务端判定</b>） */
    @Schema(description = "答案")
    @TableField(value = "answer")
    private String answer;

    /** 数值容差 */
    @Schema(description = "数值容差")
    @TableField(value = "answer_tolerance")
    private BigDecimal answerTolerance;

    /** 生成方式 RULE / RULE+MODEL（<b>不可能是 MODEL</b>） */
    @Schema(description = "生成方式")
    @TableField(value = "generated_by")
    private String generatedBy;

    /** 状态 1启用 0停用 */
    @Schema(description = "状态")
    @TableField(value = "status")
    private Integer status;

    /** 删除标记 0正常 1删除 */
    @TableLogic
    @TableField(value = "del_flag")
    private Integer delFlag;
}
