package org.jeecg.modules.study.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.jeecg.modules.study.redline.RedLineScanner;

/**
 * **F-BASE-08「离线 / 缓存策略（V0 不做）」的可执行断言套件**。
 *
 * <p>需求原文：<b>V0 明确不做</b>离线答题、本地缓存与断网补交；
 * 断网时给出可读提示并允许重试，<b>不降级为本地判分</b>；
 * 验收是「断网时提示明确、无静默失败；网络恢复后数据无丢失、无重复提交」。</p>
 *
 * <p><b>为什么必须有这个类</b>：F-BASE-08 是一条<b>否定型需求</b>——
 * 它验收的是「<b>没有</b>做某件事」。否定型需求如果只写在文档里，
 * 任何人随手加一个 {@code localStorage} 或在前端算一次对错，
 * 都不会有人拦得住，需求就等于不存在。把它落成源码级静态断言，
 * 才算真的闭合。</p>
 *
 * <p><b>五条断言</b>：</p>
 * <ol>
 *   <li>V0 不做离线与本地缓存：学习端前端不出现任何本地存储 / Service Worker；</li>
 *   <li>断网提示明确 + 可重试：<b>逐页</b>检查加载失败分支（不是只看一个文件）；</li>
 *   <li>接口无状态化：后端不出现 HttpSession，为小程序端预留（无状态是前提）；</li>
 *   <li>不降级为本地判分：前端不自己算对错（要区分「渲染服务端结果」与「本地判定」）；</li>
 *   <li>N-06 / A-15：入口页不挂第三方统计 / 广告脚本（上游百度统计已移除，见断言 5 注释）。</li>
 * </ol>
 *
 * <p><b>扫描有效性（本仓的既有教训）</b>：每一条扫描类断言都先断言
 * 「扫到的集合非空」，否则路径写错 / 目录改名都会让断言<b>假绿</b>。
 * 空集合的 {@code isEmpty()} 永远是 true，那不是通过，那是没扫到东西。</p>
 *
 * @author jeecg-study
 */
class StudyOfflinePolicyTest {

    // update-begin---author:mavis ---date:2026-10-31  for：【N-06 / A-15 / X-08】新增断言5「入口页不得挂第三方统计/广告脚本」——上游 index.html 硬编码百度统计 hm.baidu.com，对每个页面（含学习端/孩子端）无条件注入，直接违反产品红线；已移除脚本并留断言防复发-----------
    // update-end---author:mavis ---date:2026-10-31  for：【N-06 / A-15 / X-08】新增断言5「入口页不得挂第三方统计/广告脚本」——上游 index.html 硬编码百度统计 hm.baidu.com，对每个页面（含学习端/孩子端）无条件注入，直接违反产品红线；已移除脚本并留断言防复发-----------

    // ------------------------------------------------------------------ 扫描范围

    /** 学习端 / 家长端视图目录（相对前端根） */
    private static final String VIEWS_STUDY = "views/study";

    /** 共享的断网模块（相对前端根） */
    private static final String NET_MODULE = "views/study/net.ts";

    // ------------------------------------------------------------------ 词典

    /**
     * 本地缓存 / 离线相关 API（V0 一律不许出现）。
     *
     * <p>{@code sessionStorage} 也在内：它同样是「把学习数据留在浏览器里」，
     * 与 F-BASE-08 的取舍（V0 全部在线请求）冲突。</p>
     */
    private static final List<String> FORBIDDEN_STORAGE = Arrays.asList(
            "localStorage",
            "sessionStorage",
            "indexedDB",
            "caches.open",
            "serviceWorker",
            "workbox");

    /**
     * 后端有状态化痕迹（接口必须无状态，才能被小程序端直接复用）。
     *
     * <p>{@code setAttribute(} 一并禁掉：只要往 session 里塞东西，
     * 多实例部署与横向扩容就会掉数据。</p>
     */
    private static final List<String> FORBIDDEN_SESSION = Arrays.asList(
            "HttpSession",
            "getSession()",
            "setAttribute(",
            "@SessionScope");

    /** 服务端下发的对错字段（前端**渲染**它允许，见断言 4） */
    private static final Pattern CORRECTNESS_ASSIGNMENT = Pattern.compile(
            "(?i)\\b(correct|isCorrect|isRight|right|judgeResult|isJudge)\\b\\s*[:=]\\s*([^;\\n}]+)");

