package org.jeecg.modules.study.sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
 * **「一级菜单看得见，但下级菜单一个都不显示」**的可执行断言套件。
 *
 * <p><b>真实踩过的坑（2026-11-02，管理端反馈）</b>：
 * 管理端左侧栏出现了「学习助手」「家长端」两个一级菜单，点开却是空的，
 * 7 个 / 3 个下级菜单一个都不渲染，而后端返回的 JSON 里 children 明明是全的。</p>
 *
 * <p><b>根因在「数据侧触发 + 前端规则」这一对上</b>：</p>
 * <ol>
 *   <li>前端 {@code BasicSubMenuItem.vue} 的 {@code menuHasChildren()} 判定
 *       「这个节点要不要渲染成可展开的 SubMenu」，最后一项是
 *       {@code && checkChildrenHidden(menuTreeItem)}；</li>
 *   <li>而 {@code compUtils.ts} 的 {@code checkChildrenHidden()} 第一句就是
 *       {@code if (alwaysShow) { return false; }}；</li>
 *   <li>两个函数用 <b>&amp;&amp;</b> 串联 ⇒ <b>alwaysShow 为真时 menuHasChildren 恒为 false</b>，
 *       该节点被当成「没有子菜单的叶子节点」，children 数组里的下级菜单<b>一个都不会渲染</b>；</li>
 *   <li>alwaysShow 来自 {@code sys_permission.always_show}。
 *       07 号种子给 st_menu_study / st_menu_parent 写的是 {@code always_show = 1}，
 *       正好踩中这条规则。<b>全库实测：上游 12 个一级菜单的 always_show 全部是 0</b>。</li>
 * </ol>
 *
 * <p>本类把这条约束落成断言，并顺带守住另外两件同源的事：</p>
 * <ol>
 *   <li><b>凡是「有可见下级菜单」的节点，always_show 必须是 0</b>（否则下级菜单不渲染）；</li>
 *   <li>两个 study 一级菜单的 always_show 必须是 0，且增量脚本
 *       {@code 21-fix-study-menu-always-show.sql} 必须覆盖它们；</li>
 *   <li>admin 角色必须拿到<b>全部</b> study 菜单与按钮（否则侧边栏根本没有这两个一级菜单）；</li>
 *   <li>上游那条 {@code alwaysShow ⇒ 子菜单不渲染} 的耦合规则必须原样存在
 *       —— 它一旦被上游改掉，本类的断言理由就要重写（这是提醒，不是缺陷）。</li>
 * </ol>
 *
 * @author jeecg-study
 */
class StudyMenuRenderRuleTest extends StudyTestSupport {

    /** 上游：判定「子菜单是否全部隐藏」的工具函数（相对前端 src） */
    private static final String COMP_UTILS = "utils/common/compUtils.ts";

    /** 上游：渲染一级菜单项的组件（相对前端 src） */
    private static final String SUB_MENU_ITEM = "components/Menu/src/components/BasicSubMenuItem.vue";

    /** 增量脚本：修正 study 一级菜单聚合路由标记 */
    private static final String FIX_ALWAYS_SHOW = "21-fix-study-menu-always-show.sql";

    /** 增量脚本：给 admin 授权 study 菜单与按钮 */
    private static final String SEED_ADMIN_MENU = "19-seed-admin-study-menu.sql";

    /** 本项目的两个一级菜单 */
    private static final Set<String> STUDY_TOP_MENUS = Set.of("st_menu_study", "st_menu_parent");

    /** 上游 checkChildrenHidden：alwaysShow 为真就直接 return false */
    private static final Pattern RULE_ALWAYS_SHOW_SHORT_CIRCUIT =
            Pattern.compile("alwaysShow\\s*;[^}]*if\\s*\\(\\s*alwaysShow\\s*\\)\\s*\\{\\s*return\\s+false");

    /** 上游 menuHasChildren：末项与 checkChildrenHidden 用 && 串联 */
    private static final Pattern RULE_MENU_HAS_CHILDREN_AND =
            Pattern.compile("checkChildrenHidden\\(menuTreeItem\\)");

    // ================================================================= 扫描有效性

