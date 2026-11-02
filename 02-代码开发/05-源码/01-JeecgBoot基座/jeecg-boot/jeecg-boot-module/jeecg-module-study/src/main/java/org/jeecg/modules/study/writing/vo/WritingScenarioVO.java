package org.jeecg.modules.study.writing.vo;

import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 写作情境视图（一个情境 = 一种处境）。
 *
 * <p><b>红线约束</b>：本视图只含「处境 / 年龄 / 细节 / 提示」，
 * <b>没有任何可以直接抄走的句子</b>；也不含分数、名次类字段（R-04）。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "写作情境")
public class WritingScenarioVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 情境业务ID */
    @Schema(description = "情境业务ID")
    private String scenarioCode;

    /** 主题编码 */
    @Schema(description = "主题编码")
    private String themeCode;

    /** 主题名称 */
    @Schema(description = "主题名称")
    private String themeName;

    /** 情境标题：处境一句话 */
    @Schema(description = "情境标题")
    private String title;

    /** 处境描述 */
    @Schema(description = "处境描述")
    private String situationDesc;

    /** 年龄段 */
    @Schema(description = "年龄段")
    private String ageStage;

    /** 年龄下限 */
    @Schema(description = "年龄下限")
    private Integer ageMin;

    /** 年龄上限 */
    @Schema(description = "年龄上限")
    private Integer ageMax;

    /** 细节线索（可观察的物与动作） */
    @Schema(description = "细节线索")
    private List<String> detailClues;

    /** 适用学段 */
    @Schema(description = "适用学段")
    private String stageCode;

    /** 适用文体 */
    @Schema(description = "适用文体")
    private String genre;

    /** 难度层级 1…5 */
    @Schema(description = "难度层级")
    private Integer difficulty;

    /** 写作提示题：引导孩子填自己的经历 */
    @Schema(description = "写作提示题")
    private String promptQuestion;

    /** 限制条件 */
    @Schema(description = "限制条件")
    private String restrictionNote;

    /** 禁用提示：禁止写成金句 / 禁止抄范文 */
    @Schema(description = "禁用提示")
    private String forbiddenWords;
}
