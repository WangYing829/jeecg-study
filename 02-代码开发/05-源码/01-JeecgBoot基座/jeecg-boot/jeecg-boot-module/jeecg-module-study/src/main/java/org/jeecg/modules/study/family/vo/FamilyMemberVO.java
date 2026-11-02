package org.jeecg.modules.study.family.vo;

import java.io.Serializable;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 家庭成员（家长）视图
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "家庭成员（家长）视图")
public class FamilyMemberVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 成员行 ID（study_family_member.id），用于移除成员 */
    @Schema(description = "成员行 ID（study_family_member.id），用于移除成员")
    private String memberId;

    /** 与孩子的关系标签 */
    @Schema(description = "与孩子的关系标签")
    private String relationLabel;

    /** 成员角色 OWNER / PARENT */
    @Schema(description = "成员角色 OWNER / PARENT")
    private String memberRole;

    /** 状态 1正常 2已退出 */
    @Schema(description = "状态 1正常 2已退出")
    private Integer status;

    /** 是否当前登录者本人 */
    @Schema(description = "是否当前登录者本人")
    private Boolean isSelf;

    /** 加入时间 */
    @Schema(description = "加入时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date createTime;

}
