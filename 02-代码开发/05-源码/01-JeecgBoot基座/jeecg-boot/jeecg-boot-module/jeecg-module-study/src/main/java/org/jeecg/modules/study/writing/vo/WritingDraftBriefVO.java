package org.jeecg.modules.study.writing.vo;

import java.io.Serializable;
import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 初稿摘要视图（家长端只读）。
 *
 * <p><b>只给元信息，不给正文</b>：家长端只读边界（F-PAR-01）不允许下发作文正文，
 * 更不允许出现讲解、建议一类内容。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "初稿摘要")
public class WritingDraftBriefVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 初稿业务ID（家长反馈的 targetCode 就是它） */
    @Schema(description = "初稿业务ID")
    private String writingCode;

    /** 题目 */
    @Schema(description = "题目")
    private String title;

    /** 提交时间 */
    @Schema(description = "提交时间")
    private Date createTime;

    /** 孩子业务ID */
    @Schema(description = "孩子业务ID")
    private String childCode;
}