    /** 答案类字段名：前端一旦拿到它，就有可能本地判分（R-01 / R-02） */
    private static final List<String> ANSWER_FIELDS = Arrays.asList(
            "correctAnswer",
            "rightAnswer",
            "standardAnswer",
            "answerKey",
            "answer_key");

    /** 「孩子作答 vs 某个值」的比较：这是本地判分的直接形态 */
    private static final Pattern ANSWER_COMPARISON = Pattern.compile(
            "(?i)\\b(answer|userAnswer|myAnswer|typedAnswer|inputAnswer)\\b\\s*(===|==|!==|!=)\\s*[^=]");

    /** catch 语句 */
    private static final Pattern CATCH = Pattern.compile("catch\\s*\\(");

    /** {@code <script src="http...">}：入口页直接挂第三方脚本 */
    private static final Pattern SCRIPT_TAG_SRC = Pattern.compile(
            "(?is)<script\\b[^>]*\\bsrc\\s*=\\s*[\"'](https?://[^\"']+)[\"']");

    /** JS 里动态建脚本：{@code hm.src = "http..."} */
    private static final Pattern JS_SRC_ASSIGN = Pattern.compile(
            "(?i)\\bsrc\\s*=\\s*[\"'](https?://[^\"']+)[\"']");

    /** 已知统计 / 埋点 / 崩溃上报域名（防止换个写法绕过上两条正则） */
    private static final List<String> ANALYTICS_HOSTS = Arrays.asList(
            "hm.baidu.com",
            "google-analytics.com",
            "googletagmanager.com",
            "cnzz.com",
            "umeng.com",
            "sentry.io",
            "bugsnag.com",
            "matomo");

    // ================================================================= 断言 1：V0 不做离线与本地缓存

    @Test
    @DisplayName("F-BASE-08 断言1：V0 不做离线答题 / 本地缓存 / 断网补交")
    void fb08_noLocalStorageNoOfflineQueue() {
        List<Path> files = studyFrontendFiles();
        assertThat(files)
                .as("扫描有效性：views/study/** 必须扫到非空文件集合，否则下面的断言会假绿")
                .isNotEmpty();

        List<String> hits = new ArrayList<>();
        for (Path file : files) {
            String content = RedLineScanner.read(file);
            for (String token : FORBIDDEN_STORAGE) {
                if (content.contains(token)) {
                    hits.add(RedLineScanner.frontendRelative(file) + " → " + token);
                }
            }
        }
        assertThat(hits)
                .as("F-BASE-08：V0 全部在线请求，不做本地题库缓存、不做离线作答队列、不做断网补交")
                .isEmpty();
    }

    // ================================================================= 断言 2：断网提示明确 + 可重试

