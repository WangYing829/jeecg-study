package org.jeecg.modules.study.practice.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;

import lombok.RequiredArgsConstructor;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.study.practice.entity.StudyExplanationFeedback;
import org.jeecg.modules.study.practice.mapper.StudyExplanationFeedbackMapper;
import org.jeecg.modules.study.practice.service.StudyExplanationFeedbackService;
import org.jeecg.modules.study.practice.vo.ExplanationFeedbackVO;
import org.jeecg.modules.study.scope.service.ChildScopeService;

/**
 * 解析申诉服务实现（R-02 / F-FAM-08）。
 *
 * <p><b>隔离口径</b>：孩子由 {@code childCode} 解析，解析前先过
 * {@link ChildScopeService#resolveChildId(String)}（关系表求交集），
 * 越权一律 4010（不用 404，避免资源探测）。</p>
 *
 * <p><b>家长之间互不可见</b>：申诉记录带提交人；读的时候只读本人提交的那条，
 * 因此同一家庭里家长 A 不会看到家长 B 的申诉。</p>
 *
 * <p><b>只记录，不改判</b>：申诉不会改动判对错结论，也不会改动分数（R-02）。</p>
 *
 * @author jeecg-study
 */
@Service
@RequiredArgsConstructor
public class StudyExplanationFeedbackServiceImpl implements StudyExplanationFeedbackService {

    /** 处理状态：待处理 */
    private static final String STATUS_PENDING = "PENDING";

    /** 处理状态：从未提交 */
    private static final String STATUS_NONE = "NONE";

    /** 内容类型：题目 */
    private static final String CONTENT_TYPE_QUESTION = "QUESTION";

    private final ChildScopeService scopeService;
    private final StudyExplanationFeedbackMapper feedbackMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExplanationFeedbackVO feedback(String contentId, String childCode, Boolean disagree, String reason) {
        String code = contentId == null ? "" : contentId.trim();
        if (code.isEmpty()) {
            throw new JeecgBootException("内容业务ID不能为空", 400);
        }
        String childId = scopeService.resolveChildId(childCode);
        String submitter = scopeService.currentUserId();

        if (Boolean.TRUE.equals(disagree)) {
            save(childId, code, submitter, reason);
        }
        return buildVO(childId, code, submitter);
    }

    // ------------------------------------------------------------------ 内部

    /**
     * 记录申诉（幂等：同一孩子 + 同一内容只留一条，重复提交更新理由与时间）。
     */
    private void save(String childId, String contentId, String submitter, String reason) {
        StudyExplanationFeedback existed = feedbackMapper.selectByChildAndContent(childId, contentId);
        if (existed == null) {
            StudyExplanationFeedback feedback = new StudyExplanationFeedback();
            feedback.setId(IdWorker.get32UUID());
            feedback.setChildId(childId);
            feedback.setContentId(contentId);
            feedback.setContentType(CONTENT_TYPE_QUESTION);
            feedback.setDisagree(1);
            feedback.setReason(trim(reason));
            feedback.setStatus(STATUS_PENDING);
            feedback.setSubmitterUserId(submitter);
            feedback.setDelFlag(0);
            feedbackMapper.insert(feedback);
            return;
        }
        existed.setDisagree(1);
        existed.setReason(trim(reason));
        existed.setStatus(STATUS_PENDING);
        existed.setSubmitterUserId(submitter);
        feedbackMapper.updateById(existed);
    }

    private ExplanationFeedbackVO buildVO(String childId, String contentId, String submitter) {
        StudyExplanationFeedback feedback = feedbackMapper.selectByChildAndContent(childId, contentId);
        ExplanationFeedbackVO vo = new ExplanationFeedbackVO();
        vo.setContentId(contentId);
        if (feedback == null) {
            vo.setDisagreed(Boolean.FALSE);
            vo.setFeedbackCount(0);
            vo.setLatestStatus(STATUS_NONE);
            return vo;
        }
        vo.setDisagreed(feedback.getDisagree() != null && feedback.getDisagree() == 1);
        vo.setLatestReason(feedback.getReason());
        vo.setLatestStatus(feedback.getStatus() == null ? STATUS_NONE : feedback.getStatus());
        vo.setLatestFeedbackTime(feedback.getCreateTime());
        Integer count = feedbackMapper.countBySubmitter(childId, contentId, submitter);
        vo.setFeedbackCount(count == null ? 0 : count);
        return vo;
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
