package org.jeecg.modules.study.judge.engine.strategy;

import org.springframework.stereotype.Component;

import org.jeecg.modules.study.common.enums.JudgeSource;
import org.jeecg.modules.study.judge.engine.JudgeRequest;
import org.jeecg.modules.study.judge.engine.JudgeResult;
import org.jeecg.modules.study.judge.engine.JudgeStrategy;

/**
 * 精确匹配判定：忽略首尾空白与大小写。
 *
 * <p>用于单词拼写、语法填空这类答案唯一确定题型。</p>
 *
 * @author jeecg-study
 */
@Component
public class ExactMatchJudgeStrategy implements JudgeStrategy {

    /** 题型标识 */
    public static final String CONTENT_TYPE = "EXACT";

    @Override
    public String contentType() {
        return CONTENT_TYPE;
    }

    @Override
    public JudgeResult judge(JudgeRequest request) {
        String expected = normalize(request.getExpectedAnswer());
        String actual = normalize(request.getActualAnswer());
        boolean correct = expected.equals(actual);

        JudgeResult result = new JudgeResult();
        result.setCorrect(correct);
        result.setJudgeSource(JudgeSource.RULE.getCode());
        result.setBasis(correct
                ? "与标准答案完全一致"
                : "与标准答案不一致：期望「" + request.getExpectedAnswer() + "」，实际「" + request.getActualAnswer() + "」");
        return result;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
