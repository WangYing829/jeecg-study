package org.jeecg.modules.study.grammar.gameplay.service;

import org.jeecg.modules.study.grammar.gameplay.vo.GrammarDetectiveJudgeRequest;
import org.jeecg.modules.study.grammar.gameplay.vo.GrammarDetectiveJudgeVO;
import org.jeecg.modules.study.grammar.gameplay.vo.GrammarDetectiveRoundVO;
import org.jeecg.modules.study.grammar.gameplay.vo.GrammarGameCollectionResultVO;
import org.jeecg.modules.study.grammar.gameplay.vo.GrammarGameProgressVO;
import org.jeecg.modules.study.grammar.gameplay.vo.GrammarTransformJudgeRequest;
import org.jeecg.modules.study.grammar.gameplay.vo.GrammarTransformJudgeVO;
import org.jeecg.modules.study.grammar.gameplay.vo.GrammarTransformRoundVO;

/**
 * 语法玩法服务（F-GRM-04 趣味玩法）。
 *
 * <p>三个玩法：句子侦探、语法变形闯关、我的错句收藏馆。
 * 判定全部走确定性规则，{@code judgeSource} 恒为 {@code rule}（R-02）。</p>
 *
 * @author jeecg-study
 */
public interface GrammarGameService {

    /**
     * 取一关句子侦探（四句话，只给句子不给答案）。
     *
     * @return 回合
     */
    GrammarDetectiveRoundVO detectiveNext();

    /**
     * 判定句子侦探：孩子指出错的是第几句。
     *
     * @param request 判定入参
     * @return 判定结果
     */
    GrammarDetectiveJudgeVO detectiveJudge(GrammarDetectiveJudgeRequest request);

    /**
     * 取一关语法变形闯关（只给中文意思，不给目标表达）。
     *
     * @return 回合
     */
    GrammarTransformRoundVO transformNext();

    /**
     * 判定语法变形闯关：孩子写的句子对不对。
     *
     * @param request 判定入参
     * @return 判定结果
     */
    GrammarTransformJudgeVO transformJudge(GrammarTransformJudgeRequest request);

    /**
     * 我的错句收藏馆（统一错题池里自己的错句）。
     *
     * @param errorCause 错因；为空不过滤
     * @return 错句列表
     */
    GrammarGameCollectionResultVO collection(String errorCause);

    /**
     * 闯关进度（只跟自己比）。
     *
     * @return 各玩法的进度
     */
    GrammarGameProgressVO progress();

}
