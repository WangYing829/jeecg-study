package org.jeecg.modules.study.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import org.jeecg.modules.study.support.StudyTestSupport;

/**
 * **「所有菜单都没有图标（全是空心圆点）」**的可执行断言套件。
 *
 * <p><b>真实踩过的坑（2026-11-02，管理端截图标注）</b>：
 * 管理端「菜单管理」列表的「图标」列，上游菜单与 study 菜单**全部**渲染成空心圆点。</p>
 *
 * <p><b>根因不在数据库</b>：{@code sys_permission.icon} 实测有 92 个不同的图标值，全都有值。
 * 真因是前端：{@code .env.development} 里 {@code VITE_GLOB_ICONIFY_USE_TYPE=online}，
 * 而 online 分支的图标数据要<b>在线</b>从 {@code api.iconify.design} 拉 ——
 * 内网不通时 {@code Iconify.renderSVG()} 返回 null，
 * 图标组件退化成占位符 {@code <span class="iconify">}（样式是 {@code border-radius:100%}），
 * 也就是那个空心圆点。</p>
 *
 * <p>改成 {@code local} 之后，图标数据来自 {@code vite-plugin-purge-icons} 生成的离线图标集，
 * 而那个插件<b>只扫源码文本</b>（{@code content} 配置里列的 html / pug / vue / js / ts）。
 * 于是又暴露第二层缺口：<b>只存在于数据库、源码里从没出现过的图标名不会被收集</b>，
 * 离线模式下照样是空圆点（实测：{@code ant-design:solution} / {@code ant-design:team} /
 * {@code ant-design:user} 三个）。所以必须有 {@code src/components/Icon/data/menu-icons.ts}
 * 这份「数据库菜单图标白名单」。</p>
 *
 * <p>本类把这条链路的三段全盯住：</p>
 * <ol>
 *   <li>各环境文件的 {@code VITE_GLOB_ICONIFY_USE_TYPE} 必须是 {@code local}（离线可用）；</li>
 *   <li>图标组件必须保留 local 分支（走 {@code @purge-icons/generated} 离线图标集）；</li>
 *   <li><b>数据库里的每一个菜单图标，都必须能被离线图标集收集到</b>
 *       （出现在白名单文件或前端源码里）—— 漏一个就是一个空圆点。</li>
 * </ol>
 *
 * @author jeecg-study
 */
class StudyMenuIconOfflineTest extends StudyTestSupport {

    /** 菜单图标白名单（相对前端 src） */
    private static final String MENU_ICON_SAFELIST = "components/Icon/data/menu-icons.ts";

    /** 图标组件（相对前端 src） */
    private static final String ICON_COMPONENT = "components/Icon/src/Icon.vue";

    /** 图标选择器清单（相对前端 src） */
    private static final String ICON_PICKER_DATA = "components/Icon/data/icons.data.ts";

    /** 必须配成 local 的环境文件（相对前端工程根） */
    private static final List<String> ENV_FILES = List.of(
            ".env.development", ".env.production", ".env.docker", ".env.dockercloud");

    /** 图标名记号：collection:name */
    private static final Pattern ICON_TOKEN = Pattern.compile("[a-z][a-z0-9-]*:[a-z0-9][a-z0-9-]*");

    /** 会被 vite-plugin-purge-icons 扫描的源码后缀（与插件 content 配置一致） */
    private static final List<String> SCANNED_SUFFIXES =
            List.of(".html", ".pug", ".vue", ".js", ".ts", ".tsx", ".jsx");

    // ================================================================= 扫描有效性

    @Test
    @DisplayName("扫描有效性：环境文件、图标组件、白名单文件、数据库菜单图标都必须存在且非空")
    void sourceFilesAndDbIconsMustExist() {
        Path projectRoot = frontendProjectRoot();
        for (String env : ENV_FILES) {
            Path file = projectRoot.resolve(env);
            assertThat(Files.isRegularFile(file)).as("环境文件必须存在：" + env).isTrue();
            assertThat(RedLineScanner.read(file).trim()).as("扫描有效性：" + env + " 内容非空").isNotEmpty();
        }

        Path safelist = RedLineScanner.frontendRoot().resolve(MENU_ICON_SAFELIST);
        Path iconComponent = RedLineScanner.frontendRoot().resolve(ICON_COMPONENT);
        Path pickerData = RedLineScanner.frontendRoot().resolve(ICON_PICKER_DATA);
        assertThat(Files.isRegularFile(safelist)).as("菜单图标白名单必须存在：" + MENU_ICON_SAFELIST).isTrue();
        assertThat(Files.isRegularFile(iconComponent)).as("图标组件必须存在：" + ICON_COMPONENT).isTrue();
        assertThat(Files.isRegularFile(pickerData)).as("图标清单必须存在：" + ICON_PICKER_DATA).isTrue();
        assertThat(RedLineScanner.read(safelist).trim()).as("扫描有效性：白名单内容非空").isNotEmpty();

        assertThat(dbMenuIcons()).as("扫描有效性：数据库里必须查得到菜单图标").isNotEmpty();
        assertThat(safelistIcons()).as("扫描有效性：必须能从白名单解析出图标名").isNotEmpty();
        assertThat(sourceIconTokens()).as("扫描有效性：必须能从前端源码解析出图标记号").isNotEmpty();
    }

