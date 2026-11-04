package org.jeecg.modules.study.practice.service;

import org.jeecg.modules.study.practice.vo.ExplanationFeedbackVO;

/**
 * 解析申诉服务（R-02 / F-FAM-08）。
 *
 * <p><b>这是 AI 判对错的家长兜底通道</b>：孩子或家长对某个内容的讲解表示
 * 「我不同意」，服务端只<b>记录申诉</b>，不改判对错结论、不改分数（R-02：
 * AI 只做解释层，涉及分数必须有人工 / 家长兜底）。</p>
 *
 * <p><b>数据范围</b>：家长侧按 {@code childCode} 与关系表求交集，
 * 孩子侧只看自己；越权一律 4010。</p>
 *
 * @author jeecg-study
 */
public interface StudyExplanationFeedbackService {

    /**
     * 查询 / 提交解析申诉。
     *
     * @param contentId 内容业务ID
     * @param childCode 孩子业务ID，可空；为空时取当前身份唯一可见的孩子
     * @param disagree  是否表示不同意；{@code true} 时记录一条申诉，{@code false} 或不传时只查状态
     * @param reason    申诉理由
     * @return 当前申诉状态
     */
    ExplanationFeedbackVO feedback(String contentId, String childCode, Boolean disagree, String reason);
}
