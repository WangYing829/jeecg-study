package org.jeecg.modules.study.writing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.jeecg.modules.study.writing.vo.WritingDiffVO;
import org.jeecg.modules.study.writing.vo.WritingDissectionLayerVO;
import org.jeecg.modules.study.writing.vo.WritingDissectionListVO;
import org.jeecg.modules.study.writing.vo.WritingDissectionVO;
import org.jeecg.modules.study.writing.vo.WritingDraftBriefVO;
import org.jeecg.modules.study.writing.vo.WritingDraftResultVO;
import org.jeecg.modules.study.writing.vo.WritingFeedbackListVO;
import org.jeecg.modules.study.writing.vo.WritingFeedbackVO;
import org.jeecg.modules.study.writing.vo.WritingOutlineFeedbackVO;
import org.jeecg.modules.study.writing.vo.WritingOutlineResultVO;
//update-begin---author:mavis ---date:2026-10-31  for：【F-WRT-03】写作情境库：引入情境相关 VO 的导入-----------
import org.jeecg.modules.study.writing.vo.WritingScenarioListVO;
import org.jeecg.modules.study.writing.vo.WritingScenarioThemeListVO;
import org.jeecg.modules.study.writing.vo.WritingScenarioThemeVO;
import org.jeecg.modules.study.writing.vo.WritingScenarioVO;
//update-end---author:mavis ---date:2026-10-31  for：【F-WRT-03】写作情境库：引入情境相关 VO 的导入-----------

/**
 * 写作域自检：重复名 + 红线（R-02 / R-04）。
 *
 * <p>两件事必须自己守住：</p>
 * <ul>
 *   <li><b>类简单名全模块唯一</b> —— MyBatis-Spring 2.0.7 用「类名去首字母小写」当 bean 名，
 *       同名会抛 {@code ConflictingBeanDefinitionException}，Spring 上下文起不来；</li>
 *   <li><b>红线</b> —— 孩子端响应里没有分数名次类字段；家长端响应里没有讲解建议类字段；
 *       写作域不依赖任何 AI 客户端（R-02）；文案里没有排名 / 家庭内比较 / 打分字样（R-04）。</li>
 * </ul>
 *
 * <p><b>源码目录定位</b>：从当前工作目录向上找 {@code src/main/java/org/jeecg/modules/study}，
 * 因此无论测试是在模块目录还是仓库根启动都能扫到；找不到就直接失败，
 * 绝不因为「扫到 0 个文件」而假通过。</p>
 *
 * @author jeecg-study
 */
class StudyWritingRedLineTest {

    /** Java 字符串字面量 */
    private static final Pattern JAVA_STRING = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

    /** 孩子端响应 VO 里绝不能出现的字段关键词 */
    private static final List<String> KID_FORBIDDEN_FIELDS = Arrays.asList(
            "score", "points", "rank", "percentile", "totalscore", "grade", "level");

    /** 家长端响应 VO 里绝不能出现的字段关键词 */
    private static final List<String> PARENT_FORBIDDEN_FIELDS = Arrays.asList(
            "answer", "analysis", "correctanswer", "explanation", "advice", "suggestion",
            "teaching", "tutoring", "rank", "percentile", "ranking");

    /** 写作域禁止出现的文案措辞 */
    private static final List<String> FORBIDDEN_COPY = Arrays.asList(
            "排名", "排行", "百分位", "名次", "超过同学",
            "谁的孩子更", "比上次", "比上个月", "比另一个孩子", "比别的孩子", "更进步",
            "扣了几", "打分", "等级", "分数", "级别", "得分");

    /** 写作域禁止依赖的 AI 客户端包 */
    private static final List<String> AI_CLIENT_PACKAGES = Arrays.asList(
            "org.springframework.ai",
            "com.alibaba.dashscope",
            "com.alibaba.cloud.ai",
            "com.theokanning.openai",
            "com.langchain4j",
            "com.zhipu",
            "cn.hutool.ai",
            "openai.");