    @Test
    @DisplayName("F-BASE-08 断言2：断网时给出可读提示 +「重试」入口（逐页断言）")
    void fb08_offlineHintAndRetryOnEveryPage() {
        // 前置：共享模块本身必须把「重试」写进文案
        Path net = RedLineScanner.frontendRoot().resolve(NET_MODULE);
        assertThat(Files.isRegularFile(net)).as("共享断网模块必须存在：" + NET_MODULE).isTrue();
        String netContent = RedLineScanner.read(net);
        assertThat(netContent)
                .as("共享模块必须导出网络不可达判定 helper")
                .contains("export function isNetworkError");
        assertThat(netContent)
                .as("共享模块必须导出「重试」helper（把异步加载包一层，失败返回可展示错误态）")
                .contains("export async function withRetry");
        assertThat(netContent)
                .as("共享模块必须导出统一文案 OFFLINE_HINT")
                .contains("export const OFFLINE_HINT");
        assertThat(netContent)
                .as("F-BASE-08：断网文案必须含「重试」二字（用户一眼知道可以重试）")
                .containsPattern("OFFLINE_HINT\\s*=\\s*'[^']*重试[^']*'");

        List<Path> pages = studyPageFiles();
        assertThat(pages)
                .as("扫描有效性：学习端页面集合必须非空，否则「逐页断言」会退化成空跑")
                .isNotEmpty();

        List<String> problems = new ArrayList<>();
        for (Path page : pages) {
            String rel = RedLineScanner.frontendRelative(page);
            // 判定范围 = 页面 + 它直接 import 的本项目组件
            //
            //   管理端的 9 个内容维护页是「薄配置页 + 共享内核」：
            //   页面本身不发请求，发请求的是内核（ManageContentPage.vue），
            //   断网提示与「重试」入口也渲染在内核里。
            //   只扫页面文件会把「委托给内核」误判成「没处理断网」，
            //   于是要么断言永远红，要么被改松到失去意义。
            //   把「页面 + 它 import 的本项目组件」一起算，才对应真正的问题：
            //   这一页渲染出来的东西，断网时有没有可读提示和重试入口。
            String content = effectiveContent(page);
            assertThat(content.trim())
                    .as("扫描有效性：" + rel + " 必须非空")
                    .isNotEmpty();

            // 1) 页面必须接入共享的断网模块
            if (!Pattern.compile("from\\s+'(\\.\\./)+net'").matcher(content).find()) {
                problems.add(rel + " → 未 import 共享断网模块（net.ts）");
                continue;
            }

            // 2) 加载失败分支里必须同时出现「可读提示文案」与「重试」入口
            List<String> blocks = catchBlocks(content);
            assertThat(blocks).as(rel + " 必须有 catch 分支").isNotEmpty();
            boolean hasOfflineBranch = false;
            for (String block : blocks) {
                boolean mentionsHelper = block.contains("describeNetError") || block.contains("withRetry");
                boolean mentionsHint = block.contains("OFFLINE_HINT") || block.contains("网络好像断了");
                boolean mentionsRetry = block.contains("RETRY_LABEL") || block.contains("重试");
                if (mentionsHelper && mentionsHint && mentionsRetry) {
                    hasOfflineBranch = true;
                    break;
                }
            }
            if (!hasOfflineBranch) {
                problems.add(rel + " → catch 分支里没有同时出现「可读提示文案」与「重试」入口");
            }

            // 3) 页面必须真的渲染出用户看得见的「重试」入口（不是只写个变量）
            if (!content.contains(">重试<")) {
                problems.add(rel + " → 页面上没有用户可见的「重试」入口");
            }
        }

        assertThat(problems)
                .as("F-BASE-08：断网时必须给出可读提示并允许重试，且不能静默失败")
                .isEmpty();
    }

    // ================================================================= 断言 3：接口无状态化

    @Test
    @DisplayName("F-BASE-08 断言3：接口无状态化（不出现 HttpSession，为小程序端预留）")
    void fb08_backendIsStateless() {
        List<Path> sources = RedLineScanner.javaSources();
        assertThat(sources)
                .as("扫描有效性：study 模块主源码必须扫到非空文件集合，否则会假绿")
                .isNotEmpty();

        List<String> hits = new ArrayList<>();
        for (Path file : sources) {
            String content = RedLineScanner.read(file);
            for (String token : FORBIDDEN_SESSION) {
                if (content.contains(token)) {
                    hits.add(RedLineScanner.moduleRelative(file) + " → " + token);
                }
            }
        }
        assertThat(hits)
                .as("F-BASE-08 / 无状态接口：所有状态走 JWT + 数据库，不挂 HttpSession（小程序端才能复用）")
                .isEmpty();
    }

    // ================================================================= 断言 4：不降级为本地判分

    @Test
    @DisplayName("F-BASE-08 断言4：不降级为本地判分（前端不自己算对错）")
    void fb08_noLocalJudgingInFrontend() {
        List<Path> files = studyFrontendFiles();
        assertThat(files)
                .as("扫描有效性：学习端前端文件集合必须非空，否则会假绿")
                .isNotEmpty();

        List<String> hits = new ArrayList<>();
        for (Path file : files) {
            String content = RedLineScanner.read(file);
            String rel = RedLineScanner.frontendRelative(file);

            // 4.1 前端不许拿到答案字段
            for (String field : ANSWER_FIELDS) {
                if (content.contains(field)) {
                    hits.add(rel + " → 前端出现答案字段 " + field);
                }
            }

            // 4.2 前端不许拿「孩子作答」去和某个值比较
            Matcher comparison = ANSWER_COMPARISON.matcher(content);
            while (comparison.find()) {
                hits.add(rel + " → 本地比对作答: " + comparison.group().trim());
            }

            // 4.3 给「对错」标识赋值时，右值不得含比较 / 包含判断
            //
            //   判定逻辑（区分「渲染服务端结果」与「本地自己算」）：
            //     · 右值只是**读取**服务端字段（result.correct / Boolean(result?.correct)）→ 允许；
            //     · 右值含 == / === / != / .includes( 等比较或匹配 → 就是前端自己算对错 → 违规。
            Matcher assignment = CORRECTNESS_ASSIGNMENT.matcher(content);
            while (assignment.find()) {
                String rhs = assignment.group(2);
                if (containsComparison(rhs)) {
                    hits.add(rel + " → 本地判分: " + assignment.group(1).trim() + " = " + rhs.trim());
                }
            }
        }

        assertThat(hits)
                .as("F-BASE-08 / R-02：断网不得降级为本地判分；对错只能由服务端规则给出")
                .isEmpty();
    }

