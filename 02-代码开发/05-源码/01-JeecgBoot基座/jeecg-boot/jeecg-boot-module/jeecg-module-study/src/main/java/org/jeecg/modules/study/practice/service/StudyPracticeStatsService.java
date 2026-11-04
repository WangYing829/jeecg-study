package org.jeecg.modules.study.practice.service;

import org.jeecg.modules.study.practice.vo.PracticeRecordPageVO;
import org.jeecg.modules.study.practice.vo.PracticeStreakVO;
import org.jeecg.modules.study.practice.vo.PracticeTodayStatsVO;

/**
 * 作答记录的只读统计服务（F-PAR-01 / F-FAM-07 / F-WRD-02）。
 *
 * <p><b>数据范围</b>：家长侧传 {@code childCode}，服务端与关系表求交集；
 * 孩子侧不传就是自己。越权一律 4010。</p>
 *
 * <p><b>内容边界（R-04 / R-02）</b>：只给客观计数与趋势，
 * 响应里没有名次类字段，也没有答案、解析与讲解字段。</p>
 *
 * @author jeecg-study
 */
public interface StudyPracticeStatsService {

    /**
     * 作答记录分页。
     *
     * @param childCode 孩子业务ID，可空；为空时取当前身份唯一可见的孩子
     * @param pageNo    页码，从 1 开始
     * @param pageSize  每页条数
     * @param month     统计月份 yyyyMM，可空；为空取当月
     * @return 分页结果
     */
    PracticeRecordPageVO records(String childCode, Integer pageNo, Integer pageSize, String month);

    /**
     * 今日练习统计。
     *
     * @param childCode 孩子业务ID，可空；为空时取当前身份唯一可见的孩子
     * @return 今日统计
     */
    PracticeTodayStatsVO todayStats(String childCode);

    /**
     * 连续学习天数。
     *
     * @param childCode 孩子业务ID，可空；为空时取当前身份唯一可见的孩子
     * @return 连续天数
     */
    PracticeStreakVO streak(String childCode);
}
