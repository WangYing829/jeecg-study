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

    /**
     * 后端 VO 里的<b>全部</b>字段（不限类型）。
     *
     * <p><b>为什么不能只收列表字段</b>（2026-11-04 实测踩坑）：
     * 「读一个后端没有的字段」这个检查，如果已知集合只有<b>列表型</b>字段，
     * 那么前端读一个**合法的标量字段**（如 {@code ReviewCompleteVO.message}，String 型）
     * 也会被误判成「后端没有这个字段」。
     * 结果是**为了过断言把代码写变形**（先落到局部变量再判空），
     * 而**语义问题原样留着** —— 闸门从「抓错」退化成「逼人钻空子」。
     * ⇒ 已知集合必须是**全部 VO 字段**；列表的严格性由
     * {@link #listReadsMustTargetListFields()} 单独守。</p>
     */
    private static final Pattern ALL_FIELD = Pattern.compile(
            "(?m)^\\s*private\\s+(?!static)[\\w.<>,\\[\\]\\s]+?\\s(\\w+)\\s*(?:=[^;]*)?;");

    /** 取数写法：{@code result?.字段 ?? 兜底} 或 {@code result?.字段 || 兜底} */
    private static final Pattern READ_FIELD = Pattern.compile("result\\?\\.(\\w+)\\s*(\\?\\?|\\|\\|)");

    /**
     * <b>列表读法</b>：{@code result?.字段 ?? []} —— 兜底值是<b>空数组</b>。
     *
     * <p>这种写法只在读<b>列表</b>时出现，所以字段名必须命中后端的列表字段，
     * 不能用「任何 VO 字段」蒙混过关。</p>
     */
    private static final Pattern LIST_READ_FIELD = Pattern.compile("result\\?\\.(\\w+)\\s*\\?\\?\\s*\\[");

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
    @DisplayName("学习端页面不得读「后端 VO 里根本没有」的字段名")
    void studyPagesMustNotReadUnknownListField() {
        List<String> known = backendAllFieldNames();
        assertThat(known)
                .as("扫描有效性：必须能从 study 模块的 VO 里解析出字段名集合")
                .isNotEmpty();

        Pattern read = READ_FIELD;
        List<String> problems = new ArrayList<>();
        for (Path file : studyVueFiles()) {
            String content = RedLineScanner.read(file);
            Matcher matcher = read.matcher(content);
            while (matcher.find()) {
                String field = matcher.group(1);
                if (!known.contains(field)) {
                    problems.add(RedLineScanner.frontendRelative(file) + " → 读了 result?." + field
                            + "（后端 VO 里没有这个字段名 → 页面静默空态）");
                }
            }
        }

        assertThat(problems)
                .as("学习端取数时，字段名必须与后端 VO 一致；读错字段 → 页面静默空态（接口是好的，页面是空的）")
                .isEmpty();
    }

    /**
     * <b>列表读法必须命中后端的列表字段</b>（更严的一条，2026-11-04 补）。
     *
     * <p>{@code result?.items ?? []} 这种写法只在读列表时出现，
     * 所以字段名必须是后端 VO 里真实存在的<b>列表</b>字段。
     * 读成标量字段名（哪怕它确实存在于某个 VO 里）⇒ 兜底成空数组 ⇒ <b>页面永远空态</b>。</p>
     */
    @Test
    @DisplayName("学习端按空数组兜底的读法，字段名必须是后端 VO 的列表字段")
    void listReadsMustTargetListFields() {
        List<String> listFields = backendListFieldNames();
        assertThat(listFields)
                .as("扫描有效性：必须能从 study 模块的 VO 里解析出列表字段名集合")
                .isNotEmpty();

        List<String> problems = new ArrayList<>();
        for (Path file : studyVueFiles()) {
            String content = RedLineScanner.read(file);
            Matcher matcher = LIST_READ_FIELD.matcher(content);
            while (matcher.find()) {
                String field = matcher.group(1);
                if (!listFields.contains(field)) {
                    problems.add(RedLineScanner.frontendRelative(file) + " → result?." + field + " ?? []"
                            + "（后端 VO 里没有这个**列表**字段名 → 列表恒为空）");
                }
            }
        }

        assertThat(problems)
                .as("列表兜底写法必须命中后端 VO 的列表字段：\n%s", String.join("\n", problems))
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
        for (Path vo : allVoFiles()) {
            Matcher matcher = LIST_FIELD.matcher(RedLineScanner.read(vo));
            while (matcher.find()) {
                if (!out.contains(matcher.group(1))) {
                    out.add(matcher.group(1));
                }
            }
        }
        return out;
    }

    /**
     * 扫描 study 模块全部 VO，收集所有<b>字段名</b>（不限类型）。
     *
     * <p>用于「前端读的字段后端到底有没有」这一检查 ——
     * 已知集合只收列表字段会把<b>合法的标量字段</b>（如 {@code ReviewCompleteVO.message}）
     * 误判成「后端没有」，逼着前端把代码写变形。详见 {@link #ALL_FIELD} 的说明。</p>
     *
     * @return 全部字段名集合
     */
    private static List<String> backendAllFieldNames() {
        List<String> out = new ArrayList<>();
        for (Path vo : allVoFiles()) {
            Matcher matcher = ALL_FIELD.matcher(RedLineScanner.read(vo));
            while (matcher.find()) {
                if (!out.contains(matcher.group(1))) {
                    out.add(matcher.group(1));
                }
            }
        }
        return out;
    }

    /** study 模块全部 VO 源文件（按路径排序，保证结果稳定） */
    private static List<Path> allVoFiles() {
        List<Path> out = new ArrayList<>();
        Path root = RedLineScanner.studyMainRoot();
        if (!Files.isDirectory(root)) {
            return out;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith("VO.java"))
                    .sorted()
                    .forEach(out::add);
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