    /** 孩子端响应 VO */
    //update-begin---author:mavis ---date:2026-10-31  for：【F-WRT-03】写作情境库：把情境相关 VO 纳入孩子端红线自检-----------
    private static final List<Class<?>> KID_VOS = Arrays.asList(
            WritingDissectionListVO.class,
            WritingDissectionVO.class,
            WritingDissectionLayerVO.class,
            WritingOutlineResultVO.class,
            WritingOutlineFeedbackVO.class,
            WritingDraftResultVO.class,
            WritingDiffVO.class,
            WritingScenarioListVO.class,
            WritingScenarioVO.class,
            WritingScenarioThemeListVO.class,
            WritingScenarioThemeVO.class);
    //update-end---author:mavis ---date:2026-10-31  for：【F-WRT-03】写作情境库：把情境相关 VO 纳入孩子端红线自检-----------

    /** 家长端响应 VO */
    private static final List<Class<?>> PARENT_VOS = Arrays.asList(
            WritingFeedbackVO.class,
            WritingFeedbackListVO.class,
            WritingDraftBriefVO.class);

    // ------------------------------------------------------------------ 重复名

    @Test
    @DisplayName("自检-01 类简单名全模块唯一")
    void classSimpleNamesAreUniqueInModule() throws IOException {
        Path root = studyMainRoot();

        Map<String, List<String>> byName = new LinkedHashMap<>();
        for (Path path : javaFiles(root)) {
            String simpleName = path.getFileName().toString().replace(".java", "");
            byName.computeIfAbsent(simpleName, key -> new ArrayList<>())
                    .add(path.toString().replace('\\', '/'));
        }
        assertThat(byName).as("必须扫到 study 模块的源码").isNotEmpty();

        List<String> duplicated = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : byName.entrySet()) {
            if (entry.getValue().size() > 1) {
                duplicated.add(entry.getKey() + " → " + entry.getValue());
            }
        }