    // ================================================================= 断言 5：不挂第三方统计 / 广告脚本

    /**
     * N-06 / A-15：产品红线要求「不接入第三方统计 SDK、所有数据留在本地」，
     * 验收方式写得很死——<b>网络抓包确认无第三方统计 / 广告请求</b>。
     *
     * <p><b>真实踩过的坑（2026-10-31）</b>：上游 JeecgBoot 的
     * {@code jeecgboot-vue3/index.html} 里硬编码了一段百度统计脚本
     * （{@code hm.src = "https://hm.baidu.com/hm.js?..."}），
     * 它在 {@code window.load} 时<b>无条件</b>注入到<b>每一个页面</b>——
     * 包括本项目的学习端与孩子端页面，等于每个孩子打开一次练习就往百度发一次请求。
     * 这直接违反 N-06 / A-15 / X-08，必须移除，且要有断言防止它被合回去。</p>
     *
     * <p>断言方式：扫入口页 {@code index.html}，抓两种「加载第三方脚本」的形态——
     * {@code <script src="http...">} 与 JS 里的 {@code .src = "http..."} 赋值。
     * 纯超链接（{@code <a href="http...">}）不算，因为那不产生数据外传请求，
     * 且上游 demo / 系统配置页里的帮助文档链接不在学习端路径上。</p>
     */
    @Test
    @DisplayName("N-06 / A-15 断言5：入口页不得挂第三方统计 / 广告脚本")
    void n06_noThirdPartyAnalyticsScript() {
        // RedLineScanner.frontendRoot() 指的是前端工程的 **src** 目录
        // （views/study 就是从这里解析的），而 index.html 在 src 的**上一级**。
        Path index = RedLineScanner.frontendRoot().getParent().resolve("index.html");
        assertThat(Files.isRegularFile(index))
                .as("前端入口页必须存在：" + index + "（扫描有效性，否则下面的断言会假绿）")
                .isTrue();

        String content = RedLineScanner.read(index);
        assertThat(content).as("扫描有效性：index.html 内容非空").isNotEmpty();

        // ⚠ 必须先剥掉 HTML 注释再扫：
        //   本仓改上游文件要留 update-begin/update-end 痕迹，而痕迹文本里
        //   **必然会出现被移除的域名**（如 hm.baidu.com）——那是「改过什么」的说明，
        //   不是「现在还在加载什么」。不剥注释就会把修改记录当成违规，
        //   断言会永远红，而且真正的故障注入也验不出来。
        String effective = stripHtmlComments(content);

        List<String> hits = new ArrayList<>();

        // 形态一：<script src="http...">
        Matcher tag = SCRIPT_TAG_SRC.matcher(effective);
        while (tag.find()) {
            hits.add("index.html → <script src=\"" + tag.group(1) + "\">");
        }
        // 形态二：JS 里动态建脚本（.src = "http..."）
        Matcher assign = JS_SRC_ASSIGN.matcher(effective);
        while (assign.find()) {
            hits.add("index.html → 动态脚本 src=" + assign.group(1));
        }
        // 形态三：已知统计 / 埋点 / 崩溃上报域名的字面量（防止换个写法绕过前两条）
        for (String host : ANALYTICS_HOSTS) {
            if (effective.contains(host)) {
                hits.add("index.html → 出现第三方统计/上报域名 " + host);
            }
        }

        assertThat(hits)
                .as("N-06 / A-15 / X-08：产品红线要求不接入第三方统计、广告、崩溃上报 SDK，"
                        + "所有数据留在本地；入口页挂第三方脚本会让每个学习端页面都产生数据外传请求")
                .isEmpty();
    }

    // ------------------------------------------------------------------ 工具

    /**
     * 剥掉 HTML 注释（含本仓的 update-begin/update-end 改动痕迹）。
     *
     * <p>痕迹文本里会写明「移除了什么」，如果连注释一起扫，
     * 修改记录本身就会把断言打成永远红。</p>
     *
     * @param html 原始 HTML
     * @return 去掉注释后的文本
     */
    private static String stripHtmlComments(String html) {
        return html.replaceAll("(?s)<!--.*?-->", " ");
    }

