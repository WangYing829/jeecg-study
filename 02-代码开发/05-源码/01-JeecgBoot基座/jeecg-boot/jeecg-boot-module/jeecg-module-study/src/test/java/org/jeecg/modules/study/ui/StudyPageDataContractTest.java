package org.jeecg.modules.study.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.jeecg.modules.study.redline.RedLineScanner;

/**
 * **学习端「取数口径」的跨层契约断言**。
 *
 * <p><b>真实踩过的坑（2026-10-31，P2 浏览器取证 TC-08-20 截图时发现）</b>：
 * 学习端两个页面都停在空态，接口却是好的：</p>
 * <ul>
 *   <li><b>背单词页</b>：{@code GET /word/practice/next} 的 {@code lineType} 虽是可选参数，
 *       但为空时服务端直接按「线别只能是 IN（课内）或 OUT（课外）」返回 400；
 *       前端又是<b>静默</b>请求（{@code ...SILENT}），错误被吞掉 → 页面显示空态，
 *       <b>一个字都背不了</b>。</li>
 *   <li><b>英语语法页</b>：后端 {@code GrammarPointListVO} 的列表字段叫 {@code items}，
 *       前端却按 {@code list} 取 → 即使库里真有语法点，页面照样是空的。</li>
 * </ul>
 *
 * <p>这两类问题<b>静态检查看不出来、单测也不会红</b>：接口通、接口返回 200 / 400、
 * 页面不报错，只是「没有内容」。只有真正把页面渲染出来才会暴露。</p>
 *
 * <p>本类把契约落成源码级断言：</p>
 * <ol>
 *   <li>背单词页必须<b>显式</b>带上线别（课内线优先，固定 IN）；</li>
 *   <li>语法点列表前端读的字段名，必须与后端 VO 的字段名<b>逐字一致</b>
 *       （从 VO 源码里解析出来，不写死）；</li>
 *   <li>学习端所有页面的取数都不得读一个「后端根本没有」的字段名。</li>
 * </ol>
 *
 * @author jeecg-study
 */
class StudyPageDataContractTest {

    /** 背单词页（相对前端 src） */
    private static final String WORD_PAGE = "views/study/word/index.vue";

    /** 英语语法页（相对前端 src） */
    private static final String GRAMMAR_PAGE = "views/study/grammar/index.vue";

    /** 学习端 API 封装（相对前端 src） */
    private static final String API_FILE = "views/study/api.ts";

    /** 后端语法点列表 VO（相对 study 主源码根） */
    private static final String GRAMMAR_LIST_VO = "grammar/vo/GrammarPointListVO.java";

    /** 语法点列表接口的前端声明 */
    private static final Pattern GRAMMAR_POINTS_DECL = Pattern.compile(
            "fetchGrammarPoints[\\s\\S]{0,200}?defHttp\\.get<\\{([^}]*)\\}>");

    /** 取词接口的调用点 */
    private static final Pattern WORD_FETCH_CALL = Pattern.compile("fetchWordPractice\\s*\\(([^)]*)\\)");

    /** 后端 VO 里的列表字段 */
    private static final Pattern LIST_FIELD = Pattern.compile("private\\s+List<[^>]+>\\s+(\\w+)\\s*;");

    // ================================================================= 扫描有效性

    @Test
    @DisplayName("扫描有效性：背单词页 / 语法页 / api 封装 / 后端列表 VO 都必须存在")
    void sourceFilesMustExist() {
        assertThat(Files.isRegularFile(RedLineScanner.frontendRoot().resolve(WORD_PAGE)))
                .as("背单词页必须存在：" + WORD_PAGE).isTrue();
        assertThat(Files.isRegularFile(RedLineScanner.frontendRoot().resolve(GRAMMAR_PAGE)))
                .as("语法页必须存在：" + GRAMMAR_PAGE).isTrue();
        assertThat(Files.isRegularFile(RedLineScanner.frontendRoot().resolve(API_FILE)))
                .as("API 封装必须存在：" + API_FILE).isTrue();
        assertThat(Files.isRegularFile(RedLineScanner.studyMainRoot().resolve(GRAMMAR_LIST_VO)))
                .as("后端语法点列表 VO 必须存在：" + GRAMMAR_LIST_VO).isTrue();

        assertThat(RedLineScanner.read(RedLineScanner.frontendRoot().resolve(WORD_PAGE)).trim())
                .as("扫描有效性：背单词页内容非空").isNotEmpty();
        assertThat(backendListFieldName())
                .as("扫描有效性：必须能从 " + GRAMMAR_LIST_VO + " 解析出列表字段名")
                .isNotEmpty();
    }

    // ================================================================= 断言 1：背单词必须显式带线别

