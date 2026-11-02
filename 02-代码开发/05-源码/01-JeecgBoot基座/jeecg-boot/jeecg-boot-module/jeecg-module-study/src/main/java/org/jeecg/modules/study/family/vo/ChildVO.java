package org.jeecg.modules.study.family.vo;

import java.io.Serializable;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 孩子档案视图（家长视角）
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "孩子档案视图（家长视角）")
public class ChildVO implements Serializable {

    private static final long serialVersionUID = 1L;

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

    /** 状态 1正常 2停用 */
    @Schema(description = "状态 1正常 2停用")
    private Integer status;

    /** 当前家长与该孩子的关系标签 */
    @Schema(description = "当前家长与该孩子的关系标签")
    private String relationLabel;

    /** 当前家长是否主家长 */
    @Schema(description = "当前家长是否主家长")
    private Boolean isPrimary;

}
