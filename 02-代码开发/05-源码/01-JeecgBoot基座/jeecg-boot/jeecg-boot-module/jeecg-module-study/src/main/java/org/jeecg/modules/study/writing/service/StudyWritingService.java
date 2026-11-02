package org.jeecg.modules.study.writing.service;

import org.jeecg.modules.study.writing.vo.WritingDissectionListVO;
import org.jeecg.modules.study.writing.vo.WritingDissectionVO;
import org.jeecg.modules.study.writing.vo.WritingDraftRequest;
import org.jeecg.modules.study.writing.vo.WritingDraftResultVO;
import org.jeecg.modules.study.writing.vo.WritingOutlineRequest;
import org.jeecg.modules.study.writing.vo.WritingOutlineResultVO;

/**
 * 语文写作域服务（V4）。
 *
 * <p>覆盖 F-WRT-01…06：佳作拆解库（六层 + 每层提示题）、对照式批改（≤3 条、无分数无等级）、
 * 三步训练流顺序强制、病句入统一错题池。</p>
 *
 * <p><b>数据范围</b>：孩子端身份来自会话（{@code scopeService.ownChildId()}），
 * 不接受前端传来的 childId；家长反馈走 {@link StudyWritingParentService}，
 * 孩子的任何接口都不会读到家长反馈。</p>
 *
 * @author jeecg-study
 */
public interface StudyWritingService {

    /**
     * 佳作拆解库列表（每个拆解含六层）。
     *
     * @param genre     题材；为空不过滤
     * @param stageCode 学段；为空不过滤
     * @return 拆解库列表
     */
    WritingDissectionListVO listDissections(String genre, String stageCode);

    /**
     * 佳作拆解详情（六层，按题目钩子 → 结尾回扣的顺序）。
     *
     * @param code 拆解集编码或某一层的业务ID
     * @return 拆解详情
     */
    WritingDissectionVO getDissection(String code);

    /**
     * 提交提纲（审题 → 提纲）。
     *
     * @param request 入参
     * @return 提纲结果；通过时 nextStep = DRAFT
     */
    WritingOutlineResultVO submitOutline(WritingOutlineRequest request);

    /**
     * 提交初稿（提纲 → 初稿）。提纲未通过返回 4002，且不落库。
     *
     * @param request 入参
     * @return 对照式批改结果
     */
    WritingDraftResultVO submitDraft(WritingDraftRequest request);
}
