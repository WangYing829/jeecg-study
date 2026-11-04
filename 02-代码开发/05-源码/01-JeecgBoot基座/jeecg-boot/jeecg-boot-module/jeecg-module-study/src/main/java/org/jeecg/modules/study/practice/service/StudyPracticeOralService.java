package org.jeecg.modules.study.practice.service;

import org.jeecg.modules.study.practice.vo.PracticeOralCommand;
import org.jeecg.modules.study.practice.vo.PracticeOralResultVO;

/**
 * 口述关键关系服务（F-MTH-03）。
 *
 * <p><b>判据在母题域</b>：口述讲的是「这道题里哪几个量之间有固定关系」，
 * 判据就是母题第 3 段（关键关系）。因此本服务只负责「找到这道题对应的母题」，
 * 判定本身复用母题域的口述判定，保证两处口径一致。</p>
 *
 * <p><b>不给答案</b>：口述不全时只给命中 / 缺失的关键词与一条苏格拉底式追问（R-01）。</p>
 *
 * @author jeecg-study
 */
public interface StudyPracticeOralService {

    /**
     * 提交口述内容，返回命中 / 缺失关键词与追问。
     *
     * @param contentId 内容业务ID
     * @param command   口述入参
     * @return 口述反馈
     */
    PracticeOralResultVO submitOral(String contentId, PracticeOralCommand command);
}
