package org.jeecg.modules.study.error.constant;

import java.util.Arrays;
import java.util.List;

/**
 * 错因五维（TC-02-05：恰好 5 个选项）。
 *
 * <p>这是<b>枚举常量</b>，不是自由文本：错因必须是这五个之一，
 * 否则统计口径会漂。</p>
 *
 * @author jeecg-study
 */
public final class ErrorCause {

    /** 概念不清 */
    public static final String CONCEPT_UNCLEAR = "概念不清";

    /** 审题偏差 */
    public static final String READING_DEVIATION = "审题偏差";

    /** 计算失误 */
    public static final String CALCULATION_ERROR = "计算失误";

    /** 方法不会 */
    public static final String METHOD_UNKNOWN = "方法不会";

    /** 时间不够 */
    public static final String TIME_SHORTAGE = "时间不够";

    /** 五维全集（顺序固定） */
    public static final List<String> ALL = Arrays.asList(
            CONCEPT_UNCLEAR, READING_DEVIATION, CALCULATION_ERROR, METHOD_UNKNOWN, TIME_SHORTAGE);

    private ErrorCause() {
    }

    /**
     * 是否为合法错因。
     *
     * @param cause 错因
     * @return 合法返回 true
     */
    public static boolean isValid(String cause) {
        return cause != null && ALL.contains(cause.trim());
    }
}