    /**
     * 判定范围 = 页面 + 它直接 import 的本项目组件。
     *
     * @param page 页面文件
     * @return 拼接后的文本
     */
    private static String effectiveContent(Path page) {
        StringBuilder sb = new StringBuilder(RedLineScanner.read(page));
        for (Path local : localImports(page)) {
            sb.append('\n').append(RedLineScanner.read(local));
        }
        return sb.toString();
    }

    /**
     * 页面里用<b>相对路径</b> import 的本项目文件。
     *
     * <p>只认相对路径（{@code ./x} / {@code ../x}）：绝对别名（{@code /@/...}）
     * 与第三方包不在本项目内，不需要纳入判定。</p>
     *
     * @param page 页面文件
     * @return 命中的本项目文件
     */
    private static List<Path> localImports(Path page) {
        List<Path> out = new ArrayList<>();
        Path root = RedLineScanner.frontendRoot();
        Matcher matcher = Pattern.compile("from\\s+'(\\.\\.?/[^']+)'").matcher(RedLineScanner.read(page));
        while (matcher.find()) {
            Path base = page.getParent().resolve(matcher.group(1)).normalize();
            String name = base.getFileName() == null ? "" : base.getFileName().toString();
            List<Path> candidates = new ArrayList<>();
            // ① 已经是带扩展名的文件（基座的 vite 不配 resolve.extensions，
            //    所以 .vue 导入必须写全扩展名）
            candidates.add(base);
            if (!name.endsWith(".vue") && !name.endsWith(".ts")) {
                candidates.add(base.resolveSibling(name + ".vue").normalize());
                candidates.add(base.resolveSibling(name + ".ts").normalize());
            }
            // ② 目录形态的模块
            candidates.add(base.resolve("index.vue").normalize());
            candidates.add(base.resolve("index.ts").normalize());
            for (Path candidate : candidates) {
                if (Files.isRegularFile(candidate) && candidate.startsWith(root)) {
                    out.add(candidate);
                    break;
                }
            }
        }
        return out;
    }

    /** 右值里是否含比较 / 匹配运算（本地判分的形态） */
    private static boolean containsComparison(String rhs) {
        String stripped = rhs.replace("'", "").replace("\"", "");
        if (stripped.contains("===") || stripped.contains("!==")) {
            return true;
        }
        // 单个 = 是赋值，两个 == 才是比较；这里只关心比较
        if (stripped.contains("==") || stripped.contains("!=")) {
            return true;
        }
        return stripped.contains(".includes(") || stripped.contains(".startsWith(") || stripped.contains(".endsWith(");
    }

    /** 学习端 / 家长端前端文件（.vue / .ts） */
    private static List<Path> studyFrontendFiles() {
        Path root = RedLineScanner.frontendRoot().resolve(VIEWS_STUDY);
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return name.endsWith(".vue") || name.endsWith(".ts");
                    })
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** 学习端页面（views/study 下的 index.vue） */
    private static List<Path> studyPageFiles() {
        List<Path> out = new ArrayList<>();
        for (Path file : studyFrontendFiles()) {
            if ("index.vue".equals(file.getFileName().toString())) {
                out.add(file);
            }
        }
        return out;
    }

    /**
     * 取出所有 catch 块的正文。
     *
     * <p>按大括号配平扫描：先找到 {@code catch (} 的右括号，再往后找左花括号，
     * 一路配平到右花括号，取中间的内容。</p>
     */
    private static List<String> catchBlocks(String content) {
        List<String> out = new ArrayList<>();
        Matcher matcher = CATCH.matcher(content);
        while (matcher.find()) {
            int parenOpen = matcher.end() - 1;
            int parenClose = matchPair(content, parenOpen, '(', ')');
            if (parenClose < 0) {
                continue;
            }
            int braceOpen = content.indexOf('{', parenClose);
            if (braceOpen < 0 || braceOpen - parenClose > 40) {
                continue;
            }
            int braceClose = matchPair(content, braceOpen, '{', '}');
            if (braceClose < 0) {
                continue;
            }
            out.add(content.substring(braceOpen + 1, braceClose));
        }
        return out;
    }

    /** 从 open 位置开始配平括号，返回闭合位置；找不到返回 -1 */
    private static int matchPair(String content, int open, char left, char right) {
        int depth = 0;
        for (int i = open; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == left) {
                depth++;
            } else if (c == right) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }
}
