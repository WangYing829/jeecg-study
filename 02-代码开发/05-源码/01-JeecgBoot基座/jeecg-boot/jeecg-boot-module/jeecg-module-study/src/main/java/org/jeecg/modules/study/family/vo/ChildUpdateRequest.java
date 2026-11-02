package org.jeecg.modules.study.family.vo;

import java.io.Serializable;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 更新孩子档案入参
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "更新孩子档案入参")
public class ChildUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 昵称 */
    @Schema(description = "昵称")
    private String nickname;

    /** 头像 */
    @Schema(description = "头像")
    private String avatar;

    /** 性别 0未知 1男 2女 */
    @Schema(description = "性别 0未知 1男 2女")
    private Integer gender;

    /** 生日 */
    @Schema(description = "生日")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date birthday;

    /** 默认年级 G1..G12 */
    @Schema(description = "默认年级 G1..G12")
    private String gradeCode;

}
