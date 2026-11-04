package org.jeecg.modules.study.error.service.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

import org.jeecg.modules.study.error.service.StudyErrorManageService;
import org.jeecg.modules.study.error.service.StudyReviewBatchService;
import org.jeecg.modules.study.error.vo.ReviewBatchCompleteCommand;
import org.jeecg.modules.study.error.vo.ReviewBatchCompleteVO;
import org.jeecg.modules.study.error.vo.ReviewBatchItemVO;
import org.jeecg.modules.study.error.vo.ReviewCompleteCommand;
import org.jeecg.modules.study.error.vo.ReviewCompleteVO;

/**
 * 复习队列批量完成服务实现（F-BASE-05）。
 *
 * <p><b>逐项独立</b>：每一项都通过 {@link StudyErrorManageService#completeReview}
 * 走一遍完整流程（该方法带事务，代理调用才会生效），
 * 单项异常只记进该项结果，不影响其它项。</p>
 *
 * @author jeecg-study
 */
@Service
@RequiredArgsConstructor
public class StudyReviewBatchServiceImpl implements StudyReviewBatchService {

    private final StudyErrorManageService errorManageService;

    @Override
    public ReviewBatchCompleteVO completeBatch(ReviewBatchCompleteCommand command) {
        ReviewBatchCompleteVO vo = new ReviewBatchCompleteVO();
        List<ReviewCompleteCommand> items = command == null ? null : command.getItems();
        if (items == null || items.isEmpty()) {
            return vo;
        }
        vo.setTotal(items.size());

        List<ReviewBatchItemVO> results = new ArrayList<>();
        int succeeded = 0;
        int failed = 0;
        for (ReviewCompleteCommand item : items) {
            ReviewBatchItemVO result = new ReviewBatchItemVO();
            result.setErrorCode(item == null ? null : item.getErrorCode());
            try {
                ReviewCompleteVO single = errorManageService.completeReview(item);
                result.setSuccess(Boolean.TRUE);
                result.setCounted(single.getCounted());
                result.setCorrectStreak(single.getCorrectStreak());
                result.setMasteryState(single.getMasteryState());
                result.setArchived(single.getArchived());
                result.setNextReviewAt(single.getNextReviewAt());
                result.setMessage(single.getMessage());
                succeeded = succeeded + 1;
            } catch (Exception e) {
                result.setSuccess(Boolean.FALSE);
                result.setCounted(Boolean.FALSE);
                result.setMessage(e.getMessage() == null ? "这一项没有完成" : e.getMessage());
                failed = failed + 1;
            }
            results.add(result);
        }
        vo.setSucceeded(succeeded);
        vo.setFailed(failed);
        vo.setItems(results);
        return vo;
    }
}
