package org.jeecg.modules.study.kid.vo;

import java.io.Serializable;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;
import java.util.List;

/**
 * 孩子登录结果
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "孩子登录结果")
public class KidLoginVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** JWT token */
    @Schema(description = "JWT token")
    private String token;

    /** 孩子 ID */
    @Schema(description = "孩子 ID")
    private String childId;

    /** 孩子业务ID */
    @Schema(description = "孩子业务ID")
    private String childCode;

    /** 昵称 */
    @Schema(description = "昵称")
    private String nickname;

    /** 头像 */
    @Schema(description = "头像")
    private String avatar;

    /** 默认年级 */
    @Schema(description = "默认年级")
    private String gradeCode;

    /** 绑定的家长昵称列表（仅昵称，不含账号） */
    @Schema(description = "绑定的家长昵称列表（仅昵称，不含账号）")
    private List<String> parentNames;

}