    // ================================================================= 断言 1：各环境必须用离线图标集

    @Test
    @DisplayName("各环境的 VITE_GLOB_ICONIFY_USE_TYPE 必须是 local，否则离线环境图标全是空心圆点")
    void everyEnvMustUseLocalIconSource() {
        List<String> problems = new ArrayList<>();
        for (String env : ENV_FILES) {
            String value = envValue(frontendProjectRoot().resolve(env), "VITE_GLOB_ICONIFY_USE_TYPE");
            if (value == null) {
                problems.add(env + " 没有配置 VITE_GLOB_ICONIFY_USE_TYPE（缺省会走 online → 离线必然空圆点）");
            } else if (!"local".equals(value)) {
                problems.add(env + " 的 VITE_GLOB_ICONIFY_USE_TYPE=" + value + "，应为 local");
            }
        }
        assertThat(problems).as("离线环境下图标必须走 local（本地 @iconify/json 打包）").isEmpty();
    }

    // ================================================================= 断言 2：图标组件必须保留 local 分支

    @Test
    @DisplayName("图标组件必须保留 local 分支：走 @purge-icons/generated 离线图标集")
    void iconComponentMustKeepLocalBranch() {
        String source = RedLineScanner.read(RedLineScanner.frontendRoot().resolve(ICON_COMPONENT));
        assertThat(source)
                .as("Icon.vue 必须保留 local 分支（VITE_GLOB_ICONIFY_USE_TYPE === 'local'）")
                .contains("VITE_GLOB_ICONIFY_USE_TYPE === 'local'");
        assertThat(source)
                .as("Icon.vue 的 local 分支必须从 @purge-icons/generated 取离线图标集")
                .contains("@purge-icons/generated");
    }

    // ================================================================= 断言 3：数据库菜单图标必须能被离线收集

    @Test
    @DisplayName("数据库里的每一个菜单图标都必须能被离线图标集收集到（白名单 ∪ 前端源码）")
    void everyDbMenuIconMustBeCollectableOffline() {
        Set<String> dbIcons = dbMenuIcons();
        Set<String> safelist = safelistIcons();
        Set<String> sourceTokens = sourceIconTokens();

        List<String> problems = new ArrayList<>();
        for (String icon : dbIcons) {
            if (!safelist.contains(icon) && !sourceTokens.contains(icon)) {
                problems.add(icon + "（只存在于数据库，源码与白名单里都没有 → 离线模式下必然是空心圆点）");
            }
        }
        assertThat(problems)
                .as("数据库菜单图标必须出现在 src/components/Icon/data/menu-icons.ts 白名单或前端源码里")
                .isEmpty();
    }

    // ================================================================= 断言 4：白名单不许积灰

    @Test
    @DisplayName("白名单里不许留已经没人用的图标名（清单要能收敛）")
    void safelistMustNotKeepStaleIcons() {
        Set<String> dbIcons = dbMenuIcons();
        Set<String> safelist = safelistIcons();
        List<String> stale = new ArrayList<>();
        for (String icon : safelist) {
            if (!dbIcons.contains(icon)) {
                stale.add(icon);
            }
        }
        assertThat(stale)
                .as("白名单里这些图标已经不是任何菜单在用了（sys_permission.icon 里查不到），应清理掉")
                .isEmpty();
    }

    // ================================================================= 断言 5：注释不许藏图标名

    @Test
    @DisplayName("白名单文件里不许出现「只在注释里」的图标记号（注释提及会让离线收集断言假绿）")
    void safelistMustNotHideIconTokensInComments() {
        String raw = RedLineScanner.read(RedLineScanner.frontendRoot().resolve(MENU_ICON_SAFELIST));
        Set<String> rawTokens = new LinkedHashSet<>();
        Set<String> codeTokens = new LinkedHashSet<>();
        Matcher rawMatcher = ICON_TOKEN.matcher(raw);
        while (rawMatcher.find()) {
            rawTokens.add(rawMatcher.group());
        }
        Matcher codeMatcher = ICON_TOKEN.matcher(stripComments(raw));
        while (codeMatcher.find()) {
            codeTokens.add(codeMatcher.group());
        }
        List<String> onlyInComments = new ArrayList<>(rawTokens);
        onlyInComments.removeAll(codeTokens);
        assertThat(onlyInComments)
                .as("这些图标名只出现在白名单文件的注释里，代码里没有 → 删掉白名单条目时断言会假绿")
                .isEmpty();
    }

    // ================================================================= 取数

    /** 前端工程根（前端 src 的上一级） */
    private static Path frontendProjectRoot() {
        return RedLineScanner.frontendRoot().getParent();
    }

