package org.jeecg.modules.study.family.vo;

import java.io.Serializable;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 新增孩子档案的结果（含一次性明文 PIN）
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "新增孩子档案的结果（含一次性明文 PIN）")
public class ChildCreateVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 孩子 ID */
    @Schema(description = "孩子 ID")
    private String childId;

    /** 孩子业务ID（CHD-000001） */
    @Schema(description = "孩子业务ID（CHD-000001）")
    private String childCode;

    /** 昵称 */
    @Schema(description = "昵称")
    private String nickname;

    /** 头像 */
    @Schema(description = "头像")
    private String avatar;

    /** 性别 */
    @Schema(description = "性别")
    private Integer gender;

    /** 生日 */
    @Schema(description = "生日")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date birthday;

    /** 默认年级 */
    @Schema(description = "默认年级")
    private String gradeCode;

    /** 孩子的登录用户名 */
    @Schema(description = "孩子的登录用户名")
    private String username;

    /** PIN 明文——**仅本次返回**，库里只存 PBKDF2 哈希 */
    @Schema(description = "PIN 明文——**仅本次返回**，库里只存 PBKDF2 哈希")
    private String pin;

}
