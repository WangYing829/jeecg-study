package org.jeecg.modules.study.error.service;

import java.util.List;

import org.jeecg.modules.study.error.vo.ErrorPoolItemVO;
import org.jeecg.modules.study.error.vo.ReviewQueueItemVO;
import org.jeecg.modules.study.error.vo.StudyErrorReportCommand;

/**
 * 统一错题池 + 复习排期（T-V0-06 / T-V0-07）。
 *
 * <p><b>全局只有一个错题池</b>：六个学习模块的错误都汇聚到这里，
 * 各模块不得自建错题列表（TC-02-12）。</p>
 *
 * <p>复习排期规则（TC-02-07…TC-02-10）：</p>
 * <ul>
 *   <li>答错 → 下次复习 = <b>当天</b>，{@code correctStreak} 归零；</li>
 *   <li>答对 → 下次复习依次为 <b>+1 / +3 / +7</b> 天；</li>
 *   <li>连续答对 <b>3 次</b>才移出队列（{@code archived}）。</li>
 * </ul>
 *
 * @author jeecg-study
 */
public interface StudyErrorPoolService {

    /**
     * 错题入池（幂等：同一 childId + contentType + contentId 只入一次）。
     *
     * @param command 入池入参
     * @return 错题业务ID（ERR-xxxxxx）
     */
    String report(StudyErrorReportCommand command);

    /**
     * 复习完成，推进排期。
     *
     * @param errorCode  错题业务ID
     * @param correct    本次是否答对
     * @param errorCause 错因（答错时必填，缺失返回 4001）
     */
    void completeReview(String errorCode, boolean correct, String errorCause);

    /**
     * 某个孩子的错题列表。
     *
     * @param childId 孩子 ID
     * @return 错题列表
     */
    List<ErrorPoolItemVO> listByChild(String childId);

    /**
     * 某个孩子今天的复习队列，按排序权重降序。
     *
     * @param childId 孩子 ID
     * @return 队列
     */
    List<ReviewQueueItemVO> todayQueue(String childId);

    /**
     * 错因分布统计（按五维分组计数）。
     *
     * @param childId 孩子 ID
     * @return 错因 → 条数
     */
    java.util.Map<String, Integer> errorCauseStatistics(String childId);
}