        assertThat(duplicated)
                .as("类简单名必须全模块唯一（MyBatis bean 名冲突会让 Spring 上下文起不来）")
                .isEmpty();
    }

    // ------------------------------------------------------------------ R-04：字段

    @Test
    @DisplayName("自检-02 孩子端响应 VO 无分数名次类字段")
    void kidVOsHaveNoScoreOrRankField() {
        List<String> hits = new ArrayList<>();
        for (Class<?> vo : KID_VOS) {
            for (Field field : vo.getDeclaredFields()) {
                String name = field.getName().toLowerCase(Locale.ROOT);
                for (String forbidden : KID_FORBIDDEN_FIELDS) {
                    if (name.contains(forbidden)) {
                        hits.add(vo.getSimpleName() + "#" + field.getName());
                    }
                }
            }
        }
        assertThat(hits).as("孩子端响应 VO 不得含分数 / 名次类字段").isEmpty();
    }

    @Test
    @DisplayName("自检-03 家长端响应 VO 无讲解建议类字段")
    void parentVOsHaveNoTeachingOrSuggestionField() {
        List<String> hits = new ArrayList<>();
        for (Class<?> vo : PARENT_VOS) {
            for (Field field : vo.getDeclaredFields()) {
                String name = field.getName().toLowerCase(Locale.ROOT);
                for (String forbidden : PARENT_FORBIDDEN_FIELDS) {
                    if (name.contains(forbidden)) {
                        hits.add(vo.getSimpleName() + "#" + field.getName());
                    }
                }
            }
        }
        assertThat(hits).as("家长端响应 VO 不得含讲解 / 建议 / 名次类字段").isEmpty();
    }

    // ------------------------------------------------------------------ R-04：文案

    @Test
    @DisplayName("自检-04 写作域文案无排名 / 比较 / 打分措辞")
    void writingCopyHasNoForbiddenWording() throws IOException {
        Path root = studyMainRoot().resolve("writing");
        assertThat(Files.isDirectory(root)).as("必须找到写作域源码目录").isTrue();

        List<String> hits = new ArrayList<>();
        int scanned = 0;
        for (Path path : javaFiles(root)) {
            scanned++;
            for (String literal : chineseStringLiterals(read(path))) {
                for (String word : FORBIDDEN_COPY) {
                    if (literal.contains(word)) {
                        hits.add(path.getFileName() + " → 「" + literal.trim() + "」含「" + word + "」");
                    }
                }
            }
        }
        assertThat(scanned).as("必须扫到写作域的源码文件").isGreaterThan(0);
        assertThat(hits).as("写作域文案不得出现排名 / 家庭内比较 / 打分类措辞").isEmpty();
    }

    // ------------------------------------------------------------------ R-02：AI 依赖

    @Test
    @DisplayName("自检-05 写作域不依赖任何 AI 客户端")
    void writingDomainHasNoAiClientDependency() throws IOException {
        Path root = studyMainRoot().resolve("writing");
        assertThat(Files.isDirectory(root)).as("必须找到写作域源码目录").isTrue();

        List<String> hits = new ArrayList<>();
        for (Path path : javaFiles(root)) {
            for (String line : read(path).split("\n")) {
                String trimmed = line.trim();
                if (!trimmed.startsWith("import ")) {
                    continue;
                }
                for (String pkg : AI_CLIENT_PACKAGES) {
                    if (trimmed.toLowerCase(Locale.ROOT).contains(pkg.toLowerCase(Locale.ROOT))) {
                        hits.add(path.getFileName() + " → " + trimmed);
                    }
                }
            }
        }
        assertThat(hits).as("写作域的判定必须全部由确定性规则完成，不得依赖 AI 客户端").isEmpty();
    }

    // ------------------------------------------------------------------ 内部

    /**
     * study 模块的源码根：先从当前工作目录向上找，再做一次带剪枝的向下广度优先查找。
     *
     * <p>找不到就直接失败，<b>绝不因为「扫到 0 个文件」而假通过</b>。</p>
     */
    private Path studyMainRoot() {
        Path found = searchUpwards(Paths.get("").toAbsolutePath());
        if (found != null) {
            return found;
        }
        found = searchDownwards(Paths.get("").toAbsolutePath());
        if (found != null) {
            return found;
        }
        throw new AssertionError("找不到 study 模块源码目录（当前工作目录："
                + Paths.get("").toAbsolutePath() + "）");
    }

    /** 源码根相对模块目录的路径 */
    private static final Path SOURCE_SUB_PATH = Paths.get(
            "src", "main", "java", "org", "jeecg", "modules", "study");

    /** 向下查找时跳过的目录（体量大且与本断言无关） */
    private static final List<String> SKIP_DIRS = Arrays.asList(
            "node_modules", ".git", "target", "dist", ".idea", ".gradle", "build");

    /**
     * 从起点向上逐级找 {@code src/main/java/org/jeecg/modules/study}。
     */
    private Path searchUpwards(Path start) {
        Path current = start;
        for (int i = 0; i < 10 && current != null; i++) {
            Path candidate = current.resolve(SOURCE_SUB_PATH);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return null;
    }

    /**
     * 从起点向下广度优先找（跳过 node_modules / target 等大目录）。
     */
    private Path searchDownwards(Path start) {
        java.util.Deque<Path> queue = new java.util.ArrayDeque<>();
        queue.add(start);
        int visited = 0;
        while (!queue.isEmpty() && visited < 5000) {
            Path dir = queue.poll();
            visited++;
            List<Path> children = new ArrayList<>();
            try (Stream<Path> stream = Files.list(dir)) {
                stream.forEach(children::add);
            } catch (IOException e) {
                continue;
            }
            for (Path child : children) {
                String name = child.getFileName().toString();
                if (SKIP_DIRS.contains(name)) {
                    continue;
                }
                if (!Files.isDirectory(child)) {
                    continue;
                }
                Path candidate = child.resolve(SOURCE_SUB_PATH);
                if (Files.isDirectory(candidate)) {
                    return candidate;
                }
                queue.add(child);
            }
        }
        return null;
    }

    private List<Path> javaFiles(Path root) throws IOException {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }
    }

    private String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    /**
     * 取出源码里的中文字符串字面量。
     */
    private List<String> chineseStringLiterals(String content) {
        List<String> out = new ArrayList<>();
        Matcher matcher = JAVA_STRING.matcher(content);
        while (matcher.find()) {
            String raw = matcher.group(1);
            if (raw.codePoints().anyMatch(ch -> ch >= 0x4e00 && ch <= 0x9fa5)) {
                out.add(raw);
            }
        }
        return out;
    }
}
