package org.jeecg.modules.study.grammar.gameplay.constant;

import java.util.Arrays;
import java.util.List;

/**
 * 语法玩法类型（F-GRM-04 趣味玩法）。
 *
 * <p>目前两个：句子侦探、语法变形闯关；
 * 第三个玩法「我的错句收藏馆」走统一错题池，不是独立题型。</p>
 *
 * @author jeecg-study
 */
public final class GrammarGameType {

    /** 句子侦探：找出错的那一句 */
    public static final String DETECTIVE = "DETECTIVE";

    /** 语法变形闯关：把中文意思写成正确的英文 */
    public static final String TRANSFORM = "TRANSFORM";

    /** 玩法的固定顺序 */
    public static final List<String> ALL = Arrays.asList(DETECTIVE, TRANSFORM);

    private GrammarGameType() {
    }

    /**
     * 玩法类型的中文名。
     *
     * @param type 玩法类型
     * @return 中文名；未知类型返回空串
     */
    public static String nameOf(String type) {
        if (type == null) {
            return "";
        }
        switch (type.trim().toUpperCase()) {
            case DETECTIVE:
                return "句子侦探";
            case TRANSFORM:
                return "语法变形闯关";
            default:
                return "";
        }
    }

    /**
     * 是否为合法玩法类型。
     *
     * @param type 玩法类型
     * @return 合法返回 true
     */
    public static boolean isValid(String type) {
        return type != null && ALL.contains(type.trim().toUpperCase());
    }
}
