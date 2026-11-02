package org.jeecg.modules.study.grammar.engine;

import java.util.function.BiFunction;
import java.util.regex.Pattern;

/**
 * 一条确定性语法错误规则。
 *
 * <p>规则自带<b>最小讲解 + 同类例句 + 反例</b>三件套（F-GRM-02），
 * 讲解永远从孩子写的那句出发，不做预防性灌输（TC-05-04）。</p>
 *
 * <p>修正器的契约是「整段匹配文本 → 整段修正文本」：
 * 修正器拿得到完整的出错片段（例如 {@code am wanting}），
 * 才能把整段改对（改成 {@code want}），而不是只改其中一个词。</p>
 *
 * @author jeecg-study
 */
public class GrammarErrorRule {

    /** 规则ID */
    private final String ruleId;

    /** 对应语法点业务ID */
    private final String grammarCode;

    /** 匹配模式 */
    private final Pattern pattern;

    /** 修正器：入参（匹配到的整段文本，原句），返回修正后的整段文本；返回 null 表示判不了 */
    private final BiFunction<String, String, String> fixer;

    /** 最小讲解 */
    private final String explanation;

    /** 同类例句 */
    private final String example;

    /** 反例 */
    private final String counterExample;

    /**
     * 构造规则。
     *
     * @param ruleId         规则ID
     * @param grammarCode    对应语法点业务ID
     * @param pattern        匹配模式
     * @param fixer          修正器
     * @param explanation    最小讲解
     * @param example        同类例句
     * @param counterExample 反例
     */
    public GrammarErrorRule(String ruleId, String grammarCode, Pattern pattern,
                            BiFunction<String, String, String> fixer, String explanation,
                            String example, String counterExample) {
        this.ruleId = ruleId;
        this.grammarCode = grammarCode;
        this.pattern = pattern;
        this.fixer = fixer;
        this.explanation = explanation;
        this.example = example;
        this.counterExample = counterExample;
    }

    public String getRuleId() {
        return ruleId;
    }

    public String getGrammarCode() {
        return grammarCode;
    }

    public Pattern getPattern() {
        return pattern;
    }

    public BiFunction<String, String, String> getFixer() {
        return fixer;
    }

    public String getExplanation() {
        return explanation;
    }

    public String getExample() {
        return example;
    }

    public String getCounterExample() {
        return counterExample;
    }
}
