package org.jeecg.modules.study.grammar.vo;

import java.io.Serializable;

import lombok.Data;

/**
 * 语境库查询行（词条 ↔ 例句 关联 + 例句本体）。
 *
 * <p>这是<b>内部行对象</b>，不出现在任何接口响应里。</p>
 *
 * @author jeecg-study
 */
@Data
public class GrammarContextRow implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 例句业务ID */
    private String sentenceCode;

    /** 例句正文 */
    private String sentence;

    /** 中文意思 */
    private String zhMeaning;

    /** 语境类型 */
    private String contextType;

    /** 关联词条业务ID */
    private String wordCode;

    /** 关联语法点业务ID */
    private String grammarCode;

    /** 用法说明 */
    private String usageNote;

    /** 出处 */
    private String sourceRef;

    /** 版权类型 */
    private String copyrightType;

    /** 关联类型 MAIN / EXTEND */
    private String relationType;

    /** 展示顺序 */
    private Integer sortNo;

}
