package org.jeecg.modules.study.judge.engine;

import java.math.BigDecimal;

/**
 * 判定结果。
 *
 * <p><b>字段口径</b>：</p>
 * <ul>
 *   <li>{@code judgeSource} —— 只能是 {@code rule} 或 {@code rule+model}；
 *       判定本身永远来自规则，模型最多参与「解释」。</li>
 *   <li>{@code basis} —— 判定依据，必须人可读且非空（TC-08-11）。</li>
 * </ul>
 *
 * @author jeecg-study
 */
public class JudgeResult {

    /** 是否正确 */
    private boolean correct;

    /** 判定来源：rule / rule+model */
    private String judgeSource;

    /** 判定依据（人可读，非空） */
    private String basis;

    /** 实际使用的容差；未使用容差时为 null */
    private BigDecimal tolerance;

    public boolean isCorrect() {
        return correct;
    }

    public void setCorrect(boolean correct) {
        this.correct = correct;
    }

    public String getJudgeSource() {
        return judgeSource;
    }

    public void setJudgeSource(String judgeSource) {
        this.judgeSource = judgeSource;
    }

    public String getBasis() {
        return basis;
    }

    public void setBasis(String basis) {
        this.basis = basis;
    }

    public BigDecimal getTolerance() {
        return tolerance;
    }

    public void setTolerance(BigDecimal tolerance) {
        this.tolerance = tolerance;
    }
}
