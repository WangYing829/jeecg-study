package org.jeecg.modules.study.math.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 口述关键词抽取与命中判定（TC-04-08）。
 *
 * <p><b>规则口径（可测、可复现）</b>：</p>
 * <ol>
 *   <li>先把关系词（等于 / 乘以 / 除以 …）当分隔符切开；</li>
 *   <li>再按「非字母数字汉字」切词；</li>
 *   <li>去掉长度不足 2、过长、纯数字与虚词；</li>
 *   <li>保序去重，最多取 6 个。</li>
 * </ol>
 *
 * <p>例如「路程和 = 速度和 × 相遇时间」→ 关键词
 * {@code [路程和, 速度和, 相遇时间]}。</p>
 *
 * @author jeecg-study
 */
public final class NarrativeKeywordExtractor {

    /** 关键词最短长度 */
    private static final int MIN_LENGTH = 2;

    /** 关键词最长长度 */
    private static final int MAX_LENGTH = 12;

    /** 关键词最多取几个 */
    private static final int MAX_KEYWORDS = 6;

    /** 关系词：当作分隔符处理 */
    private static final List<String> RELATION_WORDS = Arrays.asList(
            "等于", "乘以", "除以", "加上", "减去", "大于", "小于");

    /** 虚词：不作为关键词 */
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "那么", "所以", "因为", "如果", "可以", "需要", "我们", "他们", "它们",
            "这个", "那个", "什么", "怎么", "以及", "并且", "然后", "最后", "首先",
            "其次", "其中", "一种", "一个", "两个", "三个", "一些", "这些", "那些"));

    private NarrativeKeywordExtractor() {
    }

    /**
     * 从关键关系里抽关键词。
     *
     * @param keyRelation 母题第 3 段：关键关系
     * @return 关键词列表（保序去重）
     */
    public static List<String> extract(String keyRelation) {
        List<String> result = new ArrayList<>();
        if (keyRelation == null || keyRelation.trim().isEmpty()) {
            return result;
        }
        String text = keyRelation;
        for (String word : RELATION_WORDS) {
            text = text.replace(word, " ");
        }
        for (String token : split(text)) {
            String trimmed = token.trim();
            if (trimmed.length() < MIN_LENGTH || trimmed.length() > MAX_LENGTH) {
                continue;
            }
            if (STOP_WORDS.contains(trimmed) || isNumeric(trimmed)) {
                continue;
            }
            if (!result.contains(trimmed)) {
                result.add(trimmed);
            }
            if (result.size() >= MAX_KEYWORDS) {
                break;
            }
        }
        return result;
    }

    /**
     * 命中的关键词。
     *
     * @param keywords  关键词列表
     * @param narrative 口述内容
     * @return 命中的关键词
     */
    public static List<String> hit(List<String> keywords, String narrative) {
        List<String> result = new ArrayList<>();
        if (keywords == null || narrative == null) {
            return result;
        }
        String text = narrative.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (keyword == null || keyword.trim().isEmpty()) {
                continue;
            }
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                result.add(keyword);
            }
        }
        return result;
    }

    /**
     * 缺失的关键词。
     *
     * @param keywords  关键词列表
     * @param narrative 口述内容
     * @return 缺失的关键词
     */
    public static List<String> missing(List<String> keywords, String narrative) {
        List<String> result = new ArrayList<>();
        if (keywords == null) {
            return result;
        }
        List<String> hit = hit(keywords, narrative);
        for (String keyword : keywords) {
            if (!hit.contains(keyword)) {
                result.add(keyword);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------ 内部

    private static List<String> split(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                current.append(c);
            } else if (current.length() > 0) {
                tokens.add(current.toString());
                current.setLength(0);
            }
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private static boolean isNumeric(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isDigit(c) && c != '.') {
                return false;
            }
        }
        return true;
    }
}
