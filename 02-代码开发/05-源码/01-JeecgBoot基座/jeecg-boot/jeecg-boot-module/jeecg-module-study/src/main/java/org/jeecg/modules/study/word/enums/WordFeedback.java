package org.jeecg.modules.study.word.enums;

import java.math.BigDecimal;

import org.jeecg.modules.study.word.constant.WordConstants;

/**
 * 四档反馈：认识 / 模糊 / 忘记 / 熟知。
 *
 * <p>F-WRD-04 明确：反馈档位**不是对/错**，因此这里没有「正确 / 错误」档，
 * 每档对应一组遗忘参数更新动作：</p>
 *
 * <table border="1">
 *   <tr><th>档位</th><th>记忆强度 S</th><th>衰减系数 λ</th></tr>
 *   <tr><td>认识 KNOWN</td><td>S + 0.35</td><td>不变</td></tr>
 *   <tr><td>模糊 VAGUE</td><td>S + 0.10</td><td>λ × 1.2</td></tr>
 *   <tr><td>忘记 FORGOT</td><td>归零</td><td>λ × 1.6</td></tr>
 *   <tr><td>熟知 FAMILIAR</td><td>S + 0.50</td><td>λ × 0.8</td></tr>
 * </table>
 *
 * @author jeecg-study
 */
public enum WordFeedback {

    /** 认识 */
    KNOWN(WordConstants.FEEDBACK_KNOWN, "认识",
            WordConstants.DELTA_KNOWN, null, false),

    /** 模糊 */
    VAGUE(WordConstants.FEEDBACK_VAGUE, "模糊",
            WordConstants.DELTA_VAGUE, WordConstants.LAMBDA_FACTOR_VAGUE, false),

    /** 忘记 */
    FORGOT(WordConstants.FEEDBACK_FORGOT, "忘记",
            null, WordConstants.LAMBDA_FACTOR_FORGOT, true),

    /** 熟知 */
    FAMILIAR(WordConstants.FEEDBACK_FAMILIAR, "熟知",
            WordConstants.DELTA_FAMILIAR, WordConstants.LAMBDA_FACTOR_FAMILIAR, false);

    private final String code;

    private final String label;

    /** 记忆强度增量；null 表示归零 */
    private final BigDecimal strengthDelta;

    /** 衰减系数乘数；null 表示不变 */
    private final BigDecimal lambdaFactor;

    /** 是否属于「没记住」，需要进统一错题池 */
    private final boolean forgotten;

    WordFeedback(String code, String label, BigDecimal strengthDelta,
                 BigDecimal lambdaFactor, boolean forgotten) {
        this.code = code;
        this.label = label;
        this.strengthDelta = strengthDelta;
        this.lambdaFactor = lambdaFactor;
        this.forgotten = forgotten;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public BigDecimal getStrengthDelta() {
        return strengthDelta;
    }

    public BigDecimal getLambdaFactor() {
        return lambdaFactor;
    }

    /**
     * 是否是「没记住」档（忘记）。
     *
     * @return 忘记档返回 true
     */
    public boolean isForgotten() {
        return forgotten;
    }

    /**
     * 计算新的记忆强度 S。
     *
     * @param current 当前 S
     * @return 新的 S
     */
    public BigDecimal nextStrength(BigDecimal current) {
        if (this == FORGOT) {
            return BigDecimal.ZERO.setScale(4, java.math.RoundingMode.HALF_UP);
        }
        BigDecimal base = current == null ? WordConstants.DEFAULT_MEMORY_STRENGTH : current;
        return base.add(strengthDelta).setScale(4, java.math.RoundingMode.HALF_UP);
    }

    /**
     * 计算新的衰减系数 λ。
     *
     * @param current 当前 λ
     * @return 新的 λ
     */
    public BigDecimal nextLambda(BigDecimal current) {
        BigDecimal base = current == null ? WordConstants.DEFAULT_DECAY_LAMBDA : current;
        if (lambdaFactor == null) {
            return base.setScale(4, java.math.RoundingMode.HALF_UP);
        }
        return base.multiply(lambdaFactor).setScale(4, java.math.RoundingMode.HALF_UP);
    }

    /**
     * 宽松解析：只认四个合法档位，其余判为非法。
     *
     * @param code 原始值
     * @return 合法枚举；非法返回 null
     */
    public static WordFeedback of(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.trim().toUpperCase();
        for (WordFeedback value : values()) {
            if (value.code.equals(normalized)) {
                return value;
            }
        }
        return null;
    }

    public static boolean isValid(String code) {
        return of(code) != null;
    }
}
