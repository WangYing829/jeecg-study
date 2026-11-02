package org.jeecg.modules.study.judge.service;

import java.util.List;

import org.jeecg.modules.study.judge.engine.JudgeRequest;
import org.jeecg.modules.study.judge.engine.JudgeResult;

/**
 * 判定引擎：按题型分发到注册的 {@link JudgeStrategy}。
 *
 * <p>没有注册策略的题型返回业务错误 <b>4005</b>（判定策略缺失），
 * 不会静默放过，更不会退化到「让模型判对错」。</p>
 *
 * @author jeecg-study
 */
public interface JudgeEngine {

    /**
     * 判定。
     *
     * @param request 判定入参
     * @return 判定结果
     */
    JudgeResult judge(JudgeRequest request);

    /**
     * 已注册的题型列表（用于自检与文档生成）。
     *
     * @return 题型标识列表
     */
    List<String> registeredContentTypes();
}
