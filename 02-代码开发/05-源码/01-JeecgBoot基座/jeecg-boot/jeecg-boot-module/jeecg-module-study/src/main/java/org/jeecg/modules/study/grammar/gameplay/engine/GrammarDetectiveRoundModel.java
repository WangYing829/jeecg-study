package org.jeecg.modules.study.grammar.gameplay.engine;

import java.util.ArrayList;
import java.util.List;

import org.jeecg.modules.study.grammar.vo.GrammarErrorVO;

/**
 * 句子侦探的一回合（服务端内部模型）。
 *
 * <p><b>不下发</b>：错句位置与错句正文都在服务端，取题接口只给四句话。</p>
 *
 * @author jeecg-study
 */
public class GrammarDetectiveRoundModel {

    /** 一回合里的四句话 */
    public static class DetectiveSentence {

        /** 位置（1 起） */
        private int position;

        /** 例句业务ID（正例句来自语境库；错句没有） */
        private String sentenceCode;

        /** 句子正文 */
        private String text;

        public int getPosition() {
            return position;
        }

        public void setPosition(int position) {
            this.position = position;
        }

        public String getSentenceCode() {
            return sentenceCode;
        }

        public void setSentenceCode(String sentenceCode) {
            this.sentenceCode = sentenceCode;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }

    /** 题目业务ID */
    private String questionCode;

    /** 关联语法点业务ID */
    private String grammarCode;

    /** 四句话（按位置排好） */
    private List<DetectiveSentence> sentences = new ArrayList<>();

    /** 错句在第几句（1 起） */
    private int wrongPosition;

    /** 错句正文 */
    private String wrongSentence;

    /** 规则在错句上检出的错误（讲解用） */
    private List<GrammarErrorVO> wrongErrors = new ArrayList<>();

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

    public List<DetectiveSentence> getSentences() {
        return sentences;
    }

    public void setSentences(List<DetectiveSentence> sentences) {
        this.sentences = sentences;
    }

    public int getWrongPosition() {
        return wrongPosition;
    }

    public void setWrongPosition(int wrongPosition) {
        this.wrongPosition = wrongPosition;
    }

    public String getWrongSentence() {
        return wrongSentence;
    }

    public void setWrongSentence(String wrongSentence) {
        this.wrongSentence = wrongSentence;
    }

    public List<GrammarErrorVO> getWrongErrors() {
        return wrongErrors;
    }

    public void setWrongErrors(List<GrammarErrorVO> wrongErrors) {
        this.wrongErrors = wrongErrors;
    }

}