    @Test
    @DisplayName("扫描有效性：上游规则文件、增量脚本、真实库菜单都必须存在且非空")
    void sourceFilesAndMenusMustExist() {
        Path compUtils = RedLineScanner.frontendRoot().resolve(COMP_UTILS);
        Path subMenuItem = RedLineScanner.frontendRoot().resolve(SUB_MENU_ITEM);
        Path fixScript = sqlScriptsDir().resolve(FIX_ALWAYS_SHOW);
        Path seedScript = sqlScriptsDir().resolve(SEED_ADMIN_MENU);

        assertThat(Files.isRegularFile(compUtils)).as("上游规则文件必须存在：" + COMP_UTILS).isTrue();
        assertThat(Files.isRegularFile(subMenuItem)).as("上游菜单组件必须存在：" + SUB_MENU_ITEM).isTrue();
        assertThat(Files.isRegularFile(fixScript)).as("增量脚本必须存在：" + FIX_ALWAYS_SHOW).isTrue();
        assertThat(Files.isRegularFile(seedScript)).as("增量脚本必须存在：" + SEED_ADMIN_MENU).isTrue();

        assertThat(RedLineScanner.read(compUtils).trim()).as("扫描有效性：" + COMP_UTILS + " 内容非空").isNotEmpty();
        assertThat(RedLineScanner.read(subMenuItem).trim()).as("扫描有效性：" + SUB_MENU_ITEM + " 内容非空").isNotEmpty();
        assertThat(RedLineScanner.read(fixScript).trim()).as("扫描有效性：" + FIX_ALWAYS_SHOW + " 内容非空").isNotEmpty();
        assertThat(RedLineScanner.read(seedScript).trim()).as("扫描有效性：" + SEED_ADMIN_MENU + " 内容非空").isNotEmpty();

        // 上游规则必须真的能被解析出来，否则下面的断言是在测空气
        assertThat(RULE_ALWAYS_SHOW_SHORT_CIRCUIT.matcher(RedLineScanner.read(compUtils)).find())
                .as("扫描有效性：必须能从 " + COMP_UTILS + " 解析出「alwaysShow 为真就 return false」的短路规则")
                .isTrue();
        assertThat(RULE_MENU_HAS_CHILDREN_AND.matcher(RedLineScanner.read(subMenuItem)).find())
                .as("扫描有效性：必须能从 " + SUB_MENU_ITEM + " 解析出 menuHasChildren 与 checkChildrenHidden 的串联调用")
                .isTrue();

        // 真实库里的菜单必须查得到
        assertThat(menuRows()).as("扫描有效性：真实库里必须查得到菜单行").isNotEmpty();
        assertThat(studyTopMenuIds()).as("扫描有效性：真实库里必须查得到两个 study 一级菜单").isNotEmpty();
    }

    // ================================================================= 断言 1：上游耦合规则仍在

    @Test
    @DisplayName("上游规则：alwaysShow 为真时 checkChildrenHidden 直接返回 false（子菜单因此不渲染）")
    void upstreamAlwaysShowShortCircuitMustStillExist() {
        String compUtils = RedLineScanner.read(RedLineScanner.frontendRoot().resolve(COMP_UTILS));
        String subMenuItem = RedLineScanner.read(RedLineScanner.frontendRoot().resolve(SUB_MENU_ITEM));

        assertThat(RULE_ALWAYS_SHOW_SHORT_CIRCUIT.matcher(compUtils).find())
                .as("compUtils.ts 的 checkChildrenHidden 必须保留「alwaysShow 为真 → return false」的短路，"
                        + "这是「下级菜单不显示」能被复现的前提")
                .isTrue();

        // menuHasChildren 的最后一个条件必须是 checkChildrenHidden —— 用 && 串联，
        // 短路返回 false 会让整个判定变成 false。
        Matcher matcher = RULE_MENU_HAS_CHILDREN_AND.matcher(subMenuItem);
        assertThat(matcher.find())
                .as("BasicSubMenuItem.vue 的 menuHasChildren 必须调用 checkChildrenHidden")
                .isTrue();
        int at = matcher.start();
        String window = subMenuItem.substring(Math.max(0, at - 400), at);
        assertThat(window.contains("&&"))
                .as("menuHasChildren 里 checkChildrenHidden 必须以 && 串联（短路返回 false 会让判定整体为 false）")
                .isTrue();
    }

    // ================================================================= 断言 2：有下级菜单的节点不得开聚合路由

    @Test
    @DisplayName("凡是「有可见下级菜单」的节点，always_show 必须是 0，否则下级菜单不渲染")
    void everyParentMenuWithVisibleChildrenMustNotSetAlwaysShow() {
        List<Map<String, Object>> rows = menuRows();

        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (Map<String, Object> row : rows) {
            String id = str(row.get("id"));
            String name = str(row.get("name"));
            int alwaysShow = num(row.get("always_show"));
            int visibleChildren = num(row.get("visible_children"));
            if (visibleChildren <= 0) {
                continue;
            }
            checked++;
            if (alwaysShow != 0) {
                problems.add(id + "（" + name + "）always_show=" + alwaysShow
                        + " 但有 " + visibleChildren + " 个可见下级菜单 → 前端会把它当叶子节点，下级菜单不显示");
            }
        }

        assertThat(checked)
                .as("扫描有效性：必须至少有一个「有可见下级菜单」的节点被检查到")
                .isGreaterThan(0);
        assertThat(problems)
                .as("有可见下级菜单的节点一律不许开 always_show（聚合路由标记与 children 在 Vue3 菜单渲染里互斥）")
                .isEmpty();
    }

