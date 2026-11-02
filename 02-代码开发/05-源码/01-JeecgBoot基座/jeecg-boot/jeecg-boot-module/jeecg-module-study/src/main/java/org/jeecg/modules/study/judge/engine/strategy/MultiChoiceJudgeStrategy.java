package org.jeecg.modules.study.judge.engine.strategy;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import org.jeecg.modules.study.common.enums.JudgeSource;
import org.jeecg.modules.study.judge.engine.JudgeRequest;
import org.jeecg.modules.study.judge.engine.JudgeResult;
import org.jeecg.modules.study.judge.engine.JudgeStrategy;

/**
 * 多选判定：选项集合完全一致才算对（少选、多选、错选都不算）。
 *
 * @author jeecg-study
 */
@Component
public class MultiChoiceJudgeStrategy implements JudgeStrategy {

    /** 题型标识 */
    public static final String CONTENT_TYPE = "MULTI_CHOICE";

    @Override
    public String contentType() {
        return CONTENT_TYPE;
    }

    @Override
    public JudgeResult judge(JudgeRequest request) {
        Set<String> expected = split(request.getExpectedOptions() == null
                ? request.getExpectedAnswer() : request.getExpectedOptions());
        Set<String> actual = split(request.getActualAnswer());
        boolean correct = expected.equals(actual);

        JudgeResult result = new JudgeResult();
        result.setCorrect(correct);
        result.setJudgeSource(JudgeSource.RULE.getCode());
        result.setBasis(correct
                ? "所选选项与标准答案一致"
                : "所选选项与标准答案不一致：期望「" + String.join(",", expected) + "」，实际「" + String.join(",", actual) + "」");
        return result;
    }

    private Set<String> split(String value) {
        Set<String> set = new LinkedHashSet<>();
        if (value == null || value.trim().isEmpty()) {
            return set;
        }
        Arrays.stream(value.split("[,，;；\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(set::add);
        return set;
    }
}
