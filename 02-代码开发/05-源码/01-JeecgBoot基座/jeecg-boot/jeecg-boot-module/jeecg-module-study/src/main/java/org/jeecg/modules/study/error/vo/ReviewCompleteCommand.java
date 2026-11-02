package org.jeecg.modules.study.error.vo;

import java.io.Serializable;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 完成一项复习的入参。
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "完成一项复习的入参")
public class ReviewCompleteCommand implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 错题业务ID */
    @Schema(description = "错题业务ID")
    private String errorCode;

    /** 本次是否答对 */
    @Schema(description = "本次是否答对")
    private Boolean correct;

    /** 错因（答错时必填，缺失返回 4001） */
    @Schema(description = "错因")
    private String errorCause;

    /** 复习日期；不填取当天（服务端计算，前端不参与排期） */
    @Schema(description = "复习日期")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date reviewDate;

    /** 变式作答结果 */
    @Schema(description = "变式作答结果")
    private java.util.List<ErrorVariantResult> variantResults;
}