    @Test
    @DisplayName("背单词页必须显式带 lineType（否则服务端 400，页面空态且孩子背不了词）")
    void wordPageMustPassLineType() {
        String content = RedLineScanner.read(RedLineScanner.frontendRoot().resolve(WORD_PAGE));

        Matcher matcher = WORD_FETCH_CALL.matcher(content);
        assertThat(matcher.find())
                .as("扫描有效性：背单词页必须调用 fetchWordPractice")
                .isTrue();

        String arg = matcher.group(1).trim();
        assertThat(arg)
                .as("背单词页调用 fetchWordPractice 必须带线别参数："
                        + "/word/practice/next 的 lineType 为空时服务端直接 400，"
                        + "而前端是静默请求 → 错误被吞掉，页面显示空态")
                .isNotEmpty();

        assertThat(content)
                .as("线别必须落在 IN（课内）/ OUT（课外）上，V1 范围是课内线优先")
                .contains("'IN'")
                .contains("'OUT'");
    }

    // ================================================================= 断言 2：语法点列表字段名前后端一致

    @Test
    @DisplayName("语法点列表：前端读的字段名必须与后端 VO 逐字一致")
    void grammarPointsFieldNameMustMatchBackendVo() {
        String backendField = backendListFieldName();
        String api = RedLineScanner.read(RedLineScanner.frontendRoot().resolve(API_FILE));
        String page = RedLineScanner.read(RedLineScanner.frontendRoot().resolve(GRAMMAR_PAGE));

        Matcher decl = GRAMMAR_POINTS_DECL.matcher(api);
        assertThat(decl.find())
                .as("扫描有效性：api.ts 必须声明 fetchGrammarPoints 的返回类型")
                .isTrue();
        assertThat(decl.group(1))
                .as("前端 fetchGrammarPoints 的返回类型里必须有后端 VO 的列表字段「%s」", backendField)
                .contains(backendField);

        assertThat(page)
                .as("语法页必须按后端 VO 的字段「%s」取列表（读成别的名字 → 页面永远空态）", backendField)
                .contains("result?." + backendField);
    }

    // ================================================================= 断言 3：学习端不得读后端没有的字段

    @Test
    @DisplayName("学习端页面不得读「后端 VO 里根本没有」的列表字段名")
    void studyPagesMustNotReadUnknownListField() {
        List<String> known = backendListFieldNames();
        assertThat(known)
                .as("扫描有效性：必须能从 study 模块的 VO 里解析出列表字段名集合")
                .isNotEmpty();

        Pattern read = Pattern.compile("result\\?\\.(\\w+)\\s*(\\?\\?|\\|\\|)");
        List<String> problems = new ArrayList<>();
        for (Path file : studyVueFiles()) {
            String content = RedLineScanner.read(file);
            Matcher matcher = read.matcher(content);
            while (matcher.find()) {
                String field = matcher.group(1);
                if (!known.contains(field)) {
                    problems.add(RedLineScanner.frontendRelative(file) + " → 读了 result?." + field
                            + "（后端 VO 里没有这个列表字段名 → 页面静默空态）");
                }
            }
        }

        assertThat(problems)
                .as("学习端读取列表时，字段名必须与后端 VO 一致；读错字段 → 页面静默空态（接口是好的，页面是空的）")
                .isEmpty();
    }

    // ------------------------------------------------------------------ 工具

    /** 解析后端列表 VO 的列表字段名 */
    private static String backendListFieldName() {
        String content = RedLineScanner.read(RedLineScanner.studyMainRoot().resolve(GRAMMAR_LIST_VO));
        Matcher matcher = LIST_FIELD.matcher(content);
        return matcher.find() ? matcher.group(1) : "";
    }

    /**
     * 扫描 study 模块全部 VO，收集所有「列表字段名」。
     *
     * @return 列表字段名集合（例如 items）
     */
    private static List<String> backendListFieldNames() {
        List<String> out = new ArrayList<>();
        Path root = RedLineScanner.studyMainRoot();
        if (!Files.isDirectory(root)) {
            return out;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> vos = new ArrayList<>();
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith("VO.java"))
                    .sorted()
                    .forEach(vos::add);
            for (Path vo : vos) {
                Matcher matcher = LIST_FIELD.matcher(RedLineScanner.read(vo));
                while (matcher.find()) {
                    if (!out.contains(matcher.group(1))) {
                        out.add(matcher.group(1));
                    }
                }
            }
        } catch (IOException e) {
            return out;
        }
        return out;
    }

    /** 学习端全部 .vue（views/study） */
    private static List<Path> studyVueFiles() {
        List<Path> out = new ArrayList<>();
        Path root = RedLineScanner.frontendRoot().resolve("views").resolve("study");
        if (!Files.isDirectory(root)) {
            return out;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".vue"))
                    .sorted()
                    .forEach(out::add);
        } catch (IOException e) {
            return out;
        }
        return out;
    }
}
