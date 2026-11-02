package org.jeecg.modules.study.word.service;

import java.util.List;

import org.jeecg.modules.study.word.vo.WordDueVO;
import org.jeecg.modules.study.word.vo.WordFeedbackOptionVO;
import org.jeecg.modules.study.word.vo.WordFeedbackRequest;
import org.jeecg.modules.study.word.vo.WordFeedbackVO;
import org.jeecg.modules.study.word.vo.WordKnownTestRequest;
import org.jeecg.modules.study.word.vo.WordKnownTestVO;
import org.jeecg.modules.study.word.vo.WordListVO;
import org.jeecg.modules.study.word.vo.WordPracticeRequest;
import org.jeecg.modules.study.word.vo.WordPracticeResultVO;
import org.jeecg.modules.study.word.vo.WordPracticeVO;
import org.jeecg.modules.study.word.vo.WordStatsVO;
import org.jeecg.modules.study.word.vo.WordUnitCompleteVO;
import org.jeecg.modules.study.word.vo.WordUnitRequest;
import org.jeecg.modules.study.word.vo.WordUnitVO;

/**
 * 背单词服务（F-WRD-01…F-WRD-06 / TC-03-01…TC-03-11）。
 *
 * <p>两条线：课内（教材单元词表）与课外（高频词线）共用同一套队列与算法内核，
 * 只是取数口径不同。</p>
 *
 * <p><b>红线约束</b>：</p>
 * <ul>
 *   <li>R-01：取词 / 词表 / 下一词响应均<b>不含拼写</b>与答案字段；</li>
 *   <li>R-02：判定走 {@code JudgeEngine}，判定来源只有 rule / rule+model；</li>
 *   <li>R-04：响应与文案不含排名 / 百分位 / 家庭内对照措辞。</li>
 * </ul>
 *
 * @author jeecg-study
 */
public interface StudyWordService {

    /**
     * 课内单元列表（教材版本 + 单元）。
     *
     * @param lineType          线别 IN / OUT
     * @param textbookVersionId 教材版本ID；课内必填
     * @return 单元清单
     */
    List<WordUnitVO> listUnits(String lineType, String textbookVersionId);

    /**
     * 词表分页。
     *
     * @param lineType          线别 IN / OUT
     * @param textbookVersionId 教材版本ID，可空
     * @param unitCode          单元编码，可空
     * @param pageNo            页码，从 1 起
     * @param pageSize          每页条数
     * @return 词表
     */
    WordListVO listWords(String lineType, String textbookVersionId, String unitCode,
                         Integer pageNo, Integer pageSize);

    /**
     * 取下一个要背的词（含无图回忆环节与词→句→篇三层）。
     *
     * @param lineType          线别 IN / OUT
     * @param textbookVersionId 教材版本ID，可空
     * @param unitCode          单元编码，可空
     * @return 取词结果
     */
    WordPracticeVO nextWord(String lineType, String textbookVersionId, String unitCode);

    /**
     * 拼写作答判定。
     *
     * @param request 入参
     * @return 判定结果
     */
    WordPracticeResultVO practice(WordPracticeRequest request);

    /**
     * 四档反馈（认识 / 模糊 / 忘记 / 熟知）。
     *
     * @param request 入参
     * @return 反馈结果
     */
    WordFeedbackVO feedback(WordFeedbackRequest request);

    /**
     * 反馈档位选项（恰好 4 档，不是对/错）。
     *
     * @return 档位选项
     */
    List<WordFeedbackOptionVO> feedbackOptions();

    /**
     * 熟词测试：把已掌握的词标为熟词并从队列剔除。
     *
     * @param request 入参
     * @return 熟词测试结果
     */
    WordKnownTestVO knownTest(WordKnownTestRequest request);

    /**
     * 到期复习词。
     *
     * @param lineType  线别 IN / OUT
     * @param childCode 孩子业务ID；孩子端可不传（取会话身份），家长端必传
     * @return 到期复习词
     */
    WordDueVO dueWords(String lineType, String childCode);

    /**
     * 背诵统计。
     *
     * @param lineType  线别 IN / OUT
     * @param childCode 孩子业务ID；孩子端可不传（取会话身份），家长端必传
     * @return 统计
     */
    WordStatsVO stats(String lineType, String childCode);

    /**
     * 进入课内单元；当日复习欠账未清空时拒绝进入（PRD §8.3 坑二）。
     *
     * @param request 入参
     * @return 单元词表
     */
    WordListVO enterUnit(WordUnitRequest request);

    /**
     * 完成一个单元（闯关奖励 + 连续天数）。
     *
     * @param request 入参
     * @return 闯关结果
     */
    WordUnitCompleteVO completeUnit(WordUnitRequest request);
}
