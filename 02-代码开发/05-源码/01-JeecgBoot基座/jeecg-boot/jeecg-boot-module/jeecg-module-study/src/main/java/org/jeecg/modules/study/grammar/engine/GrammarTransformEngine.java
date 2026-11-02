package org.jeecg.modules.study.grammar.engine;

import org.jeecg.modules.study.grammar.vo.GrammarTransformResultVO;

/**
 * 句子变形器（F-GRM-01 / TC-05-01）。
 *
 * <p>同一中文意思给出五种英文表达：直译版 / 地道口语版 / 书面版 / 考试安全版 / 错误示范版，
 * 每种都带使用场合说明。</p>
 *
 * @author jeecg-study
 */
public interface GrammarTransformEngine {

    /**
     * 变形。
     *
     * @param zhMeaning  中文意思
     * @param grammarCode 指定语法点业务ID；为空则自动匹配
     * @return 五态变形结果
     */
    GrammarTransformResultVO transform(String zhMeaning, String grammarCode);
}
