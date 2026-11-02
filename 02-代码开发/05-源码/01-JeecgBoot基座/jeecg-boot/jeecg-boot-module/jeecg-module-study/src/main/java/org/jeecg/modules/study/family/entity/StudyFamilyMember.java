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
 * 家庭成员（家长）
 *
 * @author jeecg-study
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "家庭成员（家长）")
@TableName("study_family_member")
public class StudyFamilyMember extends JeecgEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 所属家庭 ID */
    @Schema(description = "所属家庭 ID")
    @TableField(value = "family_id")
    private String familyId;

    /** JeecgBoot sys_user.id */
    @Schema(description = "JeecgBoot sys_user.id")
    @TableField(value = "user_id")
    private String userId;

    /** 成员角色 OWNER / PARENT */
    @Schema(description = "成员角色 OWNER / PARENT")
    @TableField(value = "member_role")
    private String memberRole;

    /** 与孩子的关系标签（妈妈/爸爸/…） */
    @Schema(description = "与孩子的关系标签（妈妈/爸爸/…）")
    @TableField(value = "relation_label")
    private String relationLabel;

    /** 状态 1正常 2已退出 */
    @Schema(description = "状态 1正常 2已退出")
    @TableField(value = "status")
    private Integer status;

    /** 删除标记 0正常 1删除 */
    @Schema(description = "删除标记 0正常 1删除")
    @TableLogic
    @TableField(value = "del_flag")
    private Integer delFlag;

}
