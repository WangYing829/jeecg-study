package org.jeecg.modules.study.kid.vo;

import java.io.Serializable;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 孩子 PIN 登录入参
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "孩子 PIN 登录入参")
public class KidLoginRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 孩子 ID（来自候选列表） */
    @Schema(description = "孩子 ID（来自候选列表）")
    private String childId;

    /** PIN（4-6 位数字） */
    @Schema(description = "PIN（4-6 位数字）")
    private String pin;

}
