package org.jeecg.modules.study.judge.engine;

/**
 * 判定策略。
 *
 * <p><b>实现方不允许依赖任何 AI 客户端</b>——R-02 的硬约束（红线断言 R02-1 用 ArchUnit 校验）。
 * 规则判定必须是确定性的：同样的输入必须给出同样的输出。</p>
 *
 * @author jeecg-study
 */
public interface JudgeStrategy {

    /**
     * 本策略负责的题型标识。
     *
     * @return 题型标识
     */
    String contentType();

    /**
     * 判定。
     *
     * @param request 判定入参
     * @return 判定结果
     */
    JudgeResult judge(JudgeRequest request);
}
