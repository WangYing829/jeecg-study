package org.jeecg.modules.study.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.jeecg.modules.study.redline.RedLineScanner;
import org.jeecg.modules.study.support.StudyTestSupport;

/**
 * **21 个规划页（学习端 18 + 家长端 2 + 孩子选择器组件 1）的「菜单 ↔ 页面 ↔ 组件名」闭环断言**。
 *
 * <p>2026-11-04 盘点发现：《前端页面清单》§2.2 规划 40 个页面，§2.1 只上线了 23 个，
 * 真实差集 <b>21 个</b>。这批页面此前是「规划态」——<b>文档里有、前端没有</b>。</p>
 *
 * <p>页面补齐之后，最容易出问题的不是「文件写没写」，而是<b>写完了却进不去</b>：</p>
 * <ul>
 *   <li>页面文件在、但库里没有菜单 ⇒ 界面上永远找不到入口；</li>
 *   <li>菜单的 {@code component} 写错路径 ⇒ 路由注册成功但页面 404；</li>
 *   <li>{@code component_name} 与页面里声明的组件名不一致 ⇒
 *       <b>keep-alive 缓存失效</b>，且动态路由的 route name 对不上；</li>
 *   <li>{@code sort_no} 与同父下既有菜单撞号 ⇒ 侧边栏顺序不确定。</li>
 * </ul>
 *
 * <p>本类把这四条全部落成可执行断言，并额外守住：</p>
 * <ol>
 *   <li>带 {@code :param} 的路由必须 {@code hidden = 1}（否则侧边栏出现字面量 ":code" 死链）；</li>
 *   <li>学习端页面挂 {@code st_menu_study}、家长端页面挂 {@code st_menu_parent}；</li>
 *   <li>角色授权：学习端 → {@code study_kid} + {@code admin}；家长端 → {@code study_parent} + {@code admin}。</li>
 * </ol>
 *
 * <p><b>本类跑在真实 {@code jeecg-study} 库上</b>（继承 {@link StudyTestSupport}），
 * 因此「菜单已落库且授权正确」是真的验过，不是静态文本检查。
 * 换环境跑测试前请先执行
 * {@code 02-代码开发/02-数据库/03-脚本/24-seed-study-planned-pages-menu.sql}。</p>
 *
 * @author jeecg-study
 */
class StudyPlannedPageMenuTest extends StudyTestSupport {

    // ================================================================= 清单

    /**
     * 学习端 18 页：菜单 id → 期望的 {@code component}（相对 {@code src/views}，不带 {@code .vue}）。
     *
     * <p>顺序与《前端页面清单》§2.2 的页面顺序一致，便于人工核对。</p>
     */
    private static final Map<String, String> KID_MENUS = kidMenus();

    /** 家长端 2 页（原为总览页内区块，本轮拆为独立页） */
    private static final Map<String, String> PARENT_MENUS = Map.of(
            "st_menu_parent_weekly", "study/parent/weekly/index",
            "st_menu_parent_weak", "study/parent/weakpoint/index");

    /** 孩子选择器（组件，**不是页面 ⇒ 不建菜单**，但文件必须存在） */
    private static final String CHILD_SWITCHER = "components/study/ChildSwitcher.vue";

    /** 菜单 SQL 真源（相对仓库根） */
    private static final String MENU_SQL = "02-代码开发/02-数据库/03-脚本/24-seed-study-planned-pages-menu.sql";

    /** 学习端页面必须挂的父菜单 */
    private static final String KID_PARENT = "st_menu_study";

    /** 家长端页面必须挂的父菜单 */
    private static final String PARENT_PARENT = "st_menu_parent";

    /**
     * Vue 组件名声明：{@code defineComponent({ name: '...' })} 或
     * {@code defineOptions({ name: '...' })}。
     */
    private static final Pattern VUE_COMPONENT_NAME = Pattern.compile(
            "(?:defineComponent|defineOptions)\\s*\\(\\s*\\{[\\s\\S]{0,400}?name\\s*:\\s*['\"]([^'\"]+)['\"]");

    /** 兜底：任何 {@code name: 'StudyXxx'} 形式的声明 */
    private static final Pattern VUE_COMPONENT_NAME_FALLBACK = Pattern.compile(
            "name\\s*:\\s*['\"](Study[A-Za-z0-9_]+)['\"]");

