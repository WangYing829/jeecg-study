package org.jeecg.modules.study.grammar.gameplay.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 玩法用到的文本工具（确定性，不含任何模型调用）。
 *
 * <p>变形闯关要判断「孩子写的句子有没有说出目标表达的意思」，
 * 口径固定为<b>实词重合度</b>：去掉虚词后比关键词，
 * 命中数达到目标实词数的一半（向上取整，至少 1 个）才算说到了点上。</p>
 *
 * @author jeecg-study
 */
public final class GrammarGameText {

    /** 单词切分：只保留英文字母与词内撇号 */
    private static final Pattern WORD = Pattern.compile("[A-Za-z]+(?:'[A-Za-z]+)?");

    /** 虚词表（不参与实词重合度） */
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "a", "an", "the", "to", "of", "in", "on", "at", "for", "with", "and", "or", "but",
            "i", "you", "he", "she", "it", "we", "they", "me", "him", "her", "us", "them", "my",
            "your", "his", "its", "our", "their", "this", "that", "these", "those", "is", "are",
            "am", "was", "were", "be", "been", "being", "do", "does", "did", "will", "would",
            "can", "could", "should", "shall", "may", "might", "there", "here", "not", "no"));

    private GrammarGameText() {
    }

    /**
     * 切出句子里的单词（小写）。
     *
     * @param text 句子
     * @return 单词列表
     */
    public static List<String> tokens(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        Matcher matcher = WORD.matcher(text);
        while (matcher.find()) {
            out.add(matcher.group().toLowerCase(Locale.ROOT));
        }
        return out;
    }

    /**
     * 切出句子里的实词（去掉虚词）。
     *
     * @param text 句子
     * @return 实词列表
     */
    public static List<String> contentTokens(String text) {
        List<String> out = new ArrayList<>();
        for (String token : tokens(text)) {
            if (!STOP_WORDS.contains(token)) {
                out.add(token);
            }
        }
        return out;
    }

    /**
     * 两个词表之间的重合个数（按出现次数算，重复词也计）。
     *
     * @param target 目标词表
     * @param actual 孩子的词表
     * @return 重合个数
     */
    public static int overlap(List<String> target, List<String> actual) {
        if (target == null || actual == null) {
            return 0;
        }
        int hit = 0;
        for (String token : target) {
            if (actual.contains(token)) {
                hit++;
            }
        }
        return hit;
    }

    /**
     * 至少要命中多少个实词才算说到了点上。
     *
     * @param contentCount 目标实词个数
     * @return 命中门槛（至少 1）
     */
    public static int requiredOverlap(int contentCount) {
        if (contentCount <= 0) {
            return 1;
        }
        return Math.max(1, (contentCount + 1) / 2);
    }

}