    /** 数据库里全部在用的菜单图标 */
    private Set<String> dbMenuIcons() {
        List<String> list = jdbc.queryForList(
                "SELECT DISTINCT icon FROM sys_permission "
                        + "WHERE icon IS NOT NULL AND TRIM(icon) <> ''", String.class);
        return new LinkedHashSet<>(list);
    }

    /** 白名单文件里登记的图标名（<b>先剥注释</b>，注释里写的图标名不算登记） */
    private Set<String> safelistIcons() {
        String source = stripComments(RedLineScanner.read(RedLineScanner.frontendRoot().resolve(MENU_ICON_SAFELIST)));
        Set<String> out = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("'([a-z][a-z0-9-]*:[a-z0-9][a-z0-9-]*)'").matcher(source);
        while (matcher.find()) {
            out.add(matcher.group(1));
        }
        return out;
    }

    /**
     * 前端源码里出现过的全部图标记号。
     *
     * <p>扫描范围与 {@code vite-plugin-purge-icons} 的 {@code content} 配置对齐
     * （工程根下的 html/pug/vue/js/ts，排除 node_modules）——
     * 扫描面比白名单大，所以「源码里出现过」是比「白名单里登记过」更宽松的兜底。</p>
     *
     * <p><b>必须先剥注释</b>：注释里顺口提一句图标名（例如「实测缺 ant-design:team」），
     * 会被扫描器当成「源码里出现过」→ 白名单里删掉这个图标，断言依然绿 ——
     * 这就是本项目既有纪律「字面量扫描类断言必须先剥注释，否则假绿」，
     * 本类在 2026-11-02 的故障注入里被真实踩中过一次。</p>
     */
    private Set<String> sourceIconTokens() {
        Set<String> out = new LinkedHashSet<>();
        Path root = frontendProjectRoot();
        if (!Files.isDirectory(root)) {
            return out;
        }
        try (Stream<Path> walk = Files.walk(root, 8)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                String path = file.toString().replace('\\', '/');
                if (path.contains("/node_modules/") || path.contains("/.git/") || path.contains("/dist/")) {
                    continue;
                }
                String name = file.getFileName().toString();
                boolean matched = SCANNED_SUFFIXES.stream().anyMatch(name::endsWith);
                if (!matched) {
                    continue;
                }
                try {
                    String content = stripComments(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
                    Matcher matcher = ICON_TOKEN.matcher(content);
                    while (matcher.find()) {
                        out.add(matcher.group());
                    }
                } catch (IOException ignored) {
                    // 读不动的文件跳过，不影响其余扫描
                }
            }
        } catch (IOException ignored) {
            // 目录不可读时返回已扫到的部分
        }
        return out;
    }

    /**
     * 剥掉注释，只留真正的代码。
     *
     * <p>支持四种注释：块注释 {@code /* ... *}{@code /}、HTML 注释 {@code <!-- ... -->}、
     * 整行 {@code //}、行尾 {@code //}。行尾 {@code //} 的判定会排除 {@code ://}
     * （避免把 URL 里的双斜杠当注释起点）。</p>
     *
     * <p>剥掉的注释会替换成一个空格，避免把注释前后的代码粘成一个新词。</p>
     *
     * @param source 原始文本
     * @return 去掉注释后的文本
     */
    private static String stripComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        int n = source.length();
        while (i < n) {
            char c = source.charAt(i);
            char next = (i + 1 < n) ? source.charAt(i + 1) : '\0';
            // HTML 注释
            if (c == '<' && next == '!' && source.startsWith("<!--", i)) {
                int end = source.indexOf("-->", i + 4);
                if (end < 0) {
                    break;
                }
                i = end + 3;
                out.append(' ');
                continue;
            }
            // 块注释
            if (c == '/' && next == '*') {
                int end = source.indexOf("*/", i + 2);
                if (end < 0) {
                    break;
                }
                i = end + 2;
                out.append(' ');
                continue;
            }
            // 行注释（排除 URL 里的 ://）
            if (c == '/' && next == '/' && (out.length() == 0 || out.charAt(out.length() - 1) != ':')) {
                int end = source.indexOf('\n', i);
                if (end < 0) {
                    break;
                }
                i = end;
                out.append(' ');
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /**
     * 取环境文件里某个键的值。
     *
     * <p><b>必须先剥注释</b>：{@code .env} 里的 {@code #} 注释行与
     * {@code update-begin/update-end} 痕迹行都可能被误当成配置，
     * 不剥注释就会出现「注释里有 local、真实配置却是 online」这种假绿。</p>
     *
     * @param file 环境文件
     * @param key  键名
     * @return 值；没有配置时返回 null
     */
    private static String envValue(Path file, String key) {
        for (String raw : RedLineScanner.read(file).split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            if (line.substring(0, eq).trim().equals(key)) {
                return line.substring(eq + 1).trim();
            }
        }
        return null;
    }
}
