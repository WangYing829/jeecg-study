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
 * 家庭（多用户改造新增）
 *
 * @author jeecg-study
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "家庭（多用户改造新增）")
@TableName("study_family")
public class StudyFamily extends JeecgEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 家庭码 HMAC 哈希（孩子端登录用），明文不落库 */
    @Schema(description = "家庭码 HMAC 哈希（孩子端登录用），明文不落库")
    @TableField(value = "family_code")
    private String familyCode;

    /** 家庭码随机盐（pepper 轮换预留位） */
    @Schema(description = "家庭码随机盐（pepper 轮换预留位）")
    @TableField(value = "family_code_salt")
    private String familyCodeSalt;

    /** 家庭名称 */
    @Schema(description = "家庭名称")
    @TableField(value = "family_name")
    private String familyName;

    /** 状态 1正常 2冻结 */
    @Schema(description = "状态 1正常 2冻结")
    @TableField(value = "status")
    private Integer status;

    /** 删除标记 0正常 1删除 */
    @Schema(description = "删除标记 0正常 1删除")
    @TableLogic
    @TableField(value = "del_flag")
    private Integer delFlag;

}
