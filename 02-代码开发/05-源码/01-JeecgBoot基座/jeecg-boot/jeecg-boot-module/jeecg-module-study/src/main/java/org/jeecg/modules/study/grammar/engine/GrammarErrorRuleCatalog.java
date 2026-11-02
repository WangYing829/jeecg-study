package org.jeecg.modules.study.grammar.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 语法错误规则库（确定性，零 AI 依赖）。
 *
 * <p>每条规则都带「最小讲解 + 同类例句 + 反例」三件套，
 * 供「错误即入口」链路直接使用（F-GRM-02）。</p>
 *
 * @author jeecg-study
 */
public final class GrammarErrorRuleCatalog {

    /** 第三人称单数后面接动词时，可能漏掉 -s 的常见动词 */
    private static final String THIRD_PERSON_VERBS =
            "go|come|do|have|like|need|want|know|think|take|make|look|watch|work|play|study|read|write|"
                    + "listen|speak|talk|help|ask|answer|clean|cook|dance|drive|enjoy|finish|follow|learn|"
                    + "plan|visit|wait|call|open|start|stay|walk|wash|wear|see|miss|live|love|hope|try|use|"
                    + "win|meet|bring|buy|catch|teach|feel|keep|let|put|send|sit|sleep|stand|tell|jump|"
                    + "run|sing|draw|paint|test|turn|pull|push|share|print|prepare|wish|worry|wonder|"
                    + "swim|ride|climb";

    /** 比较级里不能用 more 的形容词 */
    private static final String COMPARATIVE_ADJECTIVES =
            "better|easier|harder|taller|shorter|faster|slower|bigger|smaller|longer|higher|lower|"
                    + "stronger|weaker|richer|poorer|happier|earlier|later|newer|older|colder|warmer|"
                    + "hotter|cheaper|safer|cleaner|clearer|thicker|thinner|nicer|busier";

    /** 不可数名词误加 -s 的形态 */
    private static final String UNCOUNTABLE_PLURALS =
            "informations|advices|peoples|knowledges|homeworks|furnitures|equipments";

    private static final List<GrammarErrorRule> RULES = build();

    private GrammarErrorRuleCatalog() {
    }

    /**
     * 全部规则。
     *
     * @return 规则列表（不可变）
     */
    public static List<GrammarErrorRule> all() {
        return RULES;
    }

    /**
     * 按规则ID 查规则。
     *
     * @param ruleId 规则ID
     * @return 规则；不存在返回 null
     */
    public static GrammarErrorRule byId(String ruleId) {
        if (ruleId == null) {
            return null;
        }
        for (GrammarErrorRule rule : RULES) {
            if (ruleId.equals(rule.getRuleId())) {
                return rule;
            }
        }
        return null;
    }

