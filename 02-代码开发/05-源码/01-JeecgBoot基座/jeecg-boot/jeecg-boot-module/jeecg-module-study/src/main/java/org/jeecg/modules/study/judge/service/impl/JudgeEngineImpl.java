package org.jeecg.modules.study.judge.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.study.common.constant.StudyErrorCode;
import org.jeecg.modules.study.judge.service.JudgeEngine;
import org.jeecg.modules.study.judge.engine.JudgeRequest;
import org.jeecg.modules.study.judge.engine.JudgeResult;
import org.jeecg.modules.study.judge.engine.JudgeStrategy;

/**
 * 判定引擎实现：按题型分发。
 *
 * <p><b>本类及其依赖的 {@link JudgeStrategy} 实现不允许出现 AI 客户端依赖</b>，
 * 这是红线断言 R02-1 的落点。</p>
 *
 * @author jeecg-study
 */
@Service
@RequiredArgsConstructor
public class JudgeEngineImpl implements JudgeEngine {

    private final List<JudgeStrategy> strategies;

    @Override
    public JudgeResult judge(JudgeRequest request) {
        if (request == null || request.getContentType() == null) {
            throw new JeecgBootException("判定题型不能为空", StudyErrorCode.JUDGE_STRATEGY_MISSING);
        }
        JudgeStrategy strategy = resolve(request.getContentType());
        if (strategy == null) {
            throw new JeecgBootException("该题型未注册判定策略：" + request.getContentType(),
                    StudyErrorCode.JUDGE_STRATEGY_MISSING);
        }
        JudgeResult result = strategy.judge(request);
        if (result.getBasis() == null || result.getBasis().trim().isEmpty()) {
            // 判定依据必须人可读且非空（TC-08-11）
            result.setBasis("按「" + strategy.contentType() + "」规则判定");
        }
        return result;
    }

    @Override
    public List<String> registeredContentTypes() {
        Map<String, JudgeStrategy> map = new LinkedHashMap<>();
        for (JudgeStrategy strategy : strategies) {
            map.put(strategy.contentType(), strategy);
        }
        return new ArrayList<>(map.keySet());
    }

    private JudgeStrategy resolve(String contentType) {
        for (JudgeStrategy strategy : strategies) {
            if (strategy.contentType().equalsIgnoreCase(contentType)) {
                return strategy;
            }
        }
        return null;
    }
}
