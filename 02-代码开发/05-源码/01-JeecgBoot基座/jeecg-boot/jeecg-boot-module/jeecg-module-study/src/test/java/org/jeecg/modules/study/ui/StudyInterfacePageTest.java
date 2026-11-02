package org.jeecg.modules.study.ui;

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
 * **《用户手册》§11.2「尚未上线的界面」全部 4 类**的可执行断言套件。
 *
 * <p>2026-11-02 落地这 4 类界面时，最容易出问题的不是「页面写没写」，而是
 * <b>页面写了却进不去</b>：</p>
 * <ul>
 *   <li>前端路由<b>全部</b>由 {@code sys_permission} 下发（没有静态路由），
 *       页面文件在但库里没有菜单 ⇒ 用户在界面上<b>永远找不到入口</b>；</li>
 *   <li>菜单的 {@code component} 字段写错路径 ⇒ 路由注册成功但页面 404；</li>
 *   <li>角色没授权 ⇒ 菜单树里根本没有这一支；</li>
 *   <li>孩子端登录页是<b>唯一例外</b>：它必须免认证可达，走静态路由，
 *       因此<b>不能</b>同时挂进动态菜单（同一个 path 注册两次，vue-router 行为不可预期）。</li>
 * </ul>
 *
 * <p>本类把这些约束全部落成可执行断言，并额外守住三条红线在管理端页面上的落地：</p>
 * <ol>
 *   <li><b>R-04</b>：管理端 9 个页面只展示<b>全局共享内容</b>，
 *       不得出现任何按孩子统计的行为字段，也不得调用错题池；</li>
 *   <li><b>R-01</b>：词表页面不得索取或展示拼写（拼写就是拼写题的答案）；</li>
 *   <li><b>接口真实性</b>：前端写的每个 URL 都必须在后端控制器里<b>真的存在</b>
 *       （方法 + 路径双对上）——「接口已就绪」这句话必须由代码证明，不能靠印象。</li>
 * </ol>
 *
 * <p><b>本类跑在真实 {@code jeecg-study} 库上</b>（继承 {@link StudyTestSupport}），
 * 因此「菜单已落库且授权正确」是真的验过，不是静态文本检查。
 * 换环境跑测试前请先执行
 * {@code 02-代码开发/02-数据库/03-脚本/22-seed-study-interface-pages-menu.sql}。</p>
 *
 * @author jeecg-study
 */
class StudyInterfacePageTest extends StudyTestSupport {

    // ================================================================= 清单

    /**
     * 本轮新增的 13 个界面（相对前端 {@code src/views}）。
     *
     * <p>孩子端登录页在列，但它是<b>静态路由</b>，不进 sys_permission（见 {@link #kidLoginMustBeStaticRoute()}）。</p>
     */
    private static final List<String> NEW_PAGES = List.of(
            // ① 孩子端登录页（两步）
            "views/study/kid/login/index.vue",
            // ② 家庭与成员管理
            "views/study/parent/family/index.vue",
            // ③ 孩子档案编辑 / 关联管理
            "views/study/parent/child/index.vue",
            "views/study/parent/child/relations.vue",
            // ④ 管理端内容维护页 9 个
            "views/study/manage/context/index.vue",
            "views/study/manage/coordinate/index.vue",
            "views/study/manage/coordinate/detail.vue",
            "views/study/manage/coordinate/history.vue",
            "views/study/manage/question/index.vue",
            "views/study/manage/mother/index.vue",
            "views/study/manage/word/index.vue",
            "views/study/manage/grammar/index.vue",
            "views/study/manage/writing/index.vue");

    /** 配套的 API 层与通用内核（相对前端 src） */
    private static final List<String> NEW_SUPPORT_FILES = List.of(
            "views/study/manage/manageApi.ts",
            "views/study/manage/ManageContentPage.vue",
            "views/study/parent/familyApi.ts",
            "views/study/kid/login/kidAuthApi.ts");

