package org.jeecg.modules.study.grammar.constant;

import java.util.Arrays;
import java.util.List;

/**
 * 词条与例句的关联类型（F-GRM-05）。
 *
 * <p>同一个词在语境库里的多条例句，靠这个字段区分「主例句」与「拓展用法」：
 * 主例句就是背单词域 {@code study_word.context_sentence} 那一批，
 * 拓展用法是同一个词在真题 / 原声素材里的不同说法。</p>
 *
 * @author jeecg-study
 */
public final class GrammarContextRelation {

    /** 主例句（与背单词域共用同一句） */
    public static final String MAIN = "MAIN";

    /** 拓展用法（同词在别的语境里的说法） */
    public static final String EXTEND = "EXTEND";

    /** 关联类型的固定顺序 */
    public static final List<String> ALL = Arrays.asList(MAIN, EXTEND);

    private GrammarContextRelation() {
    }

    /**
     * 关联类型的中文名。
     *
     * @param relation 关联类型
     * @return 中文名；未知类型返回空串
     */
    public static String nameOf(String relation) {
        if (relation == null) {
            return "";
        }
        switch (relation.trim().toUpperCase()) {
            case MAIN:
                return "主例句";
            case EXTEND:
                return "拓展用法";
            default:
                return "";
        }
    }

    /**
     * 是否为合法关联类型。
     *
     * @param relation 关联类型
     * @return 合法返回 true
     */
    public static boolean isValid(String relation) {
        return relation != null && ALL.contains(relation.trim().toUpperCase());
    }
}
