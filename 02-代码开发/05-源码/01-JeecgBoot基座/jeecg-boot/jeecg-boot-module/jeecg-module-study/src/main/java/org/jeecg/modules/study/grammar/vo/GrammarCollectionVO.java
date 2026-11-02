package org.jeecg.modules.study.grammar.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import java.util.Date;

/**
 * 错句收藏馆条目
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "错句收藏馆条目")
public class GrammarCollectionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 错题业务ID */
    @Schema(description = "错题业务ID")
    private String errorCode;

    /** 内容ID */
    @Schema(description = "内容ID")
    private String contentId;

    /** 错因（五维之一） */
    @Schema(description = "错因（五维之一）")
    private String errorCause;

    /** 孩子写的原句 */
    @Schema(description = "孩子写的原句")
    private String originalSentence;

    /** 改正后的句子 */
    @Schema(description = "改正后的句子")
    private String correctedSentence;

    /** 判定来源 rule / rule+model */
    @Schema(description = "判定来源 rule / rule+model")
    private String judgeSource;

    /** 掌握状态 */
    @Schema(description = "掌握状态")
    private Integer masteryState;

    /** 是否已移出复习队列 */
    @Schema(description = "是否已移出复习队列")
    private Boolean archived;

    /** 下次复习日期 */
    @Schema(description = "下次复习日期")
    private Date nextReviewAt;

}
