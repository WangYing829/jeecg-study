package org.jeecg.modules.study.grammar.vo;

import java.io.Serializable;

import lombok.Data;

/**
 * 词条抬头信息（跨域只读背单词域 {@code study_word}）。
 *
 * <p>这是<b>内部行对象</b>，不出现在任何接口响应里。</p>
 *
 * @author jeecg-study
 */
@Data
public class GrammarWordHeaderRow implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 拼写 */
    private String spelling;

    /** 释义 */
    private String meaning;

    /** 背单词域自己的那一条例句（与语境库主例句同源） */
    private String contextSentence;

}