    private static List<GrammarErrorRule> build() {
        List<GrammarErrorRule> rules = new ArrayList<>();

        rules.add(new GrammarErrorRule("GR-ERR-01", "GRP-0031",
                Pattern.compile("(?i)\\b(?:want|wants|wanted)\\s+to\\s+(" + GrammarVerbForms.ingForm() + ")\\b"),
                GrammarErrorRuleCatalog::fixWantToIng,
                "want to do sth 里 want 后面跟动词原形，不能加 -ing",
                "I want to go home.",
                "I want going home."));

        rules.add(new GrammarErrorRule("GR-ERR-02", "GRP-0031",
                Pattern.compile("(?i)\\b(?:am|is|are)\\s+(?:want|need)\\s+to\\b"),
                GrammarErrorRuleCatalog::fixBeWantTo,
                "want / need to do sth 本身就是一个整体，前面不能再加 be 动词",
                "I want to go home.",
                "I am wanting to go home."));

        rules.add(new GrammarErrorRule("GR-ERR-03", "GRP-0018",
                Pattern.compile("(?i)\\b(?:does|did)\\s+(\\w+es|\\w+s)\\b"),
                GrammarErrorRuleCatalog::fixAuxiliaryVerb,
                "does / did 后面用动词原形，主语人称已经在助动词上了",
                "Does he go to school every day?",
                "Does he goes to school every day?"));

        rules.add(new GrammarErrorRule("GR-ERR-04", "GRP-0018",
                Pattern.compile("(?i)\\b(?:can|could|will|would|shall|should|must|may|might)\\s+(\\w+es|\\w+s)\\b"),
                GrammarErrorRuleCatalog::fixAuxiliaryVerb,
                "情态动词后面用动词原形",
                "He can swim very fast.",
                "He can swims very fast."));

        rules.add(new GrammarErrorRule("GR-ERR-05", "GRP-0007",
                Pattern.compile("(?i)\\b(?:he|she|it)\\s+(" + THIRD_PERSON_VERBS + ")\\b"),
                GrammarErrorRuleCatalog::fixThirdPerson,
                "第三人称单数（he / she / it）后面的动词要加 -s 或 -es",
                "He goes to school every day.",
                "He go to school every day."));

        rules.add(new GrammarErrorRule("GR-ERR-06", "GRP-0012",
                Pattern.compile("(?i)\\b(a)\\s+([aeiou][a-z]{2,})\\b"),
                GrammarErrorRuleCatalog::fixArticleBefore,
                "元音音素开头的单词前面用 an",
                "I saw an apple this morning.",
                "I saw a apple this morning."));

        rules.add(new GrammarErrorRule("GR-ERR-07", "GRP-0012",
                Pattern.compile("(?i)\\b(an)\\s+(?!hour|honest|honor|honour|heir)([bcdfgjklmnpqrstvwxyz][a-z]+)\\b"),
                GrammarErrorRuleCatalog::fixArticleBefore,
                "辅音音素开头的单词前面用 a",
                "I read a book last night.",
                "I read an book last night."));

        rules.add(new GrammarErrorRule("GR-ERR-08", "GRP-0025",
                Pattern.compile("(?i)\\b(goed)\\b"),
                (matched, sentence) -> "went",
                "go 的过去式是 went，不规则动词要单独记",
                "We went to the park yesterday.",
                "We goed to the park yesterday."));

        rules.add(new GrammarErrorRule("GR-ERR-09", "GRP-0021",
                Pattern.compile("(?i)\\b(" + UNCOUNTABLE_PLURALS + ")\\b"),
                (matched, sentence) -> matched.toLowerCase(Locale.ROOT).endsWith("s")
                        ? matched.substring(0, matched.length() - 1) : matched,
                "information / advice / people 这类词不能加 -s",
                "I need some information about the test.",
                "I need some informations about the test."));

        rules.add(new GrammarErrorRule("GR-ERR-10", "GRP-0009",
                Pattern.compile("(?i)\\b(more)\\s+(" + COMPARATIVE_ADJECTIVES + ")\\b"),
                (matched, sentence) -> matched.replaceFirst("(?i)^more\\s+", ""),
                "比较级不能用 more 和 -er 同时出现",
                "This question is easier than that one.",
                "This question is more easier than that one."));

        rules.add(new GrammarErrorRule("GR-ERR-11", "GRP-0014",
                Pattern.compile("(?i)\\b(how\\s+many)\\s+(book|apple|student|car|day|boy|girl|friend|"
                        + "question|problem|way|thing|piece|teacher|parent|class|room|chair|bike|bus|train|"
                        + "egg|orange|banana|flower|cat|dog|bird|fish|cake|letter|photo|picture|story|"
                        + "word|hour|minute|year|month|week|country|city)\\b"),
                (matched, sentence) -> {
                    String[] parts = matched.split("\\s+");
                    if (parts.length < 3) {
                        return null;
                    }
                    String plural = GrammarVerbForms.plural(parts[parts.length - 1]);
                    return plural == null ? null : matched.substring(0, matched.length()
                            - parts[parts.length - 1].length()) + plural;
                },
                "how many 后面接可数名词的复数形式",
                "How many books do you have?",
                "How many book do you have?"));

        rules.add(new GrammarErrorRule("GR-ERR-12", "GRP-0016",
                Pattern.compile("(?i)\\b(discuss)\\s+(about)\\b"),
                (matched, sentence) -> matched.replaceFirst("(?i)\\s+about$", ""),
                "discuss 后面直接跟宾语，不再加 about",
                "Let's discuss the problem tomorrow.",
                "Let's discuss about the problem tomorrow."));

        rules.add(new GrammarErrorRule("GR-ERR-13", "GRP-0028",
                Pattern.compile("(?i)\\b(very)\\s+(like|love|enjoy|want|need|hate|miss)\\b"),
                (matched, sentence) -> matched.replaceFirst("(?i)^very\\b", "really"),
                "very 不能直接修饰动词，改用 really 或者 very much",
                "I really like English.",
                "I very like English."));

        rules.add(new GrammarErrorRule("GR-ERR-14", "GRP-0024",
                Pattern.compile("(?i)\\b(there\\s+is)\\s+(many|several)\\b"),
                (matched, sentence) -> matched.replaceFirst("(?i)^there\\s+is\\b", "there are"),
                "many / several 后面跟复数，用 there are",
                "There are many students in the classroom.",
                "There is many students in the classroom."));

        return Collections.unmodifiableList(rules);
    }

