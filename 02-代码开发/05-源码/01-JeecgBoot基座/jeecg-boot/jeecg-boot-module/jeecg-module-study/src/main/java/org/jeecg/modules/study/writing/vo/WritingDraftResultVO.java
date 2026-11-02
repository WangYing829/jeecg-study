package org.jeecg.modules.study.writing.vo;

import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 提交初稿的结果（对照式批改）。
 *
 * <p>差异清单最多 {@code diffCount} 条，服务端硬上限 3（TC-06-03）；
 * {@code source} 恒为 {@code rule} —— 写作域没有 AI 客户端，
 * 差异全部由确定性规则算出（R-02）。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "提交初稿的结果")
public class WritingDraftResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 本篇初稿的业务ID */
    @Schema(description = "初稿业务ID")
    private String contentId;

    /** 差异清单 */
    @Schema(description = "差异清单")
    private List<WritingDiffVO> diffList;

    /** 差异条数（与服务端硬上限一致，不超过 3） */
    @Schema(description = "差异条数")
    private Integer diffCount;

    /** 差异来源：恒为 rule */
    @Schema(description = "差异来源")
    private String source;

    /** 给孩子的一句说明 */
    @Schema(description = "说明")
    private String note;
}