    // ================================================================= 断言 3：study 两个一级菜单的 always_show

    @Test
    @DisplayName("学习助手 / 家长端 两个一级菜单的 always_show 必须是 0，且增量脚本必须覆盖它们")
    void studyTopMenusMustNotUseAggregateRouteFlag() {
        Set<String> found = studyTopMenuIds();
        assertThat(found).as("两个 study 一级菜单都必须在真实库里存在").containsAll(STUDY_TOP_MENUS);

        for (Map<String, Object> row : menuRows()) {
            String id = str(row.get("id"));
            if (!STUDY_TOP_MENUS.contains(id)) {
                continue;
            }
            assertThat(num(row.get("always_show")))
                    .as(id + "（" + str(row.get("name")) + "）的 always_show 必须是 0，"
                            + "否则 Vue3 菜单把它渲染成叶子节点，下级菜单一个都不显示")
                    .isEqualTo(0);
        }

        // 增量脚本必须真的覆盖这两个 id，且是幂等写法
        String script = RedLineScanner.read(sqlScriptsDir().resolve(FIX_ALWAYS_SHOW));
        for (String id : STUDY_TOP_MENUS) {
            assertThat(script)
                    .as("增量脚本 " + FIX_ALWAYS_SHOW + " 必须覆盖 " + id)
                    .contains(id);
        }
        assertThat(script).as("增量脚本必须写成幂等形式（带 WHERE 条件）").contains("WHERE");
    }

    // ================================================================= 断言 4：admin 必须拿到全部 study 菜单与按钮

    @Test
    @DisplayName("admin 角色必须被授权全部 study 菜单与按钮，否则侧边栏根本没有这两个一级菜单")
    void adminRoleMustOwnAllStudyMenusAndButtons() {
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_permission WHERE id LIKE 'st%'", Integer.class);
        Integer granted = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_role_permission rp "
                        + "JOIN sys_role r ON r.id = rp.role_id "
                        + "JOIN sys_permission p ON p.id = rp.permission_id "
                        + "WHERE r.role_code = 'admin' AND p.id LIKE 'st%'", Integer.class);

        assertThat(total).as("真实库里必须存在 study 菜单与按钮").isGreaterThan(0);
        assertThat(granted)
                .as("admin 角色必须拿到全部 " + total + " 条 study 菜单与按钮，实际只有 " + granted
                        + " 条 —— 后端 queryByUser 纯按角色授权取菜单，少一条侧边栏就少一项")
                .isEqualTo(total);

        // 增量脚本必须按前缀覆盖全部（而不是写死当时那批 id），且幂等、无破坏性语句
        String script = RedLineScanner.read(sqlScriptsDir().resolve(SEED_ADMIN_MENU));
        assertThat(script).as("19 号脚本必须按 st_menu% / st_btn% 前缀覆盖全部 study 权限").contains("st\\_menu%").contains("st\\_btn%");
        assertThat(script).as("19 号脚本必须写成幂等形式").contains("WHERE NOT EXISTS");
        assertThat(script).as("19 号脚本不得清空授权表").doesNotContain("TRUNCATE").doesNotContain("DELETE FROM `sys_role_permission`");
    }

    // ================================================================= 取数

    /** 真实库里的菜单行（含可见下级菜单数量） */
    private List<Map<String, Object>> menuRows() {
        return jdbc.queryForList(
                "SELECT p.id, p.name, p.always_show, p.menu_type, "
                        + "(SELECT COUNT(*) FROM sys_permission c WHERE c.parent_id = p.id "
                        + " AND c.menu_type IN (0, 1) AND c.del_flag = 0) AS children, "
                        + "(SELECT COUNT(*) FROM sys_permission c WHERE c.parent_id = p.id "
                        + " AND c.menu_type IN (0, 1) AND c.del_flag = 0 "
                        + " AND (c.hidden IS NULL OR c.hidden <> 1)) AS visible_children "
                        + "FROM sys_permission p "
                        + "WHERE p.menu_type IN (0, 1) AND p.del_flag = 0");
    }

    /** 真实库里两个 study 一级菜单的 id */
    private Set<String> studyTopMenuIds() {
        Set<String> out = new LinkedHashSet<>();
        for (Map<String, Object> row : menuRows()) {
            String id = str(row.get("id"));
            if (STUDY_TOP_MENUS.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    /** SQL 真源目录 */
    private static Path sqlScriptsDir() {
        return RedLineScanner.repoRoot()
                .resolve("02-代码开发")
                .resolve("02-数据库")
                .resolve("03-脚本");
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static int num(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }
}