    // ================================================================= 断言 0：扫描有效性

    @Test
    @DisplayName("扫描有效性：21 个规划页文件必须存在且非空（含 ChildSwitcher 组件）")
    void plannedPageFilesMustExist() {
        Map<String, String> all = new LinkedHashMap<>();
        all.putAll(KID_MENUS);
        all.putAll(PARENT_MENUS);
        assertThat(all)
                .as("扫描有效性：规划页清单应有 20 个页面菜单（学习端 18 + 家长端 2）")
                .hasSize(20);

        for (Map.Entry<String, String> entry : all.entrySet()) {
            Path file = RedLineScanner.frontendRoot().resolve("views").resolve(entry.getValue() + ".vue");
            assertThat(Files.isRegularFile(file))
                    .as("规划页文件必须存在：" + entry.getKey() + " → " + file)
                    .isTrue();
            assertThat(RedLineScanner.read(file).trim())
                    .as("扫描有效性：规划页文件内容非空：" + entry.getValue())
                    .isNotEmpty();
        }

        Path switcher = RedLineScanner.frontendRoot().resolve(CHILD_SWITCHER);
        assertThat(Files.isRegularFile(switcher))
                .as("孩子选择器组件必须存在：" + CHILD_SWITCHER)
                .isTrue();
    }

    @Test
    @DisplayName("扫描有效性：菜单 SQL 真源必须存在，且覆盖全部 20 条新菜单")
    void menuSqlSourceMustCoverAllMenus() {
        Path sql = RedLineScanner.repoRoot().resolve(MENU_SQL);
        assertThat(Files.isRegularFile(sql)).as("菜单 SQL 真源必须存在：" + MENU_SQL).isTrue();
        String content = RedLineScanner.read(sql);

        Map<String, String> all = new LinkedHashMap<>();
        all.putAll(KID_MENUS);
        all.putAll(PARENT_MENUS);
        for (String menuId : all.keySet()) {
            assertThat(content)
                    .as("菜单 SQL 真源里必须有这条菜单（否则「改了脚本没落库」无从查证）：" + menuId)
                    .contains(menuId);
        }
    }

    // ================================================================= 断言 1：菜单落库

    @Test
    @DisplayName("20 条规划页菜单必须已落库，且 component 指向真实页面文件")
    void plannedMenusMustBeSeededAndResolvable() {
        Map<String, String> all = new LinkedHashMap<>();
        all.putAll(KID_MENUS);
        all.putAll(PARENT_MENUS);

        for (Map.Entry<String, String> entry : all.entrySet()) {
            Map<String, Object> row = queryOne(
                    "SELECT id, url, component, component_name, parent_id, sort_no, hidden, status, del_flag "
                            + "FROM sys_permission WHERE id = ?", entry.getKey());
            assertThat(row)
                    .as("菜单必须已落库（没落库 = 页面写了却进不去）：" + entry.getKey())
                    .isNotNull();

            assertThat(String.valueOf(row.get("component")))
                    .as("菜单 " + entry.getKey() + " 的 component 必须指向约定的页面")
                    .isEqualTo(entry.getValue());

            Path page = RedLineScanner.frontendRoot().resolve("views").resolve(entry.getValue() + ".vue");
            assertThat(Files.isRegularFile(page))
                    .as("菜单 " + entry.getKey() + " 指向的页面文件不存在：" + page)
                    .isTrue();

            assertThat(String.valueOf(row.get("status")))
                    .as("菜单必须启用：" + entry.getKey()).isEqualTo("1");
            assertThat(String.valueOf(row.get("del_flag")))
                    .as("菜单不能是已删除：" + entry.getKey()).isEqualTo("0");
            assertThat(String.valueOf(row.get("component_name")))
                    .as("菜单必须有 component_name（vue-router 的 route name）：" + entry.getKey())
                    .isNotBlank();
        }
    }

    // ================================================================= 断言 2：组件名闭环

