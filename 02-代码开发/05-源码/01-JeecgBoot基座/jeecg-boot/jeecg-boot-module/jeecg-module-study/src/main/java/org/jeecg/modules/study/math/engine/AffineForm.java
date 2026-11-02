package org.jeecg.modules.study.math.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 表达式关于某个变量的<b>仿射形式</b>：{@code a·x + b}。
 *
 * <p>有了仿射形式就能做「逆向提问」：已知结果 R，反解 x = (R − b) / a，
 * 全程是确定性算术，不涉及任何模型（R-02）。</p>
 *
 * @author jeecg-study
 */
public final class AffineForm {

    /** 一次项系数 */
    private final BigDecimal a;

    /** 常数项 */
    private final BigDecimal b;

    public AffineForm(BigDecimal a, BigDecimal b) {
        this.a = a == null ? BigDecimal.ZERO : a;
        this.b = b == null ? BigDecimal.ZERO : b;
    }

    /**
     * 常数形式（与变量无关）。
     *
     * @param value 常数值
     * @return 仿射形式
     */
    public static AffineForm constant(BigDecimal value) {
        return new AffineForm(BigDecimal.ZERO, value);
    }

    public BigDecimal getA() {
        return a;
    }

    public BigDecimal getB() {
        return b;
    }

    /**
     * 是否是常数（与变量无关）。
     *
     * @return true 表示不含该变量
     */
    public boolean isConstant() {
        return a.compareTo(BigDecimal.ZERO) == 0;
    }

    /**
     * 给定 x 求值。
     *
     * @param x 变量取值
     * @return 表达式取值
     */
    public BigDecimal valueAt(BigDecimal x) {
        return a.multiply(x).add(b);
    }

    /**
     * 反解变量：已知结果求 x。
     *
     * @param target 已知结果
     * @return 变量取值
     */
    public BigDecimal solveFor(BigDecimal target) {
        if (isConstant()) {
            throw new IllegalStateException("表达式与该变量无关，无法反解");
        }
        return target.subtract(b).divide(a, 12, RoundingMode.HALF_UP);
    }

    @Override
    public String toString() {
        return a.toPlainString() + "·x + " + b.toPlainString();
    }
}
