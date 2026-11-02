package org.jeecg.modules.study.sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.jeecg.modules.study.redline.RedLineScanner;

/**
 * **语法点种子脚本与确定性规则目录的一致性断言**。
 *
 * <p><b>真实踩过的坑（2026-10-31，P2 浏览器取证 TC-08-20 截图时发现）</b>：
 * {@code study_grammar_point} 一行都没有——种子只灌了考点 / 母题 / 题目 / 单词，
 * 语境库与玩法题那批脚本只灌了 4 张新表。结果是：</p>
 * <ul>
 *   <li>学习端「英语语法」页直接空态；</li>
 *   <li>「句子变形闯关」拿不到五态语料；</li>
 *   <li>更隐蔽的是：确定性规则目录
 *       （{@code GrammarErrorRuleCatalog}）里 14 条规则<b>各自指向一个 GRP 编码</b>，
 *       「错误即入口」要按这个编码回查语法点的三件套——编码不存在，
 *       「点错处弹出最小讲解」这一环就是<b>空的</b>。</li>
 * </ul>
 *
 * <p>这类缺口<b>不会让任何单测变红</b>：规则目录是代码、语法点是数据，两边没有约束。
 * 本类把约束补上：<b>规则目录引用的每一个 GRP 编码，种子脚本必须都有对应的 INSERT</b>。</p>
 *
 * @author jeecg-study
 */
class StudyGrammarSeedTest {

    /** 语法点种子脚本（相对 SQL 真源目录） */
    private static final String SEED_SCRIPT = "18-seed-grammar-point.sql";

    /** 确定性规则目录（相对 study 主源码根） */
    private static final String RULE_CATALOG = "grammar/engine/GrammarErrorRuleCatalog.java";

    /** 规则目录里一条规则声明的语法点编码 */
    private static final Pattern RULE_GRAMMAR_CODE = Pattern.compile(
            "new\\s+GrammarErrorRule\\s*\\(\\s*\"([^\"]+)\"\\s*,\\s*\"(GRP-\\d+)\"");

    /** 种子脚本里一行语法点 INSERT 的起始标记 */
    private static final String SEED_ROW_MARKER = "('SEED-GRP-";

    /** 语料条目里的语法点编码 */
    private static final Pattern ENTRY_GRAMMAR_CODE = Pattern.compile("\"grammarCode\"\\s*:\\s*\"(GRP-\\d+)\"");

    /** 一行语法点 INSERT 的业务编码 */
    private static final Pattern ROW_CODE = Pattern.compile("'(GRP-\\d+)'");

    /** 语料条目里的中文意思 */
    private static final Pattern ENTRY_MEANING = Pattern.compile("\"meaning\"\\s*:\\s*\"([^\"]+)\"");

    /** 语法点挂的考点 */
    private static final Pattern SEED_KNOWLEDGE_POINT = Pattern.compile(
            "'(KPT-[A-Z]{2}-\\d+)',\\s*\\n?\\s*'(?:\\[|NULL)");

    // ================================================================= 扫描有效性

    @Test
    @DisplayName("扫描有效性：种子脚本与规则目录都必须存在且非空")
    void sourceFilesMustExist() {
        Path script = sqlScriptsDir().resolve(SEED_SCRIPT);
        Path catalog = RedLineScanner.studyMainRoot().resolve(RULE_CATALOG);

        assertThat(Files.isRegularFile(script))
                .as("语法点种子脚本必须存在（SQL 真源目录）：" + SEED_SCRIPT).isTrue();
        assertThat(Files.isRegularFile(catalog))
                .as("确定性规则目录必须存在：" + RULE_CATALOG).isTrue();
        assertThat(RedLineScanner.read(script).trim())
                .as("扫描有效性：种子脚本内容非空").isNotEmpty();
        assertThat(ruleGrammarCodes())
                .as("扫描有效性：必须能从规则目录里解析出语法点编码")
                .isNotEmpty();
    }

    // ================================================================= 断言 1：规则目录引用的编码必须有种子

    @Test
    @DisplayName("规则目录引用的每个 GRP 编码，种子脚本都必须有对应的语法点")
    void everyRuleGrammarCodeMustBeSeeded() {
        Set<String> required = ruleGrammarCodes();
        List<String> rows = seedRows();

        Set<String> seeded = new LinkedHashSet<>();
        for (String row : rows) {
            seeded.add(rowCode(row));
        }

        List<String> missing = new ArrayList<>();
        for (String code : required) {
            if (!seeded.contains(code)) {
                missing.add(code);
            }
        }

        assertThat(missing)
                .as("规则目录（GrammarErrorRuleCatalog）里每条规则都指向一个语法点编码，"
                        + "「错误即入口」要按这个编码回查三件套——编码没种子，"
                        + "点错处就弹不出最小讲解（F-GRM-02 静默失效）")
                .isEmpty();
    }

    // ================================================================= 断言 2：语法点必须挂考点 + 标题型

