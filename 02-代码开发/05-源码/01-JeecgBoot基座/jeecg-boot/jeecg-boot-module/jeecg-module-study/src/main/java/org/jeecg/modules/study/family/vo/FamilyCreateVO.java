package org.jeecg.modules.study.family.vo;

import java.io.Serializable;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 创建家庭的结果（含一次性明文家庭码）
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "创建家庭的结果（含一次性明文家庭码）")
public class FamilyCreateVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 家庭 ID */
    @Schema(description = "家庭 ID")
    private String familyId;

    /** 家庭名称 */
    @Schema(description = "家庭名称")
    private String familyName;

    /** 家庭码明文——**仅本次返回**，库里只存 HMAC 哈希 */
    @Schema(description = "家庭码明文——**仅本次返回**，库里只存 HMAC 哈希")
    private String familyCode;

    /** 创建者在家庭中的角色（OWNER） */
    @Schema(description = "创建者在家庭中的角色（OWNER）")
    private String memberRole;

}
