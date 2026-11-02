package org.jeecg.modules.study.grammar.constant;

/**
 * 语法域的产出来源。
 *
 * <p>R-02：AI 只做解释层，<b>判定与答案产出必须是规则 / 语料库</b>，
 * 所以这里<b>没有</b>纯模型来源。</p>
 *
 * @author jeecg-study
 */
public final class GrammarSource {

    /** 语料库命中 */
    public static final String LIBRARY = "library";

    /** 规则判定 */
    public static final String RULE = "rule";

    /** 暂无可用产出（降级） */
    public static final String UNAVAILABLE = "unavailable";

    private GrammarSource() {
    }
}
