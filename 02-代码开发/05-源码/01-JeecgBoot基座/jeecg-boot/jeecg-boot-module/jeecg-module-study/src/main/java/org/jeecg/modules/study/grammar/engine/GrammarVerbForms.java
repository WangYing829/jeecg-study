package org.jeecg.modules.study.grammar.engine;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 英语动词形态工具（确定性规则，零 AI 依赖）。
 *
 * <p>只覆盖中考阶段高频动词；表外的动词用通用构词规则兜底，
 * 兜不出来就返回 {@code null}（<b>宁可不改，也不改错</b>）。</p>
 *
 * @author jeecg-study
 */
public final class GrammarVerbForms {

    /** 辅音字母集合（判断 y 结尾的复数 / 比较级） */
    private static final String CONSONANTS = "bcdfghjklmnpqrstvwxyz";

    /**
     * 「want to + Ving」里 Ving **一定错**的动词表。
     *
     * <p>刻意<b>不含</b> running / swimming / walking / shopping 这类
     * 本来就能跟在动词后面作伴随动作的词，避免误报。</p>
     */
    private static final Map<String, String> ING_TO_BASE;

    static {
        Map<String, String> map = new LinkedHashMap<>();
        String[][] pairs = {
                {"going", "go"}, {"making", "make"}, {"taking", "take"}, {"having", "have"},
                {"doing", "do"}, {"seeing", "see"}, {"writing", "write"}, {"playing", "play"},
                {"studying", "study"}, {"trying", "try"}, {"using", "use"}, {"buying", "buy"},
                {"coming", "come"}, {"getting", "get"}, {"giving", "give"}, {"living", "live"},
                {"meeting", "meet"}, {"opening", "open"}, {"speaking", "speak"}, {"starting", "start"},
                {"staying", "stay"}, {"thinking", "think"}, {"washing", "wash"}, {"wearing", "wear"},
                {"loving", "love"}, {"liking", "like"}, {"hoping", "hope"}, {"visiting", "visit"},
                {"asking", "ask"}, {"calling", "call"}, {"cleaning", "clean"}, {"cooking", "cook"},
                {"dancing", "dance"}, {"driving", "drive"}, {"enjoying", "enjoy"}, {"finishing", "finish"},
                {"following", "follow"}, {"painting", "paint"}, {"preparing", "prepare"},
                {"printing", "print"}, {"pulling", "pull"}, {"pushing", "push"}, {"sharing", "share"},
                {"swimming", "swim"}, {"testing", "test"}, {"turning", "turn"}, {"watching", "watch"},
                {"wishing", "wish"}, {"wondering", "wonder"}, {"worrying", "worry"}, {"being", "be"},
                {"eating", "eat"}, {"sitting", "sit"}, {"sleeping", "sleep"}, {"standing", "stand"},
                {"teaching", "teach"}, {"catching", "catch"}, {"bringing", "bring"}, {"feeling", "feel"},
                {"keeping", "keep"}, {"putting", "put"}, {"sending", "send"}, {"telling", "tell"},
                {"winning", "win"}, {"forgetting", "forget"}, {"beginning", "begin"}, {"building", "build"},
                {"choosing", "choose"}, {"drinking", "drink"}, {"falling", "fall"}, {"flying", "fly"},
                {"growing", "grow"}, {"throwing", "throw"}, {"drawing", "draw"}, {"singing", "sing"},
                {"translating", "translate"}, {"reading", "read"}, {"typing", "type"},
                {"raining", "rain"}, {"snowing", "snow"},
        };
        for (String[] pair : pairs) {
            map.put(pair[0], pair[1]);
        }
        ING_TO_BASE = Collections.unmodifiableMap(map);
    }

    /** 「want to + Ving」的可疑 Ving 形态正则片段 */
    private static final String ING_FORM = String.join("|", ING_TO_BASE.keySet());

    /** 第三人称单数里「不规则但常用」的动词 */
    private static final Map<String, String> IRREGULAR_BASE = Collections.singletonMap("has", "have");

    private GrammarVerbForms() {
    }

    /**
     * 可疑 Ving 形态的正则片段。
     *
     * @return 形如 {@code going|making|...} 的片段
     */
    public static String ingForm() {
        return ING_FORM;
    }

