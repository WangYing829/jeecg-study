package org.jeecg.modules.study.practice.vo;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 作答记录条目。
 *
 * <p><b>不下发作答原文与正确答案</b>：这里只给「做了什么题、对不对、错在哪」，
 * 既满足家长端只看客观趋势（R-04），也不泄露答案（R-02）。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "作答记录条目")
public class PracticeRecordVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 内容业务ID */
    @Schema(description = "内容业务ID")
    private String contentId;

    /** 内容类型 QUESTION/WORD/SENTENCE/PARAGRAPH */
    @Schema(description = "内容类型")
    private String contentType;

    /** 来源模块 MATH/WORD/GRAMMAR/WRITING/ERROR */
    @Schema(description = "来源模块")
    private String sourceModule;

    /** 考点业务ID */
    @Schema(description = "考点业务ID")
    private String knowledgePointCode;

    /** 是否正确 1是 0否 */
    @Schema(description = "是否正确")
    private Integer correct;

    /** 错因（答错时有值） */
    @Schema(description = "错因")
    private String errorCause;

    /** 生成的错题业务ID（答错时才有） */
    @Schema(description = "错题业务ID")
    private String errorCode;

    /** 本题耗时（毫秒） */
    @Schema(description = "本题耗时（毫秒）")
    private Integer durationMs;

    /** 练习日期 */
    @Schema(description = "练习日期")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    private java.util.Date practiceDate;

    /** 提交时间 */
    @Schema(description = "提交时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private java.util.Date createTime;
}