    /** 管理端 9 页（相对前端 src） */
    private static final List<String> MANAGE_PAGES = List.of(
            "views/study/manage/context/index.vue",
            "views/study/manage/coordinate/index.vue",
            "views/study/manage/coordinate/detail.vue",
            "views/study/manage/coordinate/history.vue",
            "views/study/manage/question/index.vue",
            "views/study/manage/mother/index.vue",
            "views/study/manage/word/index.vue",
            "views/study/manage/grammar/index.vue",
            "views/study/manage/writing/index.vue");

    /** 家长端 3 页（相对前端 src） */
    private static final List<String> PARENT_PAGES = List.of(
            "views/study/parent/family/index.vue",
            "views/study/parent/child/index.vue",
            "views/study/parent/child/relations.vue");

    /**
     * 进入 sys_permission 的 12 条菜单（孩子端登录页不在其中）。
     *
     * <p>key = 菜单 id，value = 期望的 {@code component}（相对 src/views，不带 .vue）。</p>
     */
    private static final Map<String, String> EXPECTED_MENUS = Map.ofEntries(
            Map.entry("st_menu_parent_family", "study/parent/family/index"),
            Map.entry("st_menu_parent_child", "study/parent/child/index"),
            Map.entry("st_menu_parent_child_rel", "study/parent/child/relations"),
            Map.entry("st_menu_manage_context", "study/manage/context/index"),
            Map.entry("st_menu_manage_coordinate", "study/manage/coordinate/index"),
            Map.entry("st_menu_manage_point", "study/manage/coordinate/detail"),
            Map.entry("st_menu_manage_point_hist", "study/manage/coordinate/history"),
            Map.entry("st_menu_manage_question", "study/manage/question/index"),
            Map.entry("st_menu_manage_mother", "study/manage/mother/index"),
            Map.entry("st_menu_manage_word", "study/manage/word/index"),
            Map.entry("st_menu_manage_grammar", "study/manage/grammar/index"),
            Map.entry("st_menu_manage_writing", "study/manage/writing/index"));

    /** 管理端菜单（仅 admin 可见） */
    private static final List<String> MANAGE_MENU_IDS = List.of(
            "st_menu_manage",
            "st_menu_manage_context", "st_menu_manage_coordinate",
            "st_menu_manage_point", "st_menu_manage_point_hist",
            "st_menu_manage_question", "st_menu_manage_mother",
            "st_menu_manage_word", "st_menu_manage_grammar", "st_menu_manage_writing");

    /** 家长端菜单（家长 + admin 可见） */
    private static final List<String> PARENT_MENU_IDS = List.of(
            "st_menu_parent_family", "st_menu_parent_child", "st_menu_parent_child_rel");

    /** 孩子端登录页的路由（静态路由，不进 DB） */
    private static final String KID_LOGIN_PATH = "/study/kid/login";

    /** 家长端外壳（相对前端 src） */
    private static final String PARENT_SHELL = "components/study/StudyParentShell.vue";

    /** 底座静态路由表（相对前端 src） */
    private static final String ROUTES_INDEX = "router/routes/index.ts";

    /** 菜单 SQL 真源（相对仓库根） */
    private static final String MENU_SQL = "02-代码开发/02-数据库/03-脚本/22-seed-study-interface-pages-menu.sql";

    /**
     * R-04：管理端页面**不得**出现的按孩子统计的行为字段。
     *
     * <p>这些字段后端确实会返回（{@code MotherListItemVO} / {@code WordListItemVO} 里混着），
     * 但它们是「这个孩子做了多少」的统计——出现在内容维护台里就等于在管理端暴露单个孩子的行为数据。</p>
     */
    private static final List<String> CHILD_BEHAVIOR_FIELDS = List.of(
            "attemptCount", "errorCount", "consecutiveErrors", "mastery",
            "passed", "feedbackCount", "memoryStrength", "occurCount",
            "lastFeedback", "nextReviewAt", "upgradePriority", "decayLambda");

    /**
     * 前端请求：defHttp.<方法> ... url: '...'。
     *
     * <p>中间段用负向回顾禁止再出现 {@code defHttp.}——否则一个没有 url 的调用
     * 会「跨过自己」去匹配下一个调用的 url，方法与路径就配错对了。</p>
     */
    private static final Pattern FRONTEND_CALL = Pattern.compile(
            "defHttp\\.(get|post|put|delete)(?:(?!defHttp\\.)[\\s\\S]){0,160}?url:\\s*(['\"`])([^'\"`]+)\\2");

