package org.jeecg.modules.study.kid.vo;

import java.io.Serializable;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 家庭码入参（孩子端第一步）
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "家庭码入参（孩子端第一步）")
public class FamilyCodeRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 家庭码（6 位大写字母数字） */
    @Schema(description = "家庭码（6 位大写字母数字）")
    private String familyCode;

}
