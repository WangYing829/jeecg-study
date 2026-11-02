package org.jeecg.modules.study.grammar.service;

import org.jeecg.modules.study.grammar.vo.GrammarContextResultVO;

/**
 * 例句语境库服务（F-GRM-05 语境库复用）。
 *
 * @author jeecg-study
 */
public interface StudyGrammarContextService {

    /**
     * 查例句：按词条或按语法点。
     *
     * <p>查到的就是<b>同一批例句</b>——与背单词域共用 {@code study_word_context}
     * 这一张关联表，编码与数量都从那里来。</p>
     *
     * @param wordCode    词条业务ID；为空则按语法点查
     * @param grammarCode 语法点业务ID；为空则按词条查
     * @param contextType 语境类型（TEXTBOOK / EXAM / ORIGINAL）；为空不过滤
     * @param limit       返回条数上限；为空取默认
     * @return 例句查询结果
     */
    GrammarContextResultVO context(String wordCode, String grammarCode, String contextType, Integer limit);

}
