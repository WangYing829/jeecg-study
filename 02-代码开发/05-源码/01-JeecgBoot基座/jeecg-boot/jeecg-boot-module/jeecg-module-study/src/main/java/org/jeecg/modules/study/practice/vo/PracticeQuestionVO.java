package org.jeecg.modules.study.practice.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 取题响应。
 *
 * <p><b>红线 R01-1</b>：本对象<b>不允许</b>出现 {@code answer} / {@code analysis} /
 * {@code correctAnswer} / {@code solution} 字段——前端拿不到答案，就无法在未作答时展示。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "取题响应")
public class PracticeQuestionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 内容业务ID */
    @Schema(description = "内容业务ID")
    private String contentId;

    /** 练习类型：word / math / grammar / writing / wrong */
    @Schema(description = "练习类型")
    private String moduleType;

    /** 题型 */
    @Schema(description = "题型")
    private String questionType;

    /** 学科编码 */
    @Schema(description = "学科编码")
    private String subjectCode;

    /** 考点业务ID */
    @Schema(description = "考点业务ID")
    private String knowledgePointCode;

    /** 题干 */
    @Schema(description = "题干")
    private String stem;

    /** 选项（JSON 文本） */
    @Schema(description = "选项")
    private String options;

    /** 难度 1-5 */
    @Schema(description = "难度")
    private Integer difficulty;

    /** 作答控件规格（不是答案） */
    @Schema(description = "作答控件规格")
    private InputSpecVO inputSpec;

    /** 来源 SELF/PUBLIC/LICENSE */
    @Schema(description = "来源")
    private String sourceType;

    /** 所属母题业务ID（变式题才有） */
    @Schema(description = "所属母题业务ID")
    private String motherQuestionCode;

    /** 变式层级 1换数字 2换情境 3逆向 */
    @Schema(description = "变式层级")
    private Integer variantLayer;

    /** 错题业务ID（错题重做时才有） */
    @Schema(description = "错题业务ID")
    private String errorCode;
}
