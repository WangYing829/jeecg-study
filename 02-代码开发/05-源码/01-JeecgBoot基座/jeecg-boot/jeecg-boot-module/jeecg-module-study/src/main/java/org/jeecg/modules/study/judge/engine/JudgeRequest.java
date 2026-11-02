package org.jeecg.modules.study.judge.engine;

import java.math.BigDecimal;

/**
 * 判定入参。
 *
 * @author jeecg-study
 */
public class JudgeRequest {

    /** 题型标识（用于选择判定策略） */
    private String contentType;

    /** 标准答案 */
    private String expectedAnswer;

    /** 用户答案 */
    private String actualAnswer;

    /** 数值容差；为空表示不做容差判定 */
    private BigDecimal tolerance;

    /** 多选答案的正确选项集合（逗号分隔），仅多选策略用 */
    private String expectedOptions;

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getExpectedAnswer() {
        return expectedAnswer;
    }

    public void setExpectedAnswer(String expectedAnswer) {
        this.expectedAnswer = expectedAnswer;
    }

    public String getActualAnswer() {
        return actualAnswer;
    }

    public void setActualAnswer(String actualAnswer) {
        this.actualAnswer = actualAnswer;
    }

    public BigDecimal getTolerance() {
        return tolerance;
    }

    public void setTolerance(BigDecimal tolerance) {
        this.tolerance = tolerance;
    }

    public String getExpectedOptions() {
        return expectedOptions;
    }

    public void setExpectedOptions(String expectedOptions) {
        this.expectedOptions = expectedOptions;
    }
}
