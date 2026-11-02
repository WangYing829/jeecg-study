package org.jeecg.modules.study.grammar.constant;

import java.util.Arrays;
import java.util.List;

/**
 * 例句语境类型（F-GRM-05 语境库复用）。
 *
 * <p>PRD 的要求是「同一个词在课文、真题、原声素材中的不同用法」，
 * 所以语境类型固定为三类；顺序即展示顺序（课文 → 真题 → 原声素材）。</p>
 *
 * @author jeecg-study
 */
public final class GrammarContextType {

    /** 课文语境 */
    public static final String TEXTBOOK = "TEXTBOOK";

    /** 真题语境 */
    public static final String EXAM = "EXAM";

    /** 原声素材语境 */
    public static final String ORIGINAL = "ORIGINAL";

    /** 三类语境的固定顺序 */
    public static final List<String> ALL = Arrays.asList(TEXTBOOK, EXAM, ORIGINAL);

    private GrammarContextType() {
    }

    /**
     * 语境类型的中文名。
     *
     * @param type 语境类型
     * @return 中文名；未知类型返回空串
     */
    public static String nameOf(String type) {
        if (type == null) {
            return "";
        }
        switch (type.trim().toUpperCase()) {
            case TEXTBOOK:
                return "课文";
            case EXAM:
                return "真题";
            case ORIGINAL:
                return "原声素材";
            default:
                return "";
        }
    }

    /**
     * 是否为合法语境类型。
     *
     * @param type 语境类型
     * @return 合法返回 true
     */
    public static boolean isValid(String type) {
        return type != null && ALL.contains(type.trim().toUpperCase());
    }
}
