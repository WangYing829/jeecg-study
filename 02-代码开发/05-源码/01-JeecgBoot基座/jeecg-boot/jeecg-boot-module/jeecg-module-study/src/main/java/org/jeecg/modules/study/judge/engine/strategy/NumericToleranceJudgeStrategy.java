package org.jeecg.modules.study.judge.engine.strategy;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.jeecg.modules.study.common.enums.JudgeSource;
import org.jeecg.modules.study.judge.engine.JudgeRequest;
import org.jeecg.modules.study.judge.engine.JudgeResult;
import org.jeecg.modules.study.judge.engine.JudgeStrategy;

/**
 * 数值容差判定。
 *
 * <p>边界口径：差值 <b>小于</b>容差才算对，<b>等于</b>容差算错
 * （TC-08-10：容差 0.05 时 12.54 对、12.55 错）。</p>
 *
 * @author jeecg-study
 */
@Component
public class NumericToleranceJudgeStrategy implements JudgeStrategy {

    /** 题型标识 */
    public static final String CONTENT_TYPE = "NUMERIC";

    /** 默认容差 */
    public static final BigDecimal DEFAULT_TOLERANCE = new BigDecimal("0.05");

    @Override
    public String contentType() {
        return CONTENT_TYPE;
    }

    @Override
    public JudgeResult judge(JudgeRequest request) {
        BigDecimal tolerance = request.getTolerance() == null ? DEFAULT_TOLERANCE : request.getTolerance();
        BigDecimal expected = parse(request.getExpectedAnswer());
        BigDecimal actual = parse(request.getActualAnswer());

        JudgeResult result = new JudgeResult();
        result.setJudgeSource(JudgeSource.RULE.getCode());
        result.setTolerance(tolerance);

        if (expected == null || actual == null) {
            result.setCorrect(false);
            result.setBasis("答案不是可判定的数值，无法按容差判定");
            return result;
        }
        BigDecimal diff = expected.subtract(actual).abs();
        // update-begin---author:mavis ---date:2026-10-31  for：【TC-08-10】容差边界改为「差值 < 容差」才算对（等于容差算错）-----------
        boolean correct = diff.compareTo(tolerance) < 0;
        // update-end---author:mavis ---date:2026-10-31  for：【TC-08-10】容差边界改为「差值 < 容差」才算对（等于容差算错）-----------
        result.setCorrect(correct);
        result.setBasis(correct
                ? "与标准答案相差 " + diff.stripTrailingZeros().toPlainString() + "，在容差 " + tolerance.toPlainString() + " 之内"
                : "与标准答案相差 " + diff.stripTrailingZeros().toPlainString() + "，超出容差 " + tolerance.toPlainString());
        return result;
    }

    private BigDecimal parse(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim()).setScale(6, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
