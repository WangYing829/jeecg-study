package org.jeecg.modules.study.mastery.service;

import java.util.List;

import org.jeecg.modules.study.mastery.vo.MasteryPointVO;
import org.jeecg.modules.study.mastery.vo.MasteryWeakPointVO;

/**
 * 考点掌握度服务（F-PAR-01 / F-FAM-07）。
 *
 * <p><b>数据范围</b>：家长侧按 {@code childCode} 与关系表求交集，孩子侧只看自己；
 * 越权一律 4010。</p>
 *
 * <p><b>内容边界（R-04 / R-02）</b>：只给客观状态与条数，
 * 响应里没有名次类字段，也没有答案、解析与讲解字段。</p>
 *
 * @author jeecg-study
 */
public interface StudyMasteryService {

    /**
     * 薄弱考点清单（按错题条数从多到少）。
     *
     * @param childCode 孩子业务ID，可空；为空时取当前身份唯一可见的孩子
     * @param limit     最多返回条数
     * @return 薄弱考点清单
     */
    List<MasteryWeakPointVO> weakPoints(String childCode, int limit);

    /**
     * 某个考点的掌握度。
     *
     * @param childCode 孩子业务ID，可空；为空时取当前身份唯一可见的孩子
     * @param pointCode 考点业务ID
     * @return 考点掌握度
     */
    MasteryPointVO point(String childCode, String pointCode);
}