    /** 后端：类级 @RequestMapping("...") */
    private static final Pattern CLASS_MAPPING = Pattern.compile("@RequestMapping\\s*\\(\\s*\"([^\"]*)\"");

    /** 后端：方法级 @(Get|Post|Put|Delete)Mapping("...") 或无参 */
    private static final Pattern METHOD_MAPPING = Pattern.compile(
            "@(Get|Post|Put|Delete)Mapping(?:\\s*\\(\\s*\"([^\"]*)\")?");

    /** 占位符归一：{code} 与 ${code} 统一成 {} */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{[^}]*\\}|\\{[^}]*\\}");

    // ================================================================= 扫描有效性

    @Test
    @DisplayName("扫描有效性：13 个界面文件、4 个配套文件必须存在且非空")
    void allNewPagesMustExist() {
        for (String page : NEW_PAGES) {
            Path file = RedLineScanner.frontendRoot().resolve(page);
            assertThat(Files.isRegularFile(file)).as("界面文件必须存在：" + page).isTrue();
            assertThat(RedLineScanner.read(file).trim()).as("扫描有效性：界面文件内容非空：" + page).isNotEmpty();
        }
        for (String file : NEW_SUPPORT_FILES) {
            Path path = RedLineScanner.frontendRoot().resolve(file);
            assertThat(Files.isRegularFile(path)).as("配套文件必须存在：" + file).isTrue();
            assertThat(RedLineScanner.read(path).trim()).as("扫描有效性：配套文件内容非空：" + file).isNotEmpty();
        }
    }

    @Test
    @DisplayName("扫描有效性：12 条菜单必须已落库，且 component 对应的页面文件真实存在")
    void menusMustBeSeededAndResolvable() {
        for (Map.Entry<String, String> entry : EXPECTED_MENUS.entrySet()) {
            Map<String, Object> row = queryOne(
                    "SELECT id, url, component, component_name, hidden, status, del_flag "
                            + "FROM sys_permission WHERE id = ?", entry.getKey());
            assertThat(row).as("菜单必须已落库（没落库 = 页面写了却进不去）：" + entry.getKey()).isNotNull();

            assertThat(String.valueOf(row.get("component")))
                    .as("菜单 " + entry.getKey() + " 的 component 必须指向真实页面")
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

    // ================================================================= 断言 1：角色授权

    @Test
    @DisplayName("管理端 9 页只授权给 admin，家长端 3 页授权给家长与 admin，孩子角色一律不给")
    void menuGrantsMustMatchRoles() {
        String adminRoleId = roleId("admin");
        String parentRoleId = roleId("study_parent");
        String kidRoleId = roleId("study_kid");
        assertThat(adminRoleId).as("admin 角色必须存在").isNotBlank();
        assertThat(parentRoleId).as("study_parent 角色必须存在").isNotBlank();
        assertThat(kidRoleId).as("study_kid 角色必须存在").isNotBlank();

        for (String menuId : MANAGE_MENU_IDS) {
            assertThat(grantedTo(menuId, adminRoleId))
                    .as("管理端菜单必须授权给 admin：" + menuId).isTrue();
            assertThat(grantedTo(menuId, parentRoleId))
                    .as("管理端菜单**不应**授权给家长（内容维护是管理端职责）：" + menuId).isFalse();
            assertThat(grantedTo(menuId, kidRoleId))
                    .as("管理端菜单**不应**授权给孩子：" + menuId).isFalse();
        }

        for (String menuId : PARENT_MENU_IDS) {
            assertThat(grantedTo(menuId, parentRoleId))
                    .as("家长端菜单必须授权给家长：" + menuId).isTrue();
            assertThat(grantedTo(menuId, adminRoleId))
                    .as("家长端菜单必须授权给 admin（管理端演示要用）：" + menuId).isTrue();
            assertThat(grantedTo(menuId, kidRoleId))
                    .as("家长端菜单**不应**授权给孩子：" + menuId).isFalse();
        }
    }

    // ================================================================= 断言 2：路由不冲突

    @Test
    @DisplayName("study 域菜单的 url 与 component_name 不得重复（重复路由名会让页面互相顶掉）")
    void studyMenusMustNotCollide() {
        List<Map<String, Object>> rows = queryList(
                "SELECT url, component_name FROM sys_permission "
                        + "WHERE id LIKE 'st\\_%' AND menu_type IN ('0','1') "
                        + "AND url IS NOT NULL AND url <> '' AND del_flag = 0");

        Set<String> urls = new LinkedHashSet<>();
        Set<String> names = new LinkedHashSet<>();
        List<String> dupUrls = new ArrayList<>();
        List<String> dupNames = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String url = String.valueOf(row.get("url"));
            if (!urls.add(url)) {
                dupUrls.add(url);
            }
            Object cn = row.get("component_name");
            if (cn != null && !String.valueOf(cn).isBlank()) {
                String name = String.valueOf(cn);
                if (!names.add(name)) {
                    dupNames.add(name);
                }
            }
        }

        assertThat(dupUrls).as("study 域菜单出现重复 url（同一个 path 注册两次，行为不可预期）").isEmpty();
        assertThat(dupNames).as("study 域菜单出现重复 component_name（vue-router route name 冲突）").isEmpty();
    }

    // ================================================================= 断言 3：孩子端登录页走静态路由

    @Test
    @DisplayName("孩子端登录页必须是免认证静态路由，且**不得**同时挂进动态菜单")
    void kidLoginMustBeStaticRoute() {
        String routes = RedLineScanner.read(RedLineScanner.frontendRoot().resolve(ROUTES_INDEX));

        assertThat(routes)
                .as("孩子端登录页必须在底座静态路由表里登记（孩子没有账号密码，只有家庭码 + PIN，"
                        + "动态菜单要登录态才下发，挂在菜单里孩子根本进不去）")
                .contains(KID_LOGIN_PATH);
        assertThat(routes)
                .as("孩子端登录页必须带 ignoreAuth（permissionGuard 在无 token 时靠它放行）")
                .contains("ignoreAuth");
        assertThat(routes)
                .as("孩子端登录页必须被放进 basicRoutes（静态注册，不依赖登录态）")
                .contains("StudyKidLoginRoute");

        // 反向：同一个 path 不能既在静态路由又在 sys_permission —— vue-router 会注册两次
        Integer inDb = count("SELECT COUNT(*) FROM sys_permission WHERE url = ?", KID_LOGIN_PATH);
        assertThat(inDb)
                .as("孩子端登录页**不能**同时出现在 sys_permission（静态路由 + 动态菜单会注册出同一个 path）")
                .isZero();
    }

    @Test
    @DisplayName("孩子端登录页必须把 token 交给底座 userStore，不能自建本地存储键")
    void kidLoginMustUseBaseUserStoreForToken() {
        String page = stripComments(RedLineScanner.read(
                RedLineScanner.frontendRoot().resolve("views/study/kid/login/index.vue")));

        assertThat(page)
                .as("登录成功后必须走底座 userStore.setToken："
                        + "axios 层统一从 store 取 token 注入请求头，自建 localStorage 键不会被带上，"
                        + "结果就是「登录成功但后续请求全部 401」")
                .contains("setToken");
        assertThat(page)
                .as("孩子端登录页不得自己往 localStorage 写 token 键")
                .doesNotContain("STUDY_KID_TOKEN");
    }

    @Test
    @DisplayName("孩子端登录页不得暴露候选孩子的账号名等身份信息")
    void kidLoginMustNotExposeIdentityFields() {
        String page = stripComments(RedLineScanner.read(
                RedLineScanner.frontendRoot().resolve("views/study/kid/login/index.vue")));

        for (String forbidden : List.of("userName", "username", "userId", "accountName", "parentUserId")) {
            assertThat(page)
                    .as("孩子端登录页不得渲染 " + forbidden + "（只显示头像 + 昵称，§6.2）")
                    .doesNotContain(forbidden);
        }
    }

    // ================================================================= 断言 4：家长端页面与外壳

    @Test
    @DisplayName("家长端 3 个页面都必须走 StudyParentShell，外壳导航必须带上这 3 条路由")
    void parentPagesMustUseShellAndShellMustNavigate() {
        for (String page : PARENT_PAGES) {
            String content = RedLineScanner.read(RedLineScanner.frontendRoot().resolve(page));
            assertThat(content)
                    .as("家长端页面必须统一走家长端外壳（家长端无侧栏，外壳是唯一导航与登出入口）：" + page)
                    .contains("StudyParentShell");
        }

        String shell = stripComments(RedLineScanner.read(RedLineScanner.frontendRoot().resolve(PARENT_SHELL)));
        for (String route : List.of("/study-parent/family", "/study-parent/child")) {
            assertThat(shell)
                    .as("家长端外壳没有侧栏，顶部条是唯一入口，必须带上路由：" + route)
                    .contains(route);
        }
    }

    // ================================================================= 断言 5：管理端页面走通用内核

    @Test
    @DisplayName("管理端 9 个页面都必须走通用内核 ManageContentPage（避免 9 份重复代码各自漂移）")
    void managePagesMustUseSharedKernel() {
        for (String page : MANAGE_PAGES) {
            String content = RedLineScanner.read(RedLineScanner.frontendRoot().resolve(page));
            assertThat(content)
                    .as("管理端页面必须走通用内核 ManageContentPage：" + page)
                    .contains("ManageContentPage");
        }
    }

    // ================================================================= 断言 6：R-04 管理端不展示孩子行为数据

    @Test
    @DisplayName("R-04：管理端页面与 API 层不得出现按孩子统计的行为字段，也不得调用错题池")
    void managePagesMustNotExposeChildBehavior() {
        List<String> files = new ArrayList<>(MANAGE_PAGES);
        files.add("views/study/manage/manageApi.ts");

        for (String file : files) {
            String code = stripComments(RedLineScanner.read(RedLineScanner.frontendRoot().resolve(file)));
            for (String field : CHILD_BEHAVIOR_FIELDS) {
                assertThat(code)
                        .as("管理端只维护全局共享内容，" + file + " 里出现按孩子统计的字段 " + field
                                + " 会把单个孩子的行为数据带进内容维护台（R-04）")
                        .doesNotContain(field);
            }
            assertThat(code)
                    .as("管理端页面不得调用错题池接口（study_error_pool 是按孩子隔离的行为表，"
                            + "且 GET /error-pool 强制带 childCode）：" + file)
                    .doesNotContain("/error-pool");
        }
    }

    // ================================================================= 断言 7：R-01 词表不下发拼写

    @Test
    @DisplayName("R-01：词表维护页与 API 层不得索取或展示拼写")
    void wordPageMustNotTouchSpelling() {
        for (String file : List.of("views/study/manage/word/index.vue", "views/study/manage/manageApi.ts")) {
            String code = stripComments(RedLineScanner.read(RedLineScanner.frontendRoot().resolve(file)));
            assertThat(code)
                    .as("拼写就是拼写题的答案，管理端词表页面不得索取/展示拼写（R-01）：" + file)
                    .doesNotContain("spelling");
        }
    }

    // ================================================================= 断言 8：前端 URL 必须真实存在于后端

    @Test
    @DisplayName("前端写的每个接口 URL 都必须在后端控制器里真实存在（方法 + 路径双对上）")
    void frontendUrlsMustExistInBackend() {
        Set<String> backend = backendEndpoints();
        assertThat(backend)
                .as("扫描有效性：必须能从后端控制器解析出接口清单")
                .isNotEmpty();

        List<String> apiFiles = List.of(
                "views/study/manage/manageApi.ts",
                "views/study/parent/familyApi.ts",
                "views/study/kid/login/kidAuthApi.ts");

        List<String> missing = new ArrayList<>();
        int checked = 0;
        for (String file : apiFiles) {
            String code = RedLineScanner.read(RedLineScanner.frontendRoot().resolve(file));
            Matcher matcher = FRONTEND_CALL.matcher(code);
            while (matcher.find()) {
                String method = matcher.group(1).toUpperCase();
                String url = normalize(matcher.group(3));
                checked++;
                if (!backend.contains(method + " " + url)) {
                    missing.add(method + " " + url + "（" + file + "）");
                }
            }
        }

        assertThat(checked).as("扫描有效性：必须从前端 API 层解析出接口调用").isGreaterThan(20);
        assertThat(missing)
                .as("前端写了后端不存在的接口 —— 页面会在运行时 404。缺：" + missing)
                .isEmpty();
    }

    // ================================================================= 断言 9：菜单 SQL 真源与库内一致

    @Test
    @DisplayName("菜单 SQL 真源与库内菜单必须一致（真源在仓库根；文件不存在时跳过该检查）")
    void menuSqlMustMatchDatabase() {
        Path sql = RedLineScanner.repoRoot().resolve(MENU_SQL);
        if (!Files.isRegularFile(sql)) {
            // 基座单独还原（restore）时仓库根没有 SQL 目录，这一项无法验证，跳过而不是假绿
            System.out.println("[StudyInterfacePageTest] 未找到菜单 SQL 真源（基座独立还原场景），跳过一致性检查：" + sql);
            return;
        }

        String content = RedLineScanner.read(sql);
        for (Map.Entry<String, String> entry : EXPECTED_MENUS.entrySet()) {
            assertThat(content)
                    .as("菜单 SQL 真源里必须有这条菜单：" + entry.getKey())
                    .contains(entry.getKey());
        }
        for (String key : EXPECTED_MENUS.keySet()) {
            Map<String, Object> row = queryOne("SELECT component FROM sys_permission WHERE id = ?", key);
            assertThat(row).as("菜单必须已落库：" + key).isNotNull();
            assertThat(content)
                    .as("SQL 真源里的 component 必须与库内一致：" + key)
                    .contains(String.valueOf(row.get("component")));
        }
    }

    // ================================================================= 取数

    /** 按 role_code 取角色 id（sys_role 没有 del_flag 列，只有 id / role_code / role_name 等） */
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
     * 解析后端控制器，得出「方法 + 路径」全集。
     *
     * <p>路径里的占位符统一成 {@code {}}（{@code {code}} 与前端 {@code ${code}} 归一后才可比）。</p>
     */
    private Set<String> backendEndpoints() {
        Set<String> out = new LinkedHashSet<>();
        for (Path file : RedLineScanner.javaSources()) {
            String name = file.getFileName().toString();
            if (!name.endsWith("Controller.java")) {
                continue;
            }
            String source = RedLineScanner.read(file).replaceAll("(?s)/\\*.*?\\*/", " ");

            // 类级 @RequestMapping：取第一个（在 class 声明之前）
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

    /** 拼接类级与方法级路径 */
    private String join(String root, String path) {
        String head = root == null ? "" : root;
        String tail = path == null ? "" : path;
        if (head.isEmpty()) {
            return tail.isEmpty() ? "/" : tail;
        }
        if (tail.isEmpty()) {
            return head;
        }
        return (head.endsWith("/") ? head.substring(0, head.length() - 1) : head)
                + (tail.startsWith("/") ? tail : "/" + tail);
    }

    /** 占位符归一：{@code ${code}} / {@code {code}} → {@code {}} */
    private String normalize(String path) {
        return PLACEHOLDER.matcher(path).replaceAll("{}");
    }

    /**
     * 去掉 HTML 注释、块注释与行注释。
     *
     * <p><b>必须先剥注释</b>：本项目既有纪律「字面量扫描类断言必须先剥注释，否则假绿」。
     * 本类的 R-04 / R-01 断言全是字面量扫描——注释里为了说明「这些字段不能出现」而写下字段名，
     * 不剥注释就会把这些说明当成违规。</p>
     */
    private String stripComments(String content) {
        String out = content;
        out = out.replaceAll("(?s)<!--.*?-->", " ");
        out = out.replaceAll("(?s)/\\*.*?\\*/", " ");
        out = out.replaceAll("(?m)//[^\\n]*", " ");
        return out;
    }
}
