package org.jeecg.modules.study.practice.vo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 口述关键关系的反馈结果（F-MTH-03）。
 *
 * <p><b>口述不过关时绝不给答案</b>，只给一条苏格拉底式追问与命中 / 缺失的关键词（R-01）。</p>
 *
 * <p>字段口径：</p>
 * <ul>
 *   <li>{@code passed} —— 本次口述是否通过（母题口径：关键关系关键词全部命中）；</li>
 *   <li>{@code motherPassed} —— 该内容所属母题<b>当前</b>是否已过关，
 *       口径为「变式已全对」（口述结果不落库，正式过关判定走母题过关接口）；</li>
 *   <li>没有关联母题的内容没有关键关系判据，此时 {@code passed} 与 {@code motherPassed} 均为 false。</li>
 * </ul>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "口述反馈结果")
public class PracticeOralResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 内容业务ID */
    @Schema(description = "内容业务ID")
    private String contentId;

    /** 所属母题业务ID（无母题时为空） */
    @Schema(description = "所属母题业务ID")
    private String motherCode;

    /** 本次口述是否通过 */
    @Schema(description = "本次口述是否通过")
    private Boolean passed = Boolean.FALSE;

    /** 命中的关键词 */
    @Schema(description = "命中的关键词")
    private List<String> matchedKeywords = new ArrayList<>();

    /** 缺失的关键词 */
    @Schema(description = "缺失的关键词")
    private List<String> missingKeywords = new ArrayList<>();

    /** 苏格拉底式追问（口述不过关时给，不给答案） */
    @Schema(description = "苏格拉底式追问")
    private String followUpQuestion;

    /** 所属母题当前是否已过关（变式全对口径） */
    @Schema(description = "所属母题当前是否已过关")
    private Boolean motherPassed = Boolean.FALSE;
}
