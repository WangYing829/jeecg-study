package org.jeecg.modules.study.writing.service;

import org.jeecg.modules.study.writing.vo.WritingDraftBriefVO;
import org.jeecg.modules.study.writing.vo.WritingFeedbackListVO;
import org.jeecg.modules.study.writing.vo.WritingFeedbackRequest;
import org.jeecg.modules.study.writing.vo.WritingFeedbackVO;

/**
 * 家长端写作域服务（F-PAR-02 / F-WRT-06）。
 *
 * <p>家长端只有<b>一句话反馈</b>这一个写操作；反馈内容不超过 100 字，
 * 超出直接拒绝且不落库（TC-06-08）。</p>
 *
 * <p><b>家长之间互不可见</b>：所有查询都以
 * {@code scopeService.visibleChildIds()}（关系表求交集后的孩子集合）为条件，
 * 越权一律 4010（R-04）。</p>
 *
 * @author jeecg-study
 */
public interface StudyWritingParentService {

    /**
     * 提交一句话反馈。
     *
     * @param request 入参
     * @return 反馈
     */
    WritingFeedbackVO submitFeedback(WritingFeedbackRequest request);

    /**
     * 我可见孩子的反馈清单。
     *
     * @param childCode 孩子业务ID；为空表示当前家长可见的全部孩子
     * @return 反馈清单
     */
    WritingFeedbackListVO listFeedbacks(String childCode);

    /**
     * 我可见孩子的最近初稿清单（供家长挑反馈对象）。
     *
     * <p>只给元信息（业务ID / 题目 / 提交时间 / 孩子业务ID），<b>不给作文正文</b>。</p>
     *
     * @param childCode 孩子业务ID；为空表示当前家长可见的全部孩子
     * @return 初稿摘要清单
     */
    java.util.List<WritingDraftBriefVO> listDrafts(String childCode);
}
