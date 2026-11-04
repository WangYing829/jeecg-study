package org.jeecg.modules.study.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.jeecg.modules.study.redline.RedLineScanner;

/**
 * **《接口设计说明书》的每个端点都必须在后端控制器里真实存在**的可执行断言。
 *
 * <p>2026-11-04 全量盘点时发现一个结构性缺口：</p>
 * <blockquote>
 * 《接口设计说明书》共 106 个端点，代码里实存 105 个 —— 但**差的不是 1 个，是 13 个**。
 * 这 13 个端点（如 {@code /mastery/weak-points}、{@code /practice/stats/today}、
 * {@code /parent/notification-preference}）在接口文档里**标注了需求编号**，
 * 于是<b>追溯矩阵按「接口清单里有这个路径」判定为「已覆盖」</b>——
 * 但它查的是<b>文档表格</b>，不是<b>代码路由</b>。
 * 结果就是：<b>文档承诺的接口一个都没实现，追溯矩阵却显示零缺口。</b>
 * </blockquote>
 *
 * <p>本类把这条链路补上：<b>接口文档 → 代码</b> 方向的闭合校验。</p>
 * <p>注意与 {@link StudyInterfacePageTest#frontendUrlsMustExistInBackend()} 的区别：</p>
 * <ul>
 *   <li>那个断言是<b>前端 → 后端</b>：前端写的 URL 必须在控制器里存在；</li>
 *   <li>本类是<b>接口文档 → 后端</b>：接口文档承诺的端点必须在控制器里存在。</li>
 * </ul>
 * 两个方向都要有——只有前端侧校验时，「文档写了但没实现」的端点会从两个方向都漏过去。
 *
 * <p><b>合法豁免</b>只有一种：接口文档里显式标注了
 * 「本接口未实现 / 暂不实现」等说明的条目（见 {@link #DOC_EXEMPT_MARKERS}）。
 * 这类条目必须写明原因，否则不允许豁免。</p>
 *
 * @author jeecg-study
 */
class StudyApiDocEndpointTest {

    /** 《接口设计说明书》源稿（Markdown 源稿，与 HTML 同基名同目录） */
    private static final String API_DOC = "02-代码开发/03-接口文档/01-接口设计说明书.md";

    /** API 根：接口文档里的路径**不带**这个前缀，代码里带 ⇒ 比对前统一补上 */
    private static final String API_ROOT = "/api/v1/study";

    /**
     * 端点表行：{@code | 序号 | 方法 | `路径` | 说明 | 需求 |}
     *
     * <p>要求第 1 列是<b>纯数字序号</b>——这是「端点总表」与第 9 章
     * 「多用户改造对照表」的唯一区别（后者也有「方法 / 路径」列，但**没有序号列**）。
     * 不加这条约束，{@code /parent/feedback} 这种 ADR-030 已判定为旧路径的端点
     * 会被当成「文档承诺但未实现」，把闸门刷红。</p>
     */
    private static final Pattern DOC_ROW = Pattern.compile(
            "^\\|\\s*(\\d+)\\s*\\|\\s*(GET|POST|PUT|DELETE)\\s*\\|\\s*`([^`]+)`\\s*\\|([^|]*)\\|([^|]*)\\|");

    /** 后端：类级 @RequestMapping("...") */
    private static final Pattern CLASS_MAPPING = Pattern.compile("@RequestMapping\\s*\\(\\s*\"([^\"]*)\"");

    /** 后端：方法级 @(Get|Post|Put|Delete)Mapping("...") 或无参 */
    private static final Pattern METHOD_MAPPING = Pattern.compile(
            "@(Get|Post|Put|Delete)Mapping(?:\\s*\\(\\s*\"([^\"]*)\")?");

