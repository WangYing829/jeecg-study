package org.jeecg.modules.study.grammar.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 语境库里的一个例句。
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "语境库例句")
public class GrammarContextItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 例句业务ID */
    @Schema(description = "例句业务ID")
    private String sentenceCode;

    /** 例句正文 */
    @Schema(description = "例句正文")
    private String sentence;

    /** 中文意思 */
    @Schema(description = "中文意思")
    private String zhMeaning;

    /** 语境类型 TEXTBOOK / EXAM / ORIGINAL */
    @Schema(description = "语境类型")
    private String contextType;

    /** 语境类型中文名 */
    @Schema(description = "语境类型中文名")
    private String contextTypeName;

    /** 关联类型 MAIN主例句 / EXTEND拓展用法 */
    @Schema(description = "关联类型")
    private String relationType;

    /** 用法说明 */
    @Schema(description = "用法说明")
    private String usageNote;

    /** 出处 */
    @Schema(description = "出处")
    private String sourceRef;

    /** 关联语法点业务ID */
    @Schema(description = "关联语法点业务ID")
    private String grammarCode;

}
