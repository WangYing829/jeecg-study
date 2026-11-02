package org.jeecg.modules.study.grammar.gameplay.engine;

/**
 * 语法变形闯关的一回合（服务端内部模型）。
 *
 * <p><b>不下发</b>：目标表达留在服务端，取题接口只给中文意思。</p>
 *
 * @author jeecg-study
 */
public class GrammarTransformRoundModel {

    /** 题目业务ID */
    private String questionCode;

    /** 关联语法点业务ID */
    private String grammarCode;

    /** 中文意思（这一关要写什么） */
    private String meaning;

    /** 目标表达（服务端持有，取题不下发） */
    private String targetText;

    /** 目标表达所在的语境例句业务ID */
    private String targetSentenceCode;

    public String getQuestionCode() {
        return questionCode;
    }

    public void setQuestionCode(String questionCode) {
        this.questionCode = questionCode;
    }

    public String getGrammarCode() {
        return grammarCode;
    }

    public void setGrammarCode(String grammarCode) {
        this.grammarCode = grammarCode;
    }

    public String getMeaning() {
        return meaning;
    }

    public void setMeaning(String meaning) {
        this.meaning = meaning;
    }

    public String getTargetText() {
        return targetText;
    }

    public void setTargetText(String targetText) {
        this.targetText = targetText;
    }

    public String getTargetSentenceCode() {
        return targetSentenceCode;
    }

    public void setTargetSentenceCode(String targetSentenceCode) {
        this.targetSentenceCode = targetSentenceCode;
    }

}
