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
 * 孩子档案（学习端身份载体）
 *
 * @author jeecg-study
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "孩子档案（学习端身份载体）")
@TableName("study_child")
public class StudyChild extends JeecgEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务ID CHD-000001 */
    @Schema(description = "业务ID CHD-000001")
    @TableField(value = "child_code")
    private String childCode;

    /** 所属家庭 ID */
    @Schema(description = "所属家庭 ID")
    @TableField(value = "family_id")
    private String familyId;

    /** 孩子的登录账号 sys_user.id（角色 study_kid） */
    @Schema(description = "孩子的登录账号 sys_user.id（角色 study_kid）")
    @TableField(value = "user_id")
    private String userId;

    /** 昵称（学习端显示） */
    @Schema(description = "昵称（学习端显示）")
    @TableField(value = "nickname")
    private String nickname;

    /** 头像 */
    @Schema(description = "头像")
    @TableField(value = "avatar")
    private String avatar;

    /** 性别 0未知 1男 2女 */
    @Schema(description = "性别 0未知 1男 2女")
    @TableField(value = "gender")
    private Integer gender;

    /** 生日（仅本人可见，不参与任何比较） */
    @Schema(description = "生日（仅本人可见，不参与任何比较）")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @TableField(value = "birthday")
    private Date birthday;

    /** 默认年级 G1..G12 */
    @Schema(description = "默认年级 G1..G12")
    @TableField(value = "grade_code")
    private String gradeCode;

    /** PIN 哈希（PBKDF2，不存明文） */
    @Schema(description = "PIN 哈希（PBKDF2，不存明文）")
    @TableField(value = "pin_hash")
    private String pinHash;

    /** PIN 每行独立盐 */
    @Schema(description = "PIN 每行独立盐")
    @TableField(value = "pin_salt")
    private String pinSalt;

    /** PIN 连续失败次数 */
    @Schema(description = "PIN 连续失败次数")
    @TableField(value = "pin_fail_count")
    private Integer pinFailCount;

    /** PIN 锁定截止（失败 5 次锁 10 分钟） */
    @Schema(description = "PIN 锁定截止（失败 5 次锁 10 分钟）")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "pin_lock_until")
    private Date pinLockUntil;

    /** 状态 1正常 2停用 */
    @Schema(description = "状态 1正常 2停用")
    @TableField(value = "status")
    private Integer status;

    /** 删除标记 0正常 1删除 */
    @Schema(description = "删除标记 0正常 1删除")
    @TableLogic
    @TableField(value = "del_flag")
    private Integer delFlag;

}
