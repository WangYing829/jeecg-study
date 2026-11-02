package org.jeecg.modules.study.family.vo;

import java.io.Serializable;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;
import java.util.List;

/**
 * 家庭详情（含成员清单）
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "家庭详情（含成员清单）")
public class FamilyDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 家庭 ID */
    @Schema(description = "家庭 ID")
    private String familyId;

    /** 家庭名称 */
    @Schema(description = "家庭名称")
    private String familyName;

    /** 有效成员数量 */
    @Schema(description = "有效成员数量")
    private Integer memberCount;

    /** 当前登录者在家庭中的角色 */
    @Schema(description = "当前登录者在家庭中的角色")
    private String myRole;

    /** 成员清单 */
    @Schema(description = "成员清单")
    private List<FamilyMemberVO> members;

}
