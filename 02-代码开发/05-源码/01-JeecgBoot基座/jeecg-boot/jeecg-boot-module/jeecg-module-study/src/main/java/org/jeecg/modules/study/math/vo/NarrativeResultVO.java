package org.jeecg.modules.study.math.vo;

import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 口述反馈结果（TC-04-08）。
 *
 * <p>返回<b>命中的关键词</b>与<b>缺失的关键词</b>；口述不全时<b>不直接判对</b>，
 * 只给一条苏格拉底式追问，<b>绝不给答案</b>。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "口述反馈结果")
public class NarrativeResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 母题业务ID */
    @Schema(description = "母题业务ID")
    private String motherCode;

    /** 口述判据是否通过（全部关键词命中才通过） */
    @Schema(description = "口述判据是否通过")
    private boolean narrativePassed;

    /** 命中的关键词 */
    @Schema(description = "命中的关键词")
    private List<String> hitKeywords;

    /** 缺失的关键词 */
    @Schema(description = "缺失的关键词")
    private List<String> missingKeywords;

    /** 苏格拉底式追问（口述不过关时给，不给答案） */
    @Schema(description = "苏格拉底式追问")
    private String followUpQuestion;
}