    /** 占位符归一：{@code {code}} 与 {@code ${code}} 统一成 {@code {}} */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{[^}]*\\}|\\{[^}]*\\}");

    /**
     * 文档里允许豁免「未实现」的说明标记。
     *
     * <p>只认这些明确表述；文档里不能写「已合并到 XXX」这种含糊说法来蒙混——
     * 那会让断言失去意义（这正是本轮 13 个漏网端点的成因）。</p>
     */
    private static final List<String> DOC_EXEMPT_MARKERS = List.of(
            "未实现", "暂不实现", "本期不实现", "尚未实现");

    // ============================================================ 扫描有效性

    @Test
    @DisplayName("扫描有效性：接口设计说明书必须存在，且能解析出端点表")
    void docMustBeParsable() {
        Path doc = RedLineScanner.repoRoot().resolve(API_DOC);
        assertThat(Files.exists(doc)).as("接口设计说明书源稿必须存在：" + API_DOC).isTrue();

        List<String> endpoints = docEndpoints(doc);
        assertThat(endpoints)
                .as("扫描有效性：接口设计说明书里必须能解析出端点表（当前解析到 %d 个）", endpoints.size())
                .isNotEmpty();
        assertThat(endpoints.size())
                .as("接口设计说明书应有 100 个以上的端点，实际只有 %d —— 解析口径坏了", endpoints.size())
                .isGreaterThan(100);
    }

    @Test
    @DisplayName("扫描有效性：后端控制器必须能解析出端点清单")
    void backendMustBeScannable() {
        Set<String> backend = backendEndpoints();
        assertThat(backend)
                .as("扫描有效性：必须能从后端控制器解析出接口清单")
                .isNotEmpty();
        assertThat(backend.size())
                .as("后端端点数应超过 100，实际只有 %d —— 扫描口径坏了", backend.size())
                .isGreaterThan(100);
    }

    // ============================================================ 核心断言

    @Test
    @DisplayName("接口设计说明书的每个端点都必须在后端控制器里真实存在（方法 + 路径双对上）")
    void everyDocumentedEndpointMustExistInBackend() {
        Path doc = RedLineScanner.repoRoot().resolve(API_DOC);
        Set<String> backend = backendEndpoints();

        List<String> missing = new ArrayList<>();
        for (String entry : docEndpoints(doc)) {
            // "GET /api/v1/study/xxx"
            int sp = entry.indexOf(' ');
            String method = entry.substring(0, sp);
            String path = entry.substring(sp + 1);
            if (!backend.contains(entry)) {
                missing.add(method + " " + path);
            }
        }

        assertThat(missing)
                .as("《接口设计说明书》承诺了但后端**没有实现**的端点：\n"
                        + "  · 这些端点在接口文档里标了需求编号 ⇒ 追溯矩阵会判成「已覆盖」，\n"
                        + "  · 但实际没有任何代码 ⇒ 文档与实现脱节（2026-11-04 盘点发现 13 个此类漏网端点）。\n"
                        + "  · 处置二选一：① 补实现；② 在接口文档该行说明里显式写明「未实现」并说明原因。\n"
                        + "  缺失清单：\n%s", String.join("\n", missing))
                .isEmpty();
    }

    @Test
    @DisplayName("接口设计说明书不得出现「已实现」却无代码的端点：豁免必须显式写明未实现及原因")
    void exemptionsMustBeExplicit() {
        Path doc = RedLineScanner.repoRoot().resolve(API_DOC);
        Set<String> backend = backendEndpoints();

        List<String> unmarked = new ArrayList<>();
        for (String[] row : docRows(doc)) {
            String method = row[0];
            String rawPath = row[1];
            String note = row[2];
            String entry = method + " " + normalize(API_ROOT + rawPath);
            if (backend.contains(entry)) {
                continue;
            }
            // 没实现 ⇒ 说明列必须显式写明「未实现」并给出原因
            boolean exempt = DOC_EXEMPT_MARKERS.stream().anyMatch(note::contains);
            if (!exempt) {
                unmarked.add(method + " " + normalize(API_ROOT + rawPath) + "  （说明列：" + note.trim() + "）");
            }
        }

        assertThat(unmarked)
                .as("以下端点在接口文档里存在、后端却没有实现，且**说明列没有显式写明未实现及原因**：\n%s\n"
                        + "  · 追溯矩阵按「接口清单里有这个路径」判为「已覆盖」⇒ 缺口被掩盖。\n"
                        + "  · 请补实现，或在说明列写明「未实现（原因：…）」。",
                        String.join("\n", unmarked))
                .isEmpty();
    }

    @Test
    @DisplayName("接口设计说明书的端点不得重复（同方法同路径只能有一条）")
    void documentedEndpointsMustBeUnique() {
        Path doc = RedLineScanner.repoRoot().resolve(API_DOC);
        List<String> all = docEndpoints(doc);
        Set<String> distinct = new LinkedHashSet<>(all);
        assertThat(all.size())
                .as("接口设计说明书里有重复的端点条目：重复数 %d。重复条目会让「一个端点对应几条需求」无法判断。",
                        all.size() - distinct.size())
                .isEqualTo(distinct.size());
    }

    // ============================================================ 解析

    /**
     * 端点表原始行：{方法, 路径, 说明}
     *
     * <p>对应 {@link #DOC_ROW} 的分组：1=序号、2=方法、3=路径、4=说明、5=需求。
     * 说明列取 group(4)，需求列 group(5) 本断言不消费（豁免只看说明列是否显式写明未实现）。</p>
     */
    private static List<String[]> docRows(Path doc) {
        List<String[]> out = new ArrayList<>();
        for (String line : RedLineScanner.read(doc).split("\\R")) {
            Matcher m = DOC_ROW.matcher(line);
            if (m.find()) {
                out.add(new String[] {
                        m.group(2).toUpperCase(),
                        m.group(3),
                        m.group(4) == null ? "" : m.group(4).trim()});
            }
        }
        return out;
    }

    /** 端点表 → 归一化后的端点清单："GET /api/v1/study/xxx" */
    private static List<String> docEndpoints(Path doc) {
        List<String> out = new ArrayList<>();
        for (String[] row : docRows(doc)) {
            out.add(row[0] + " " + normalize(API_ROOT + row[1]));
        }
        return out;
    }

    /** 后端控制器 → 端点清单 */
    private static Set<String> backendEndpoints() {
        Set<String> out = new LinkedHashSet<>();
        for (Path file : RedLineScanner.javaSources()) {
            String name = file.getFileName().toString();
            if (!name.endsWith("Controller.java")) {
                continue;
            }
            String source = RedLineScanner.read(file).replaceAll("(?s)/\\*.*?\\*/", " ");

            Matcher classMatcher = CLASS_MAPPING.matcher(source);
            String root = "";
            if (classMatcher.find()) {
                root = normalize(classMatcher.group(1));
            }

            Matcher methodMatcher = METHOD_MAPPING.matcher(source);
            while (methodMatcher.find()) {
                String method = methodMatcher.group(1).toUpperCase();
                String path = methodMatcher.group(2) == null ? "" : normalize(methodMatcher.group(2));
                out.add(method + " " + join(root, path));
            }
        }
        return out;
    }

    // ============================================================ 工具

    /**
     * 路径归一：占位符统一成 {@code {}}，补前导斜杠，去掉结尾斜杠。
     *
     * <p><b>空串必须原样返回 {@code ""}</b>：方法级映射可以没有路径
     * （如 {@code @PostMapping} + 类级 {@code @RequestMapping("/api/v1/study/family")}），
     * 此时方法路径是「空」而不是「根」。若把空串补成 {@code "/"}，
     * 拼出的就是 {@code /api/v1/study/family/}（多一个尾斜杠），
     * 与文档里的 {@code /api/v1/study/family} 对不上 ——
     * 会凭空造出 7 条假阳性缺失（context / textbook-versions / family×3 / children×2）。</p>
     */
    private static String normalize(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        String p = PLACEHOLDER.matcher(path).replaceAll("{}");
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        // 去掉结尾斜杠（/xxx/ 与 /xxx 是同一个端点）
        if (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    private static String join(String root, String path) {
        if (root.isEmpty()) {
            return path;
        }
        if (path.isEmpty()) {
            return root;
        }
        return (root + path).replaceAll("/{2,}", "/");
    }
}
