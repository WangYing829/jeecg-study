package org.jeecg.modules.study.math.entity;

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
 * 数学母题（五段固定结构）。
 *
 * <p><b>内容表，全局共享，不加 child_id</b>（多用户改造契约 §2.2）：
 * 母题供给成本高，家庭内共享；只有<b>变式</b>是行为表，按 child_id 隔离。</p>
 *
 * <p>五段分别落在 title / condition_types / key_relation / steps / pitfalls 五列，
 * 缺任一段都不允许保存（4007）。</p>
 *
 * @author jeecg-study
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "数学母题（五段固定结构）")
@TableName("study_mother_question")
public class StudyMotherQuestion extends JeecgEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务ID MTH-{学科码}-{序号} */
    @Schema(description = "业务ID")
    @TableField(value = "mother_code")
    private String motherCode;

    /** 第 1 段：题型名 */
    @Schema(description = "第1段 题型名")
    @TableField(value = "title")
    private String title;

    /** 第 2 段：已知条件类型（JSON 文本） */
    @Schema(description = "第2段 已知条件类型")
    @TableField(value = "condition_types")
    private String conditionTypes;

    /** 第 3 段：关键关系（过关口述对象） */
    @Schema(description = "第3段 关键关系")
    @TableField(value = "key_relation")
    private String keyRelation;

    /** 第 4 段：解题步骤链（JSON 文本） */
    @Schema(description = "第4段 解题步骤链")
    @TableField(value = "steps")
    private String steps;

    /** 第 5 段：易错点清单（JSON 文本） */
    @Schema(description = "第5段 易错点清单")
    @TableField(value = "pitfalls")
    private String pitfalls;

    /** 学科编码 MA */
    @Schema(description = "学科编码")
    @TableField(value = "subject_code")
    private String subjectCode;

    /** 所属考点业务ID */
    @Schema(description = "所属考点业务ID")
    @TableField(value = "knowledge_point_code")
    private String knowledgePointCode;

    /** 过关所需变式全对数 */
    @Schema(description = "过关所需变式全对数")
    @TableField(value = "pass_variant_count")
    private Integer passVariantCount;

    /** 已生成变式数 */
    @Schema(description = "已生成变式数")
    @TableField(value = "variant_count")
    private Integer variantCount;

    /** 闯关顺序（考纲优先级） */
    @Schema(description = "闯关顺序")
    @TableField(value = "order_no")
    private Integer orderNo;

    /** 状态 1启用 0停用 */
    @Schema(description = "状态")
    @TableField(value = "status")
    private Integer status;

    /** 删除标记 0正常 1删除 */
    @TableLogic
    @TableField(value = "del_flag")
    private Integer delFlag;
}
