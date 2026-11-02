package org.jeecg.modules.study.grammar.service;

import org.jeecg.modules.study.grammar.vo.GrammarCollectionResultVO;
import org.jeecg.modules.study.grammar.vo.GrammarDetectRequest;
import org.jeecg.modules.study.grammar.vo.GrammarDetectResultVO;
import org.jeecg.modules.study.grammar.vo.GrammarErrorDetailRequest;
import org.jeecg.modules.study.grammar.vo.GrammarErrorFeedbackVO;
import org.jeecg.modules.study.grammar.vo.GrammarPointListVO;
import org.jeecg.modules.study.grammar.vo.GrammarPointVO;
import org.jeecg.modules.study.grammar.vo.GrammarTransformRequest;
import org.jeecg.modules.study.grammar.vo.GrammarTransformResultVO;

/**
 * 英语语法域服务（V3）。
 *
 * <p>覆盖 F-GRM-01…03 / F-GRM-06：句子变形器、错误即入口链路、考纲题型标注、错句入池。</p>
 *
 * @author jeecg-study
 */
public interface StudyGrammarService {

    /**
     * 语法点列表（按中考题型过滤）。
     *
     * @param examType 中考题型；为空不过滤
     * @return 语法点列表
     */
    GrammarPointListVO listPoints(String examType);

    /**
     * 语法点详情（三件套）。
     *
     * @param grammarCode 语法点业务ID
     * @return 语法点
     */
    GrammarPointVO getPoint(String grammarCode);

    /**
     * 句子变形器。
     *
     * @param request 入参
     * @return 五态变形结果
     */
    GrammarTransformResultVO transform(GrammarTransformRequest request);

    /**
     * 错误检测（检出错误的同时把错句送进统一错题池）。
     *
     * @param request 入参
     * @return 检测结果
     */
    GrammarDetectResultVO detect(GrammarDetectRequest request);

    /**
     * 点开某个错误时的三件套讲解。
     *
     * @param request 入参
     * @return 讲解
     */
    GrammarErrorFeedbackVO errorDetail(GrammarErrorDetailRequest request);

    /**
     * 我的错句收藏馆。
     *
     * @param errorCause 错因筛选；为空不过滤
     * @return 收藏馆
     */
    GrammarCollectionResultVO collection(String errorCause);
}