    @Test
    @DisplayName("菜单的 component_name 必须等于页面里声明的 Vue 组件名（keep-alive 与 route name 都靠它）")
    void componentNameMustMatchVueComponentName() {
        Map<String, String> all = new LinkedHashMap<>();
        all.putAll(KID_MENUS);
        all.putAll(PARENT_MENUS);

        List<String> mismatched = new ArrayList<>();
        for (Map.Entry<String, String> entry : all.entrySet()) {
            Map<String, Object> row = queryOne(
                    "SELECT component_name FROM sys_permission WHERE id = ?", entry.getKey());
            if (row == null) {
                mismatched.add(entry.getKey() + "：菜单未落库");
                continue;
            }
            String dbName = String.valueOf(row.get("component_name"));

            Path page = RedLineScanner.frontendRoot().resolve("views").resolve(entry.getValue() + ".vue");
            if (!Files.isRegularFile(page)) {
                mismatched.add(entry.getKey() + "：页面文件不存在 " + entry.getValue() + ".vue");
                continue;
            }
            String vueName = vueComponentName(RedLineScanner.read(page));
            if (vueName == null) {
                mismatched.add(entry.getKey() + "：" + entry.getValue() + ".vue 里读不到组件名声明");
                continue;
            }
            if (!dbName.equals(vueName)) {
                mismatched.add(entry.getKey() + "：库内 component_name=" + dbName
                        + "，页面声明 name=" + vueName);
            }
        }

        assertThat(mismatched)
                .as("菜单 component_name 与页面组件名不一致：\n  · component_name 是 vue-router 的 route name，\n"
                        + "  · 也是 keep-alive 的 include 依据 ⇒ 不一致会让页面缓存失效、路由对不上。\n"
                        + "  不一致清单：\n%s", String.join("\n", mismatched))
                .isEmpty();
    }

    // ================================================================= 断言 3：不冲突

    @Test
    @DisplayName("规划页菜单的 url 与 component_name 不得重复（重复路由名会让页面互相顶掉）")
    void plannedMenusMustNotCollide() {
        Map<String, String> all = new LinkedHashMap<>();
        all.putAll(KID_MENUS);
        all.putAll(PARENT_MENUS);

        List<Map<String, Object>> rows = queryList(
                "SELECT id, url, component_name FROM sys_permission "
                        + "WHERE id LIKE 'st\\_%' AND menu_type IN ('0','1') "
                        + "AND url IS NOT NULL AND url <> '' AND del_flag = 0");

        Set<String> urls = new LinkedHashSet<>();
        Set<String> names = new LinkedHashSet<>();
        List<String> dupUrl = new ArrayList<>();
        List<String> dupName = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String url = String.valueOf(row.get("url"));
            String name = String.valueOf(row.get("component_name"));
            if (!urls.add(url)) {
                dupUrl.add(url + "（" + row.get("id") + "）");
            }
            if (name != null && !name.isBlank() && !"null".equals(name) && !names.add(name)) {
                dupName.add(name + "（" + row.get("id") + "）");
            }
        }

        assertThat(dupUrl)
                .as("study 域菜单 url 重复（侧边栏会出现两个同名入口）：\n%s", String.join("\n", dupUrl))
                .isEmpty();
        assertThat(dupName)
                .as("study 域菜单 component_name 重复（重复 route name 会让页面互相顶掉）：\n%s",
                        String.join("\n", dupName))
                .isEmpty();