    // ------------------------------------------------------------------ 修正器

    /**
     * want to + Ving → want to + 动词原形。
     */
    private static String fixWantToIng(String matched, String sentence) {
        int index = matched.lastIndexOf(' ');
        if (index < 0) {
            return null;
        }
        String verb = matched.substring(index + 1);
        String base = GrammarVerbForms.base(verb);
        return base == null ? null : matched.substring(0, index + 1) + base;
    }

    /**
     * be + want/need to → want/need to（去掉 be 动词，并把 -ing 还原成原形）。
     */
    private static String fixBeWantTo(String matched, String sentence) {
        String rest = matched.replaceFirst("(?i)^(?:am|is|are)\\s+", "");
        String[] parts = rest.split("\\s+");
        if (parts.length > 0 && parts[0].length() > 0) {
            String base = GrammarVerbForms.base(parts[0]);
            if (base != null) {
                parts[0] = base;
            }
            rest = String.join(" ", parts);
        }
        return rest;
    }

    /**
     * 助动词 / 情态动词 + 第三人称单数 → 原形。
     */
    private static String fixAuxiliaryVerb(String matched, String sentence) {
        int index = matched.lastIndexOf(' ');
        if (index < 0) {
            return null;
        }
        String verb = matched.substring(index + 1);
        String base = GrammarVerbForms.thirdPersonToBase(verb);
        return base == null ? null : matched.substring(0, index + 1) + base;
    }

    /**
     * 第三人称单数主语 + 动词原形 → 动词第三人称单数。
     */
    private static String fixThirdPerson(String matched, String sentence) {
        int index = matched.lastIndexOf(' ');
        if (index < 0) {
            return null;
        }
        String verb = matched.substring(index + 1);
        String third = GrammarVerbForms.thirdPerson(verb);
        return third == null ? null : matched.substring(0, index + 1) + third;
    }

    /**
     * 冠词 a / an 用错 → 互换。
     */
    private static String fixArticleBefore(String matched, String sentence) {
        String[] parts = matched.split("\\s+");
        if (parts.length < 2) {
            return null;
        }
        String article = parts[0];
        String replacement = "an".equalsIgnoreCase(article) ? "a" : "an";
        if (article.length() > 0 && Character.isUpperCase(article.charAt(0))) {
            replacement = Character.toUpperCase(replacement.charAt(0)) + replacement.substring(1);
        }
        return replacement + matched.substring(article.length());
    }
}