    /**
     * Ving 形态 → 动词原形。
     *
     * @param ing Ving 形态
     * @return 动词原形；判断不了返回 null
     */
    public static String base(String ing) {
        if (ing == null) {
            return null;
        }
        String word = ing.trim().toLowerCase(Locale.ROOT);
        if (word.isEmpty()) {
            return null;
        }
        String hit = ING_TO_BASE.get(word);
        if (hit != null) {
            return hit;
        }
        if (word.endsWith("ying") && word.length() > 5) {
            return word.substring(0, word.length() - 4) + "ie";
        }
        if (word.endsWith("ing") && word.length() > 5) {
            String stem = word.substring(0, word.length() - 3);
            int n = stem.length();
            if (n >= 2 && stem.charAt(n - 1) == stem.charAt(n - 2)) {
                return stem.substring(0, n - 1);
            }
            return stem;
        }
        return null;
    }

    /**
     * 第三人称单数形态 → 动词原形。
     *
     * @param verb 第三人称单数形态
     * @return 动词原形；判断不了返回 null
     */
    public static String thirdPersonToBase(String verb) {
        if (verb == null) {
            return null;
        }
        String word = verb.trim().toLowerCase(Locale.ROOT);
        if (word.isEmpty()) {
            return null;
        }
        String irregular = IRREGULAR_BASE.get(word);
        if (irregular != null) {
            return irregular;
        }
        if (word.endsWith("ies") && word.length() > 4) {
            return word.substring(0, word.length() - 3) + "y";
        }
        if (word.endsWith("sses") || word.endsWith("shes") || word.endsWith("ches")
                || word.endsWith("xes") || word.endsWith("zes") || word.endsWith("oes")) {
            return word.substring(0, word.length() - 2);
        }
        if (word.endsWith("s") && !word.endsWith("ss")) {
            return word.substring(0, word.length() - 1);
        }
        return null;
    }

    /**
     * 动词原形 → 第三人称单数形态。
     *
     * @param verb 动词原形
     * @return 第三人称单数形态
     */
    public static String thirdPerson(String verb) {
        if (verb == null) {
            return null;
        }
        String word = verb.trim().toLowerCase(Locale.ROOT);
        if (word.isEmpty()) {
            return null;
        }
        if ("have".equals(word)) {
            return "has";
        }
        if (word.endsWith("s") || word.endsWith("x") || word.endsWith("z")
                || word.endsWith("ch") || word.endsWith("sh")) {
            return word + "es";
        }
        // 以 o 结尾：辅音 + o 加 -es（go → goes、do → does），元音 + o 只加 -s（radio → radios）
        if (word.endsWith("o") && word.length() > 1
                && CONSONANTS.indexOf(word.charAt(word.length() - 2)) >= 0) {
            return word + "es";
        }
        if (word.endsWith("y") && word.length() > 1 && CONSONANTS.indexOf(word.charAt(word.length() - 2)) >= 0) {
            return word.substring(0, word.length() - 1) + "ies";
        }
        return word + "s";
    }

    /**
     * 名词单数 → 复数。
     *
     * @param noun 名词单数
     * @return 复数形态
     */
    public static String plural(String noun) {
        if (noun == null) {
            return null;
        }
        String word = noun.trim().toLowerCase(Locale.ROOT);
        if (word.isEmpty()) {
            return null;
        }
        if (word.endsWith("y") && word.length() > 1 && CONSONANTS.indexOf(word.charAt(word.length() - 2)) >= 0) {
            return word.substring(0, word.length() - 1) + "ies";
        }
        if (word.endsWith("s") || word.endsWith("x") || word.endsWith("ch") || word.endsWith("sh")) {
            return word + "es";
        }
        return word + "s";
    }

    /**
     * 常用可数名词（how many 后面必须用复数）。
     *
     * @return 名词列表
     */
    public static List<String> countableNouns() {
        return Arrays.asList("book", "apple", "student", "car", "day", "boy", "girl", "friend",
                "question", "problem", "way", "thing", "piece", "teacher", "parent", "class",
                "room", "chair", "bike", "bus", "train", "egg", "orange", "banana", "flower",
                "cat", "dog", "bird", "fish", "cake", "letter", "photo", "picture", "story",
                "word", "hour", "minute", "year", "month", "week", "country", "city");
    }
}
