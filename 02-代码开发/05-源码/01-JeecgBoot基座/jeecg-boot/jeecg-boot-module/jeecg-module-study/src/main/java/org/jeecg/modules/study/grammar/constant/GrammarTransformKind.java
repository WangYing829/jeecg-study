package org.jeecg.modules.study.grammar.constant;

import java.util.Arrays;
import java.util.List;

/**
 * 句子变形的五种形态（F-GRM-01 / TC-05-01）。
 *
 * @author jeecg-study
 */
public final class GrammarTransformKind {

    /** 直译版 */
    public static final String LITERAL = "LITERAL";

    /** 地道口语版 */
    public static final String COLLOQUIAL = "COLLOQUIAL";

    /** 书面版 */
    public static final String WRITTEN = "WRITTEN";

    /** 考试安全版 */
    public static final String EXAM_SAFE = "EXAM_SAFE";

    /** 错误示范版 */
    public static final String WRONG = "WRONG";

    /** 错误示范版的固定前缀（TC-05-02） */
    public static final String WRONG_MARK = "✗ 不要这样写：";

    /** 五态的固定输出顺序 */
    public static final List<String> ALL = Arrays.asList(LITERAL, COLLOQUIAL, WRITTEN, EXAM_SAFE, WRONG);

    private GrammarTransformKind() {
    }

    /**
     * 形态的中文名。
     *
     * @param kind 形态标识
     * @return 中文名
     */
    public static String nameOf(String kind) {
        if (kind == null) {
            return "";
        }
        switch (kind.trim().toUpperCase()) {
            case LITERAL:
                return "直译版";
            case COLLOQUIAL:
                return "地道口语版";
            case WRITTEN:
                return "书面版";
            case EXAM_SAFE:
                return "考试安全版";
            case WRONG:
                return "错误示范版";
            default:
                return "";
        }
    }

    /**
     * 形态的默认使用场合说明。
     *
     * @param kind 形态标识
     * @return 场合说明
     */
    public static String usageOf(String kind) {
        if (kind == null) {
            return "";
        }
        switch (kind.trim().toUpperCase()) {
            case LITERAL:
                return "意思对得过去，但母语者听起来有点生硬";
            case COLLOQUIAL:
                return "日常口语里最自然的说法";
            case WRITTEN:
                return "书面表达更稳妥";
            case EXAM_SAFE:
                return "口语考试与写作里的稳妥选择";
            case WRONG:
                return "错误示范，只用来对照，别这样写";
            default:
                return "";
        }
    }
}
