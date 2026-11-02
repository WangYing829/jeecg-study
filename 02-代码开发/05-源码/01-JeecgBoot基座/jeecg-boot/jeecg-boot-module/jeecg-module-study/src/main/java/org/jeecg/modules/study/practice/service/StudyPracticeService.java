package org.jeecg.modules.study.practice.service;

import org.jeecg.modules.study.practice.vo.AnswerCommand;
import org.jeecg.modules.study.practice.vo.AnswerResultVO;
import org.jeecg.modules.study.practice.vo.ExplanationVO;
import org.jeecg.modules.study.practice.vo.PracticeQuestionVO;
import org.jeecg.modules.study.practice.vo.PracticeSessionVO;
import org.jeecg.modules.study.practice.vo.SessionSummaryVO;

/**
 * 练习：取题 → 作答 → 判定 → 解析（T-V0-09 / T-V0-10）。
 *
 * <p><b>红线</b>：</p>
 * <ul>
 *   <li>R01-1：取题响应<b>不含</b>答案与解析字段；</li>
 *   <li>R01-2：未作答取解析返回 4003；</li>
 *   <li>R02-3：判对错响应必带 {@code judgeSource}，取值只有 rule / rule+model。</li>
 * </ul>
 *
 * @author jeecg-study
 */
public interface StudyPracticeService {

    /**
     * 取下一题。
     *
     * @param moduleType 练习类型：word / math / grammar / writing / wrong
     * @return 题目（不含答案与解析）
     */
    PracticeQuestionVO nextQuestion(String moduleType);

    /**
     * 提交作答：判定 → 采集错因 → 答错入错题池 → 落作答记录。
     *
     * @param command 作答入参
     * @return 判定结果
     */
    AnswerResultVO submitAnswer(AnswerCommand command);

    /**
     * 取解析（必须在作答之后）。
     *
     * @param contentId 内容业务ID
     * @param sessionId 会话 ID，可空
     * @return 解析
     */
    ExplanationVO explanation(String contentId, String sessionId);

    /**
     * 生成练习会话。
     *
     * @param moduleType 练习类型
     * @return 会话
     */
    PracticeSessionVO createSession(String moduleType);

    /**
     * 结束会话并汇总。
     *
     * @param sessionId 会话 ID
     * @return 会话汇总
     */
    SessionSummaryVO finishSession(String sessionId);
}
