package org.jeecg.modules.study.grammar.gameplay.vo;

import java.io.Serializable;
import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 我的错句收藏馆里的一条错句（F-GRM-04 第三个玩法）。
 *
 * <p>数据来自<b>统一错题池</b>，按 {@code child_id} 隔离，
 * 不另建错句表（F-GRM-06 / ADR-024）。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "错句收藏馆条目")
public class GrammarGameCollectionItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 错题业务ID */
    @Schema(description = "错题业务ID")
    private String errorCode;

    /** 错句原文 */
    @Schema(description = "错句原文")
    private String originalSentence;

    /** 改正后的句子 */
    @Schema(description = "改正后的句子")
    private String correctedSentence;

    /** 错因 */
    @Schema(description = "错因")
    private String errorCause;

    /** 判定来源（恒为 rule） */
    @Schema(description = "判定来源")
    private String judgeSource;

    /** 是否已移出复习队列 */
    @Schema(description = "是否已移出复习队列")
    private Boolean archived;

    /** 下次复习日期 */
    @Schema(description = "下次复习日期")
    private Date nextReviewAt;

}
