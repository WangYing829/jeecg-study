package org.jeecg.modules.study.error.service;

import org.jeecg.modules.study.error.vo.ReviewBatchCompleteCommand;
import org.jeecg.modules.study.error.vo.ReviewBatchCompleteVO;

/**
 * 复习队列批量完成服务（F-BASE-05）。
 *
 * <p><b>逐项独立</b>：每一项单独走「完成一项复习」的完整流程
 * （排期推进 + 幂等判定 + 变式校验），一项失败不影响其它项 ——
 * 批量完成的语义本来就是「这一批里哪些做完了」。</p>
 *
 * <p><b>为什么单独一个服务</b>：要拿到「每项各自一个事务」，
 * 就必须通过 Spring 代理调用单项完成方法；同 bean 内部自调用会绕过代理、
 * 事务失效。拆成独立服务后，依赖方向是
 * 批量服务 → 错题池服务，没有循环依赖。</p>
 *
 * @author jeecg-study
 */
public interface StudyReviewBatchService {

    /**
     * 批量完成复习项。
     *
     * @param command 批量入参
     * @return 逐项结果
     */
    ReviewBatchCompleteVO completeBatch(ReviewBatchCompleteCommand command);
}
