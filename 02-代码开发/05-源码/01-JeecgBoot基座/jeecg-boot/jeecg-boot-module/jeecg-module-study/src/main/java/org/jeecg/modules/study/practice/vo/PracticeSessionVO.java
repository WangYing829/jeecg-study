package org.jeecg.modules.study.practice.vo;

import java.io.Serializable;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 练习会话。
 *
 * <p>会话 ID 由服务端生成（PS-yyyyMMdd-xxxxxx），<b>不落库</b>：
 * 作答记录里带 sessionId，会话汇总按 sessionId 从作答记录聚合。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "练习会话")
public class PracticeSessionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 会话 ID */
    @Schema(description = "会话 ID")
    private String sessionId;

    /** 练习类型 */
    @Schema(description = "练习类型")
    private String moduleType;

    /** 建议题量 */
    @Schema(description = "建议题量")
    private Integer questionCount;

    /** 会话创建时间 */
    @Schema(description = "会话创建时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;
}
