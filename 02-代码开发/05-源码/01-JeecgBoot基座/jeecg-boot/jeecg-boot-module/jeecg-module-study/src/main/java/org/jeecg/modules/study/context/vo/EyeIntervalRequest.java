package org.jeecg.modules.study.context.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 护眼提醒间隔入参。
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "护眼提醒间隔入参")
public class EyeIntervalRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 护眼提醒间隔（分钟），合法范围 1–240 */
    @Schema(description = "护眼提醒间隔（分钟），合法范围 1-240")
    private Integer eyeIntervalMin;

}
