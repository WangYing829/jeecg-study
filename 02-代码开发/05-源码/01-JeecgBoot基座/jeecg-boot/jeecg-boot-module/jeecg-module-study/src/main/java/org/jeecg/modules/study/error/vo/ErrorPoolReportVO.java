package org.jeecg.modules.study.error.vo;

import java.io.Serializable;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 错题入池结果。
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "错题入池结果")
public class ErrorPoolReportVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 错题业务ID */
    @Schema(description = "错题业务ID")
    private String errorCode;

    /** 下次复习日期（入池当天） */
    @Schema(description = "下次复习日期")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date nextReviewAt;

    /** 是否命中幂等（同一道题重复入池返回原错题） */
    @Schema(description = "是否命中幂等")
    private Boolean duplicated;
}
