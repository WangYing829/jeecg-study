package org.jeecg.modules.study.math.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 关卡说明（口述任务）。
 *
 * <p><b>不列关键词</b>：关键词是过关判据，列出来就等于给了答案。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "关卡说明")
public class NarrativeBriefVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 母题业务ID */
    @Schema(description = "母题业务ID")
    private String motherCode;

    /** 题型名 */
    @Schema(description = "题型名")
    private String title;

    /** 是否需要口述才能过关 */
    @Schema(description = "是否需要口述才能过关")
    private boolean narrativeRequired;

    /** 过关需要讲清的要点个数（不给要点本身） */
    @Schema(description = "要点个数")
    private Integer keyPointCount;

    /** 任务提示语 */
    @Schema(description = "任务提示语")
    private String prompt;
}