        // 规划页自身也不得与既有菜单撞 url
        List<String> conflict = new ArrayList<>();
        for (String menuId : all.keySet()) {
            Map<String, Object> row = queryOne(
                    "SELECT url FROM sys_permission WHERE id = ?", menuId);
            if (row == null) {
                continue;
            }
            String url = String.valueOf(row.get("url"));
            Integer others = count("SELECT COUNT(*) FROM sys_permission "
                    + "WHERE url = ? AND id <> ? AND del_flag = 0", url, menuId);
            if (others != null && others > 0) {
                conflict.add(menuId + " → " + url + "（还有 " + others + " 条同 url 菜单）");
            }
        }
        assertThat(conflict)
                .as("规划页 url 与既有菜单撞车：\n%s", String.join("\n", conflict))
                .isEmpty();
    }

    @Test
    @DisplayName("规划页的 sort_no 不得与同父下既有菜单撞号（撞号 ⇒ 侧边栏顺序不确定）")
    void sortNoMustNotCollideAmongSiblings() {
        for (String parentId : List.of(KID_PARENT, PARENT_PARENT)) {
            List<Map<String, Object>> rows = queryList(
                    "SELECT id, sort_no FROM sys_permission "
                            + "WHERE parent_id = ? AND del_flag = 0 AND status = '1'", parentId);

            Map<Double, List<String>> bySort = new LinkedHashMap<>();
            for (Map<String, Object> row : rows) {
                Object raw = row.get("sort_no");
                if (raw == null) {
                    continue;
                }
                double sort = Double.parseDouble(String.valueOf(raw));
                bySort.computeIfAbsent(sort, k -> new ArrayList<>()).add(String.valueOf(row.get("id")));
            }

            List<String> collisions = new ArrayList<>();
            for (Map.Entry<Double, List<String>> entry : bySort.entrySet()) {
                if (entry.getValue().size() > 1) {
                    collisions.add("sort_no=" + entry.getKey() + " → " + String.join(", ", entry.getValue()));
                }
            }
            assertThat(collisions)
                    .as("父菜单 " + parentId + " 下存在 sort_no 撞号：\n%s", String.join("\n", collisions))
                    .isEmpty();
        }
    }

    // ================================================================= 断言 4：路由形状

    @Test
    @DisplayName("带 :param 的路由必须 hidden=1（否则侧边栏出现字面量 \":code\" 死链）")
    void paramRoutesMustBeHidden() {
        Map<String, String> all = new LinkedHashMap<>();
        all.putAll(KID_MENUS);
        all.putAll(PARENT_MENUS);

        List<String> leaked = new ArrayList<>();
        for (String menuId : all.keySet()) {
            Map<String, Object> row = queryOne(
                    "SELECT url, hidden FROM sys_permission WHERE id = ?", menuId);
            if (row == null) {
                continue;
            }
            String url = String.valueOf(row.get("url"));
            int hidden = Integer.parseInt(String.valueOf(row.get("hidden")));
            if (url.contains(":") && hidden != 1) {
                leaked.add(menuId + " → " + url + "（hidden=" + hidden + "）");
            }
        }
        assertThat(leaked)
                .as("带参数的路由必须 hidden=1：\n%s", String.join("\n", leaked))
                .isEmpty();
    }

    @Test
    @DisplayName("学习端页面必须挂「学习助手」，家长端页面必须挂「家长端」")
    void parentMenusMustMatchDomain() {
        for (String menuId : KID_MENUS.keySet()) {
            Map<String, Object> row = queryOne(
                    "SELECT parent_id FROM sys_permission WHERE id = ?", menuId);
            if (row != null) {
                assertThat(String.valueOf(row.get("parent_id")))
                        .as("学习端菜单必须挂在 " + KID_PARENT + " 下：" + menuId)
                        .isEqualTo(KID_PARENT);
            }
        }
        for (String menuId : PARENT_MENUS.keySet()) {
            Map<String, Object> row = queryOne(
                    "SELECT parent_id FROM sys_permission WHERE id = ?", menuId);
            if (row != null) {
                assertThat(String.valueOf(row.get("parent_id")))
                        .as("家长端菜单必须挂在 " + PARENT_PARENT + " 下：" + menuId)
                        .isEqualTo(PARENT_PARENT);
            }
        }
    }

    // ================================================================= 断言 5：角色授权

    @Test
    @DisplayName("学习端 18 页授权给 study_kid + admin，家长端 2 页授权给 study_parent + admin")
    void roleGrantsMustMatchDomain() {
        String adminRoleId = roleId("admin");
        String parentRoleId = roleId("study_parent");
        String kidRoleId = roleId("study_kid");
        assertThat(adminRoleId).as("admin 角色必须存在").isNotBlank();
        assertThat(parentRoleId).as("study_parent 角色必须存在").isNotBlank();
        assertThat(kidRoleId).as("study_kid 角色必须存在").isNotBlank();

        for (String menuId : KID_MENUS.keySet()) {
            assertThat(grantedTo(menuId, kidRoleId))
                    .as("学习端菜单必须授权给孩子：" + menuId).isTrue();
            assertThat(grantedTo(menuId, adminRoleId))
                    .as("学习端菜单必须授权给 admin（管理端演示要用）：" + menuId).isTrue();
            assertThat(grantedTo(menuId, parentRoleId))
                    .as("学习端菜单**不应**授权给家长：" + menuId).isFalse();
        }

        for (String menuId : PARENT_MENUS.keySet()) {
            assertThat(grantedTo(menuId, parentRoleId))
                    .as("家长端菜单必须授权给家长：" + menuId).isTrue();
            assertThat(grantedTo(menuId, adminRoleId))
                    .as("家长端菜单必须授权给 admin：" + menuId).isTrue();
            assertThat(grantedTo(menuId, kidRoleId))
                    .as("家长端菜单**不应**授权给孩子：" + menuId).isFalse();
        }
    }

    // ================================================================= 工具

    /** 从页面源码里读出 Vue 组件名；读不到返回 null */
    private static String vueComponentName(String source) {
        Matcher m = VUE_COMPONENT_NAME.matcher(source);
        if (m.find()) {
            return m.group(1);
        }
        Matcher fallback = VUE_COMPONENT_NAME_FALLBACK.matcher(source);
        return fallback.find() ? fallback.group(1) : null;
    }

    /** 取某角色的 id（sys_role 没有 del_flag 列） */
    private String roleId(String roleCode) {
        List<String> list = jdbc.queryForList(
                "SELECT id FROM sys_role WHERE role_code = ?", String.class, roleCode);
        return list.isEmpty() ? "" : list.get(0);
    }

    /** 某条菜单是否授权给了某角色 */
    private boolean grantedTo(String permissionId, String roleId) {
        if (roleId == null || roleId.isBlank()) {
            return false;
        }
        Integer n = count(
                "SELECT COUNT(*) FROM sys_role_permission WHERE permission_id = ? AND role_id = ?",
                permissionId, roleId);
        return n != null && n > 0;
    }

    /**
     * 学习端 18 页清单（保持《前端页面清单》§2.2 的顺序）。
     *
     * <p><b>全部用 {@code .../index.vue}</b>：既符合仓库既有主约定，
     * 也让 {@code StudyMaintainabilityTest} 的 N-09 断言（只扫文件名为 {@code index.vue} 的页面）
     * 能覆盖到这 18 页的每一页 —— 若用 {@code detail.vue} 这类命名，
     * N-09 就<b>扫不到</b>，等于少一道闸门。</p>
     */
    private static Map<String, String> kidMenus() {
        Map<String, String> m = new LinkedHashMap<>();
        // 首页 / 复习 2 个
        m.put("st_menu_kid_home", "study/kid/home/index");
        m.put("st_menu_kid_review", "study/kid/review/index");
        // 错题线 4 个
        m.put("st_menu_kid_error", "study/kid/error/index");
        m.put("st_menu_kid_error_import", "study/kid/error/import/index");
        m.put("st_menu_kid_error_detail", "study/kid/error/detail/index");
        m.put("st_menu_kid_error_stat", "study/kid/error/statistics/index");
        // 单词线 4 个
        m.put("st_menu_kid_word_unit", "study/kid/word/unit/index");
        m.put("st_menu_kid_word_out", "study/kid/word/out/index");
        m.put("st_menu_kid_word_known", "study/kid/word/known-test/index");
        m.put("st_menu_kid_word_stats", "study/kid/word/stats/index");
        // 数学线 5 个
        m.put("st_menu_kid_math", "study/kid/math/index");
        m.put("st_menu_kid_math_mother", "study/kid/math/mother/index");
        m.put("st_menu_kid_math_teach", "study/kid/math/mother/teach/index");
        m.put("st_menu_kid_math_variant", "study/kid/math/variant/index");
        m.put("st_menu_kid_math_oral", "study/kid/math/oral/index");
        // 语法线 3 个
        m.put("st_menu_kid_grammar_detail", "study/kid/grammar/detail/index");
        m.put("st_menu_kid_grammar_transform", "study/kid/grammar/transform/index");
        m.put("st_menu_kid_grammar_collection", "study/kid/grammar/collection/index");
        return m;
    }
}
