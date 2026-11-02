package org.jeecg.modules.study.family.entity;

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
 * 家长↔孩子 多对多关系
 *
 * @author jeecg-study
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "家长↔孩子 多对多关系")
@TableName("study_parent_child_rel")
public class StudyParentChildRel extends JeecgEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 家长 sys_user.id */
    @Schema(description = "家长 sys_user.id")
    @TableField(value = "parent_user_id")
    private String parentUserId;

    /** 孩子 study_child.id */
    @Schema(description = "孩子 study_child.id")
    @TableField(value = "child_id")
    private String childId;

    /** 关系标签（妈妈/爸爸/…） */
    @Schema(description = "关系标签（妈妈/爸爸/…）")
    @TableField(value = "relation_label")
    private String relationLabel;

    /** 是否主家长 1是 0否（仅主家长可重置 PIN / 停用） */
    @Schema(description = "是否主家长 1是 0否（仅主家长可重置 PIN / 停用）")
    @TableField(value = "is_primary")
    private Integer isPrimary;

    /** 状态 1生效 2已解除 */
    @Schema(description = "状态 1生效 2已解除")
    @TableField(value = "status")
    private Integer status;

    /** 删除标记 0正常 1删除 */
    @Schema(description = "删除标记 0正常 1删除")
    @TableLogic
    @TableField(value = "del_flag")
    private Integer delFlag;

}
