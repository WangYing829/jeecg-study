package org.jeecg.modules.study.grammar.engine;

import java.util.List;

import org.jeecg.modules.study.grammar.vo.GrammarErrorVO;

/**
 * 语法错误检测器（确定性规则，零 AI 依赖）。
 *
 * @author jeecg-study
 */
public interface GrammarErrorDetector {

    /**
     * 检测句子里的语法错误。
     *
     * @param sentence 孩子写的句子
     * @return 检出的错误列表；没有错误返回空列表
     */
    List<GrammarErrorVO> detect(String sentence);
}
