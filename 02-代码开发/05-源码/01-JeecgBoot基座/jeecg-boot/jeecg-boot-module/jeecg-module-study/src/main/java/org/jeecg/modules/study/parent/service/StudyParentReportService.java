package org.jeecg.modules.study.parent.service;

import org.jeecg.modules.study.parent.vo.ParentErrorReportVO;
import org.jeecg.modules.study.parent.vo.ParentOverviewVO;
import org.jeecg.modules.study.parent.vo.ParentWeeklyVO;

/**
 * 家长端只读报表服务（F-PAR-01）。
 *
 * <p><b>只读边界</b>：本服务只查询、只返回趋势，没有任何写入入口。</p>
 *
 * <p><b>内容边界</b>：响应里不含答案、解析、讲解与教学建议字段，
 * 也不含任何排名类字段（R-02 / R-04）。</p>
 *
 * <p><b>数据范围</b>：只查 {@code study_parent_child_rel} 关联的孩子，
 * 越权一律 4010。</p>
 *
 * @author jeecg-study
 */
public interface StudyParentReportService {

    /**
     * 家长端总览：用眼、连续天数、掌握度分布、错因 Top3、薄弱考点、写作趋势。
     *
     * @param childCode 孩子业务ID；为空时取当前身份唯一可见的孩子
     * @return 总览
     */
    ParentOverviewVO overview(String childCode);

    /**
     * 家长端周报（近 7 天逐日）。
     *
     * @param childCode 孩子业务ID；为空时取当前身份唯一可见的孩子
     * @return 周报
     */
    ParentWeeklyVO weekly(String childCode);

    /**
     * 家长端错因分布与薄弱考点。
     *
     * @param childCode 孩子业务ID；为空时取当前身份唯一可见的孩子
     * @return 错因分布
     */
    ParentErrorReportVO errors(String childCode);
}