    @Test
    @DisplayName("每个语法点都必须挂考点树考点并标注中考题型（F-GRM-03 考纲锚定）")
    void everyGrammarPointMustAnchorToKnowledgePoint() {
        List<String> rows = seedRows();

        List<String> problems = new ArrayList<>();
        for (String row : rows) {
            String code = rowCode(row);
            if (!row.contains("'KPT-")) {
                problems.add(code + " → 没有挂考点（knowledge_point_code）");
            }
            if (!row.contains("'语法填空'") && !row.contains("'单选'")
                    && !row.contains("'完形'") && !row.contains("'书面表达'")) {
                problems.add(code + " → 没有标注中考题型（单选 / 完形 / 语法填空 / 书面表达）");
            }
        }

        assertThat(rows.size()).as("扫描有效性：至少要解析出若干条语法点 INSERT").isPositive();
        assertThat(problems)
                .as("F-GRM-03：每个语法点都要挂到考点树并标中考题型，否则「只讲该地区该学段要考的」落不了地")
                .isEmpty();
    }

    // ================================================================= 断言 3：语料条目的语法点编码必须与所在语法点一致

    @Test
    @DisplayName("变形语料条目里的 grammarCode 必须与所在语法点一致")
    void corpusEntriesMustBelongToTheirGrammarPoint() {
        List<String> rows = seedRows();

        List<String> problems = new ArrayList<>();
        int entries = 0;
        for (String row : rows) {
            String owner = rowCode(row);
            Matcher entryMatcher = ENTRY_GRAMMAR_CODE.matcher(row);
            while (entryMatcher.find()) {
                entries++;
                if (!owner.equals(entryMatcher.group(1))) {
                    problems.add(owner + " → 语料条目指向了 " + entryMatcher.group(1));
                }
            }
        }

        assertThat(entries).as("扫描有效性：至少要解析出若干条变形语料条目").isPositive();
        assertThat(problems)
                .as("变形语料条目必须归属到自己的语法点，否则「点语法点看五态说法」会对不上")
                .isEmpty();
    }

    // ================================================================= 断言 4：玩法题必须回填语法点

    @Test
    @DisplayName("26 条玩法题（14 侦探 + 12 变形）必须回填 grammar_code")
    void everyGameQuestionMustBeAnchored() {
        String script = RedLineScanner.read(sqlScriptsDir().resolve(SEED_SCRIPT));

        List<Integer> indexes = new ArrayList<>();
        for (int i = 1; i <= 14; i++) {
            indexes.add(i);
        }
        for (int i = 101; i <= 112; i++) {
            indexes.add(i);
        }

        List<String> missing = new ArrayList<>();
        for (Integer index : indexes) {
            String code = String.format("GAM-%06d", index);
            if (!script.contains("'" + code + "'")) {
                missing.add(code);
            }
        }

        assertThat(missing)
                .as("句子侦探题与变形闯关题都要挂上语法点："
                        + "挂不上时「这题练的是哪个语法点」是空的，F-GRM-02 / F-GRM-03 都断链")
                .isEmpty();
    }

    // ================================================================= 断言 5：语料意思不能为空

    @Test
    @DisplayName("变形语料的每个条目都必须有中文意思（服务端按意思匹配语料）")
    void corpusEntriesMustHaveMeaning() {
        List<String> rows = seedRows();

        List<String> problems = new ArrayList<>();
        int entries = 0;
        for (String row : rows) {
            Matcher meaningMatcher = ENTRY_MEANING.matcher(row);
            while (meaningMatcher.find()) {
                entries++;
                if (meaningMatcher.group(1).trim().isEmpty()) {
                    problems.add(rowCode(row) + " → 有一条语料的 meaning 是空的");
                }
            }
        }

        assertThat(entries).as("扫描有效性：至少要解析出若干条变形语料条目").isPositive();
        assertThat(problems)
                .as("服务端是按「中文意思」归一化后匹配语料的，meaning 为空则永远匹配不上")
                .isEmpty();
    }

    // ------------------------------------------------------------------ 工具

    /**
     * 把种子脚本里每一条语法点 INSERT 切成一个整块。
     *
     * <p>一条 INSERT 是<b>跨多行</b>的（transform_variants 的 JSON 很长），
     * 按行正则只能抓到第一行，所以这里按 {@code ('SEED-GRP-} 标记切块。</p>
     *
     * @return 每条 INSERT 的文本块
     */
    private static List<String> seedRows() {
        List<String> out = new ArrayList<>();
        String script = RedLineScanner.read(sqlScriptsDir().resolve(SEED_SCRIPT));
        String[] chunks = script.split(Pattern.quote(SEED_ROW_MARKER));
        for (int i = 1; i < chunks.length; i++) {
            out.add(chunks[i]);
        }
        return out;
    }

    /** 取一条 INSERT 块里的语法点业务编码 */
    private static String rowCode(String row) {
        Matcher matcher = ROW_CODE.matcher(row);
        return matcher.find() ? matcher.group(1) : "(未知)";
    }

    /** SQL 真源目录 */
    private static Path sqlScriptsDir() {
        return RedLineScanner.repoRoot()
                .resolve("02-代码开发")
                .resolve("02-数据库")
                .resolve("03-脚本");
    }

    /** 规则目录引用的全部语法点编码 */
    private static Set<String> ruleGrammarCodes() {
        Set<String> out = new LinkedHashSet<>();
        Path catalog = RedLineScanner.studyMainRoot().resolve(RULE_CATALOG);
        if (!Files.isRegularFile(catalog)) {
            return out;
        }
        Matcher matcher = RULE_GRAMMAR_CODE.matcher(RedLineScanner.read(catalog));
        while (matcher.find()) {
            out.add(matcher.group(2));
        }
        return out;
    }
}
