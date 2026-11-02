package org.jeecg.modules.study.grammar.constant;

import java.util.Arrays;
import java.util.List;

/**
 * 中考英语题型（TC-05-05：单选 / 完形 / 语法填空 / 书面表达）。
 *
 * @author jeecg-study
 */
public final class GrammarExamType {

    /** 单选 */
    public static final String SINGLE = "单选";

    /** 完形 */
    public static final String CLOZE = "完形";

    /** 语法填空 */
    public static final String FILL_BLANK = "语法填空";

    /** 书面表达 */
    public static final String WRITING = "书面表达";

    /** 四种题型全集 */
    public static final List<String> ALL = Arrays.asList(SINGLE, CLOZE, FILL_BLANK, WRITING);

    private GrammarExamType() {
    }

    /**
     * 是否为合法题型。
     *
     * @param examType 题型
     * @return 合法返回 true
     */
    public static boolean isValid(String examType) {
        return examType != null && ALL.contains(examType.trim());
    }
}
