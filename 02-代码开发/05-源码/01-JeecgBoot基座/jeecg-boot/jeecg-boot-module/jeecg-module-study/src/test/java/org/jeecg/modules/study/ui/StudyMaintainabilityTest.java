package org.jeecg.modules.study.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.jeecg.modules.study.redline.RedLineScanner;

/**
 * **N-09「可维护性」的可执行断言套件**（把「代码走查」变成自动化走查）。
 *
 * <p>需求原文：<b>遵循 JeecgBoot 既有分层与代码规范；新增页面遵循《产品设计规范》。
 * 验收方式：代码走查。</b></p>
 *
 * <p><b>为什么必须有这个类</b>：「代码走查」靠人就会漏，而且只在改动那一刻查一次。
 * 这里的做法是：<b>把走查规则写成源码级断言</b>，让每次构建都重新走一遍。
 * 规则全部来自<b>真实代码</b>（读现有实现归纳出来的约定），不臆造。</p>
 *
 * <p><b>分层</b>：Controller 只依赖 Service 接口；Service 实现不跨域访问别的域的 Mapper
 * （跨域只读点有显式白名单）；Mapper 要么继承 {@code BaseMapper}、要么是注解式 SQL Mapper；
 * 实体继承 {@code JeecgEntity}。</p>
 *
 * <p><b>规范</b>：接口根统一 {@code /api/v1/study}；Controller 方法统一返回 {@code Result}；
 * 权限码统一 {@code study:<域>:<动作>} 且不硬编码角色名；类简单名全模块唯一（ADR-027）。</p>
 *
 * <p><b>前端侧</b>：每个学习端页面都要有对应的 {@code sys_permission} 菜单种子
 * （页面有代码却没菜单 = 用户永远进不去）；学习端不引入第三方统计 / 广告 / 崩溃上报 SDK。</p>
 *
 * <p><b>扫描有效性</b>：每条扫描类断言都先断言扫到的集合非空，防假绿。</p>
 *
 * @author jeecg-study
 */
class StudyMaintainabilityTest {

    // ------------------------------------------------------------------ 目录与常量

    /** 学习端视图目录（相对前端根） */
    private static final String VIEWS_STUDY = "views/study";

    /** SQL 真源目录（相对仓库根） */
    private static final String SQL_SCRIPT_DIR = "02-代码开发/02-数据库/03-脚本";

    /** 接口根 */
    private static final String API_ROOT = "/api/v1/study";

    /** 权限码形态：study: + 2~3 段（域 / 子域 / 动作） */
    private static final Pattern PERMISSION_CODE =
            Pattern.compile("^study:[a-z0-9][a-z0-9-]*(:[a-z0-9][a-z0-9-]*){1,2}$");

    /**
     * 「动作」段不许出现的角色名。
     *
     * <p>权限码表达的是<b>资源 + 动作</b>，动作位写角色名（{@code study:xxx:parent}）
     * 就把「谁能做」写进了「能做什么」，授权模型会歪。</p>
     */
    private static final Set<String> ROLE_NAMES =
            Set.of("parent", "kid", "admin", "teacher", "student", "user", "role", "guest");

    /** Spring 的映射注解（用于判定「哪些方法是接口端点」） */
    private static final List<Class<?>> MAPPING_ANNOTATIONS = List.of(
            RequestMapping.class, GetMapping.class, PostMapping.class,
            PutMapping.class, DeleteMapping.class, PatchMapping.class);

    /** Spring 的 bean 注解（ADR-027：只有这些会被注册成 bean，简单名必须唯一） */
    private static final List<String> BEAN_ANNOTATIONS = Arrays.asList(
            "@Mapper", "@Service", "@Component", "@Controller", "@RestController",
            "@Repository", "@Configuration");

    /**
     * <b>已知偏差 1：Controller 直接注入了 Mapper</b>。
     *
     * <p>{@code StudyErrorPoolController → StudyChildLookupMapper}：
     * 家长端按 childCode 定位孩子主键时直接用了只读 Mapper。
     * 这是<b>分层偏差</b>（Controller 应当只依赖 Service），待后续把
     * 「按 childCode 解析 childId」下沉到错题域的 Service 再收口。</p>
     */
    private static final Set<String> KNOWN_CONTROLLER_MAPPER_DEVIATIONS =
            Set.of("StudyErrorPoolController#StudyChildLookupMapper");

    /**
     * <b>已知偏差 2：硬编码角色名的 {@code @RequiresRoles}</b>。
     *
     * <p>{@code StudyParentReportController} 与 {@code StudyWritingParentController}
     * 在方法上写了 {@code @RequiresRoles("study_parent")}。
     * 权限应统一走 {@code study:<域>:<动作>} 权限码，角色名只应出现在角色-权限映射里。</p>
     */
    private static final Set<String> KNOWN_HARDCODED_ROLE_DEVIATIONS =
            Set.of("StudyParentReportController", "StudyWritingParentController");

    /**
     * <b>跨域只读白名单</b>：Service 实现类允许访问的其它域 Mapper。
     *
     * <p>每一条都有理由——跨域访问只允许「只读」，且必须是数据范围隔离所必需的：</p>
     * <ul>
     *   <li>{@code context → family/StudyChildMapper}：上下文按孩子隔离，
     *       配上下文前必须先按 childCode 定位孩子主键（ADR-018）；</li>
     *   <li>{@code kid → family/StudyChildMapper / StudyFamilyMapper / StudyParentChildRelMapper}：
     *       孩子用「家庭码 + PIN」登录，登录链路必须读家庭、亲子关系与孩子档案（ADR-018）；</li>
     *   <li>{@code parent → family/StudyChildMapper}：家长端按 childCode 定位孩子主键后查报告；</li>
     *   <li>{@code scope → family/StudyChildMapper / StudyFamilyMemberMapper / StudyParentChildRelMapper}：
     *       {@code ChildScopeService} 本身就是「按 child_id 隔离」的公共判定服务，
     *       必须读家庭域的关系表。</li>
     * </ul>
     */
    private static final Set<String> CROSS_DOMAIN_READ_WHITELIST = Set.of(
            "context/service/impl/StudyContextServiceImpl.java#family/StudyChildMapper",
            "kid/service/impl/StudyKidAuthServiceImpl.java#family/StudyChildMapper",
            "kid/service/impl/StudyKidAuthServiceImpl.java#family/StudyFamilyMapper",
            "kid/service/impl/StudyKidAuthServiceImpl.java#family/StudyParentChildRelMapper",
            "parent/service/impl/StudyParentReportServiceImpl.java#family/StudyChildMapper",
            "scope/service/impl/ChildScopeServiceImpl.java#family/StudyChildMapper",
            "scope/service/impl/ChildScopeServiceImpl.java#family/StudyFamilyMemberMapper",
            "scope/service/impl/ChildScopeServiceImpl.java#family/StudyParentChildRelMapper");

    /**
     * <b>已知偏差 3：跨域写</b>。
     *
     * <p>{@code kid/service/impl/StudyKidAuthServiceImpl.java} 的
     * {@code childMapper.update(...)} 回写孩子档案上的「PIN 失败次数 / 锁定时间」。
     * 锁定状态就长在孩子那一行上，写入是必需的，但它确实是<b>跨域写</b>，
     * 与「跨域只读」的约定相悖，登记在此以免被悄悄放过。</p>
     */
    private static final Set<String> CROSS_DOMAIN_WRITE_WHITELIST = Set.of(
            "kid/service/impl/StudyKidAuthServiceImpl.java#family/StudyChildMapper#update");

    /**
     * <b>已知偏差 4：非实体载体</b>。
     *
     * <p>{@code math/entity/StudyMathRecord} 只是「数学作答记录的写入载体」，
     * 类注释里已写明「不是数据库实体，只作写入参数用」，所以不继承 {@code JeecgEntity}。</p>
     */
    private static final Set<String> NON_ENTITY_WHITELIST = Set.of("StudyMathRecord");

    /**
     * 第三方统计 / 广告 / 崩溃上报 SDK（学习端一律不许引入）。
     *
     * <p>对应《测试用例》TC-08-12「零第三方请求」与仓库红线「学习端不引入第三方
     * 统计、广告、崩溃上报 SDK」。</p>
     */
    private static final List<String> FORBIDDEN_SDKS = Arrays.asList(
            "analytics", "sentry", "bugly", "growingio", "umeng", "baidu",
            "tongji", "gtag", "googletagmanager", "mixpanel", "datadog", "fundebug");

    /** 跨域访问必须是只读方法 */
    private static final List<String> READ_METHOD_PREFIXES =
            Arrays.asList("select", "get", "query", "list", "count", "find", "exists", "load");

    // ================================================================= 分层

    @Test
    @DisplayName("N-09 分层1：Controller 只依赖 Service 接口，不直接注入 Mapper")
    void n09_controllerDoesNotInjectMapper() {
        List<Class<?>> controllers = controllerClasses();
        assertThat(controllers)
                .as("扫描有效性：必须扫到 Controller 类，否则这条断言会假绿")
                .isNotEmpty();

        List<String> deviations = new ArrayList<>();
        for (Class<?> type : controllers) {
            for (Field field : type.getDeclaredFields()) {
                Class<?> fieldType = field.getType();
                if (!fieldType.isInterface()) {
                    continue;
                }
                if (!fieldType.getName().endsWith("Mapper")) {
                    continue;
                }
                deviations.add(type.getSimpleName() + "#" + fieldType.getSimpleName());
            }
        }

        assertThat(deviations)
                .as("N-09 分层：Controller 不得直接注入 Mapper（已知偏差："
                        + KNOWN_CONTROLLER_MAPPER_DEVIATIONS + "）")
                .isSubsetOf(KNOWN_CONTROLLER_MAPPER_DEVIATIONS);
    }

    @Test
    @DisplayName("N-09 分层2：Service 实现不跨域访问别的域的 Mapper（白名单外判违规）")
    void n09_serviceImplDoesNotCrossDomainMapper() {
        List<Path> impls = serviceImplSources();
        assertThat(impls)
                .as("扫描有效性：必须扫到 Service 实现类，否则这条断言会假绿")
                .isNotEmpty();

        Pattern importPattern =
                Pattern.compile("^import org\\.jeecg\\.modules\\.study\\.([a-z0-9.]+)\\.mapper\\.(\\w+);");
        Pattern callPattern = Pattern.compile("\\b(\\w*[Mm]apper)\\.(\\w+)\\s*\\(");

        List<String> violations = new ArrayList<>();
        List<String> writes = new ArrayList<>();
        for (Path file : impls) {
            String rel = moduleRel(file);
            String domain = rel.contains("/") ? rel.substring(0, rel.indexOf('/')) : rel;
            String content = RedLineScanner.read(file);

            List<String> foreignMappers = new ArrayList<>();
            Matcher matcher = importPattern.matcher(content);
            while (matcher.find()) {
                String pkg = matcher.group(1);
                String mapper = matcher.group(2);
                String foreignDomain = pkg.contains(".") ? pkg.substring(0, pkg.indexOf('.')) : pkg;
                if (foreignDomain.equals(domain)) {
                    continue;
                }
                foreignMappers.add(foreignDomain + "/" + mapper);
                String key = rel + "#" + foreignDomain + "/" + mapper;
                if (!CROSS_DOMAIN_READ_WHITELIST.contains(key)) {
                    violations.add(rel + " → 跨域访问 Mapper " + foreignDomain + "/" + mapper
                            + "（不在跨域只读白名单里）");
                }
            }

            // 跨域调用必须是只读方法（登记过的跨域写除外）
            for (String foreign : foreignMappers) {
                String simple = foreign.substring(foreign.indexOf('/') + 1);
                Matcher call = callPattern.matcher(content);
                while (call.find()) {
                    String receiver = call.group(1);
                    String method = call.group(2);
                    if (!belongsTo(receiver, simple)) {
                        continue;
                    }
                    boolean readOnly = READ_METHOD_PREFIXES.stream().anyMatch(method::startsWith);
                    if (readOnly) {
                        continue;
                    }
                    String key = rel + "#" + foreign + "#" + method;
                    if (!CROSS_DOMAIN_WRITE_WHITELIST.contains(key)) {
                        writes.add(rel + " → 跨域写 " + foreign + "." + method
                                + "（跨域访问只允许只读）");
                    }
                }
            }
        }

        assertThat(violations)
                .as("N-09 分层：Service 实现不得跨域访问别的域的 Mapper；跨域只读点必须在显式白名单里")
                .isEmpty();
        assertThat(writes)
                .as("N-09 分层：跨域访问只允许只读（已知偏差：" + CROSS_DOMAIN_WRITE_WHITELIST + "）")
                .isEmpty();
    }

    @Test
    @DisplayName("N-09 分层3：Mapper 要么继承 BaseMapper，要么是注解式 SQL Mapper")
    void n09_mapperFollowsBaseMapperOrAnnotatedSql() {
        List<Class<?>> mappers = classesWithSuffix("Mapper.class");
        assertThat(mappers)
                .as("扫描有效性：必须扫到 Mapper 接口，否则这条断言会假绿")
                .isNotEmpty();

        List<String> violations = new ArrayList<>();
        for (Class<?> type : mappers) {
            if (!type.isInterface()) {
                violations.add(type.getName() + " → Mapper 必须是接口");
                continue;
            }
            boolean extendsBaseMapper = hasBaseMapper(type);
            if (extendsBaseMapper) {
                continue;
            }
            // 注解式 SQL Mapper：每个方法都必须带 @Select/@Insert/@Update/@Delete
            Method[] methods = type.getDeclaredMethods();
            if (methods.length == 0) {
                violations.add(type.getName() + " → 既不继承 BaseMapper，也没有注解式 SQL 方法");
                continue;
            }
            for (Method method : methods) {
                boolean annotated = Arrays.stream(method.getAnnotations())
                        .anyMatch(a -> {
                            String name = a.annotationType().getSimpleName();
                            return "Select".equals(name) || "Insert".equals(name)
                                    || "Update".equals(name) || "Delete".equals(name);
                        });
                if (!annotated) {
                    violations.add(type.getName() + "#" + method.getName()
                            + " → 既不继承 BaseMapper，方法上也没有 SQL 注解");
                }
            }
        }

        assertThat(violations)
                .as("N-09 分层：Mapper 必须继承 BaseMapper（单表 CRUD），"
                        + "或退化为注解式 SQL Mapper（跨域只读 / 分表写入）")
                .isEmpty();
    }

    @Test
    @DisplayName("N-09 分层4：实体继承 JeecgEntity（非实体载体白名单除外）")
    void n09_entityExtendsJeecgEntity() {
        List<Class<?>> entities = entityClasses();
        assertThat(entities)
                .as("扫描有效性：必须扫到实体类，否则这条断言会假绿")
                .isNotEmpty();

        List<String> violations = new ArrayList<>();
        for (Class<?> type : entities) {
            if (NON_ENTITY_WHITELIST.contains(type.getSimpleName())) {
                continue;
            }
            if (!"org.jeecg.common.system.base.entity.JeecgEntity".equals(superClassName(type))) {
                violations.add(type.getName() + " → 实体必须继承 JeecgEntity");
            }
        }

        assertThat(violations)
                .as("N-09 分层：实体必须继承 JeecgEntity（基座统一审计字段与逻辑删除）")
                .isEmpty();
    }

    // ================================================================= 规范

    @Test
    @DisplayName("N-09 规范1：Controller 的 @RequestMapping 一律以 /api/v1/study 开头")
    void n09_requestMappingUsesUnifiedApiRoot() {
        List<Class<?>> controllers = controllerClasses();
        assertThat(controllers)
                .as("扫描有效性：必须扫到 Controller 类，否则这条断言会假绿")
                .isNotEmpty();

        List<String> violations = new ArrayList<>();
        for (Class<?> type : controllers) {
            RequestMapping mapping = type.getAnnotation(RequestMapping.class);
            if (mapping == null) {
                violations.add(type.getSimpleName() + " → 类上没有 @RequestMapping");
                continue;
            }
            String[] values = mapping.value();
            if (values.length == 0 || !values[0].startsWith(API_ROOT)) {
                violations.add(type.getSimpleName() + " → @RequestMapping=" + Arrays.toString(values));
            }
        }

        assertThat(violations)
                .as("N-09 规范：接口根统一为 " + API_ROOT)
                .isEmpty();
    }

    @Test
    @DisplayName("N-09 规范2：Controller 的端点方法一律返回 Result")
    void n09_controllerEndpointsReturnResult() {
        List<Class<?>> controllers = controllerClasses();
        assertThat(controllers)
                .as("扫描有效性：必须扫到 Controller 类，否则这条断言会假绿")
                .isNotEmpty();

        List<String> violations = new ArrayList<>();
        int endpointCount = 0;
        for (Class<?> type : controllers) {
            for (Method method : type.getDeclaredMethods()) {
                if (!isEndpoint(method)) {
                    continue;
                }
                endpointCount++;
                Class<?> returnType = method.getReturnType();
                if (!"Result".equals(returnType.getSimpleName())) {
                    violations.add(type.getSimpleName() + "#" + method.getName()
                            + " → 返回类型 " + returnType.getSimpleName());
                }
            }
        }
        assertThat(endpointCount).as("扫描有效性：必须扫到端点方法").isGreaterThan(0);

        assertThat(violations)
                .as("N-09 规范：响应体结构统一为 Result（success/code/message/result）")
                .isEmpty();
    }

    @Test
    @DisplayName("N-09 规范3：权限码统一 study:<域>:<动作>，动作位不写角色名")
    void n09_permissionCodeShape() {
        List<Class<?>> controllers = controllerClasses();
        assertThat(controllers)
                .as("扫描有效性：必须扫到 Controller 类，否则这条断言会假绿")
                .isNotEmpty();

        List<String> violations = new ArrayList<>();
        int permCount = 0;
        for (Class<?> type : controllers) {
            for (Method method : type.getDeclaredMethods()) {
                RequiresPermissions perm = method.getAnnotation(RequiresPermissions.class);
                if (perm == null) {
                    continue;
                }
                for (String code : perm.value()) {
                    permCount++;
                    if (!PERMISSION_CODE.matcher(code).matches()) {
                        violations.add(type.getSimpleName() + "#" + method.getName()
                                + " → 权限码形态不对: " + code);
                        continue;
                    }
                    String action = code.substring(code.lastIndexOf(':') + 1);
                    if (ROLE_NAMES.contains(action)) {
                        violations.add(type.getSimpleName() + "#" + method.getName()
                                + " → 权限码的动作位写了角色名: " + code);
                    }
                }
            }
        }
        assertThat(permCount).as("扫描有效性：必须扫到 @RequiresPermissions 注解").isGreaterThan(0);

        assertThat(violations)
                .as("N-09 规范：权限码统一为 study:<域>[:<子域>]:<动作>，动作位不得是角色名")
                .isEmpty();
    }

    @Test
    @DisplayName("N-09 规范4：不得硬编码角色名（@RequiresRoles 走白名单）")
    void n09_noHardcodedRoleNames() {
        List<Class<?>> controllers = controllerClasses();
        assertThat(controllers)
                .as("扫描有效性：必须扫到 Controller 类，否则这条断言会假绿")
                .isNotEmpty();

        List<String> deviations = new ArrayList<>();
        for (Class<?> type : controllers) {
            boolean usesRoles = false;
            for (Method method : type.getDeclaredMethods()) {
                RequiresRoles roles = method.getAnnotation(RequiresRoles.class);
                if (roles == null) {
                    continue;
                }
                usesRoles = true;
                for (String role : roles.value()) {
                    if (role.trim().isEmpty()) {
                        deviations.add(type.getSimpleName() + "#" + method.getName() + " → 角色名为空");
                    }
                }
            }
            if (usesRoles) {
                deviations.add(type.getSimpleName());
            }
        }

        assertThat(deviations)
                .as("N-09 规范：授权一律走权限码，不得硬编码角色名（已知偏差："
                        + KNOWN_HARDCODED_ROLE_DEVIATIONS + "）")
                .isSubsetOf(KNOWN_HARDCODED_ROLE_DEVIATIONS);
    }

    @Test
    @DisplayName("N-09 规范5：类简单名全模块唯一（ADR-027）")
    void n09_simpleClassNamesAreUnique() {
        Map<String, List<String>> byName = new LinkedHashMap<>();
        for (Path file : moduleJavaSources()) {
            String content = RedLineScanner.read(file);
            boolean bean = BEAN_ANNOTATIONS.stream().anyMatch(content::contains);
            if (!bean) {
                continue;
            }
            String simple = file.getFileName().toString().replace(".java", "");
            byName.computeIfAbsent(simple, k -> new ArrayList<>()).add(moduleRel(file));
        }
        assertThat(byName)
                .as("扫描有效性：必须扫到会被 Spring 注册成 bean 的类，否则这条断言会假绿")
                .isNotEmpty();

        List<String> conflicts = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : byName.entrySet()) {
            if (entry.getValue().size() > 1) {
                conflicts.add(entry.getKey() + " → " + String.join(" / ", entry.getValue()));
            }
        }

        assertThat(conflicts)
                .as("ADR-027：不同包下同名类会让 Spring 上下文起不来（bean 名 = 类简单名去首字母小写）")
                .isEmpty();
    }

    // ================================================================= 前端侧

    @Test
    @DisplayName("N-09 前端1：每个学习端页面都有对应的 sys_permission 菜单种子")
    void n09_everyLearningPageHasMenuSeed() {
        List<Path> pages = learningPageFiles();
        assertThat(pages)
                .as("扫描有效性：学习端页面集合必须非空，否则会假绿")
                .isNotEmpty();

        List<Path> scripts = sqlScripts();
        assertThat(scripts)
                .as("扫描有效性：SQL 脚本目录必须扫到文件，否则会假绿")
                .isNotEmpty();
        StringBuilder all = new StringBuilder();
        for (Path script : scripts) {
            all.append(RedLineScanner.read(script)).append('\n');
        }
        String seed = all.toString();
        assertThat(seed)
                .as("扫描有效性：SQL 脚本里必须能读到 sys_permission 的菜单种子")
                .contains("sys_permission");

        List<String> missing = new ArrayList<>();
        for (Path page : pages) {
            String rel = RedLineScanner.frontendRelative(page);
            // views/study/practice/index.vue → study/practice/index（sys_permission.component 的写法）
            String component = rel.replaceFirst("^views/", "").replace(".vue", "");
            if (!seed.contains("'" + component + "'")) {
                missing.add(rel + " → 脚本里找不到 component='" + component + "' 的菜单记录");
            }
        }

        assertThat(missing)
                .as("N-09：页面有代码却没菜单 = 用户永远进不去（动态路由由 sys_permission 下发）")
                .isEmpty();
    }

    @Test
    @DisplayName("N-09 前端2：学习端不引入第三方统计 / 广告 / 崩溃上报 SDK")
    void n09_noThirdPartyAnalyticsSdk() {
        List<Path> files = learningFrontendFiles();
        assertThat(files)
                .as("扫描有效性：学习端前端文件集合必须非空，否则会假绿")
                .isNotEmpty();

        List<String> hits = new ArrayList<>();
        Pattern importLine = Pattern.compile("(?m)^\\s*import\\s+.*?from\\s+'([^']+)'");
        for (Path file : files) {
            String content = RedLineScanner.read(file);
            String rel = RedLineScanner.frontendRelative(file);
            Matcher matcher = importLine.matcher(content);
            while (matcher.find()) {
                String module = matcher.group(1);
                String lower = module.toLowerCase(Locale.ROOT);
                for (String sdk : FORBIDDEN_SDKS) {
                    if (lower.contains(sdk)) {
                        hits.add(rel + " → import '" + module + "' 命中禁用 SDK 关键字 " + sdk);
                    }
                }
            }
        }

        assertThat(hits)
                .as("学习端不得引入第三方统计 / 广告 / 崩溃上报 SDK（TC-08-12 零第三方请求）")
                .isEmpty();
    }

    // ------------------------------------------------------------------ 工具

    /** 方法是不是接口端点（带 Spring 映射注解） */
    private static boolean isEndpoint(Method method) {
        return Arrays.stream(method.getAnnotations())
                .anyMatch(a -> MAPPING_ANNOTATIONS.contains(a.annotationType()));
    }

    /**
     * 字段名是否就是这个 Mapper 的引用。
     *
     * <p>字段名按 JeecgBoot 惯例是「类简单名去首字母小写并去掉 Study 前缀」，
     * 例如 {@code StudyChildMapper → childMapper}、{@code StudyParentChildRelMapper → relMapper}。
     * 这里用「类简单名以字段名结尾」来反查，容忍前缀差异。</p>
     */
    private static boolean belongsTo(String receiver, String mapperSimpleName) {
        if (!receiver.toLowerCase(Locale.ROOT).endsWith("mapper")) {
            return false;
        }
        return mapperSimpleName.toLowerCase(Locale.ROOT).endsWith(receiver.toLowerCase(Locale.ROOT));
    }

    /**
     * 模块内源码相对主源码根的路径（**保留大小写**，白名单键按原始大小写写）。
     *
     * <p>{@code RedLineScanner.moduleRelative} 会把路径转小写，白名单键对不上，
     * 所以这里自己算一份。</p>
     */
    private static String moduleRel(Path file) {
        Path root = RedLineScanner.studyMainRoot();
        try {
            return root.relativize(file.toAbsolutePath()).toString().replace('\\', '/');
        } catch (IllegalArgumentException e) {
            return file.getFileName().toString();
        }
    }

    /** 是否继承 MyBatis-Plus 的 BaseMapper */
    private static boolean hasBaseMapper(Class<?> type) {
        for (Class<?> iface : type.getInterfaces()) {
            if ("com.baomidou.mybatisplus.core.mapper.BaseMapper".equals(iface.getName())) {
                return true;
            }
        }
        return false;
    }

    /** 直接父类全名 */
    private static String superClassName(Class<?> type) {
        Class<?> parent = type.getSuperclass();
        return parent == null ? "" : parent.getName();
    }

    /** 扫描 classes 目录下以给定后缀结尾的类 */
    private static List<Class<?>> classesWithSuffix(String suffix) {
        Path classes = RedLineScanner.classesDir();
        List<Class<?>> out = new ArrayList<>();
        if (!Files.isDirectory(classes)) {
            return out;
        }
        try (Stream<Path> stream = Files.walk(classes)) {
            for (Path file : stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(suffix))
                    .sorted()
                    .toList()) {
                Class<?> type = load(classes, file);
                if (type != null) {
                    out.add(type);
                }
            }
        } catch (IOException e) {
            return out;
        }
        return out;
    }

    /** 扫描 classes 目录下 entity 包里的类 */
    private static List<Class<?>> entityClasses() {
        Path classes = RedLineScanner.classesDir();
        List<Class<?>> out = new ArrayList<>();
        if (!Files.isDirectory(classes)) {
            return out;
        }
        try (Stream<Path> stream = Files.walk(classes)) {
            for (Path file : stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".class"))
                    .filter(p -> p.toString().replace('\\', '/').contains("/entity/"))
                    .sorted()
                    .toList()) {
                Class<?> type = load(classes, file);
                if (type != null && !type.isInterface()) {
                    out.add(type);
                }
            }
        } catch (IOException e) {
            return out;
        }
        return out;
    }

    /** 扫描 @RestController 类 */
    private static List<Class<?>> controllerClasses() {
        List<Class<?>> out = new ArrayList<>();
        for (Class<?> type : classesWithSuffix("Controller.class")) {
            if (type.isAnnotationPresent(org.springframework.web.bind.annotation.RestController.class)) {
                out.add(type);
            }
        }
        return out;
    }

    /** 加载 class 文件对应的类（加载不了的跳过） */
    private static Class<?> load(Path classes, Path file) {
        String rel = classes.relativize(file).toString().replace('\\', '/');
        String className = rel.substring(0, rel.length() - ".class".length()).replace('/', '.');
        try {
            return Class.forName(className, false, StudyMaintainabilityTest.class.getClassLoader());
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 模块内全部 java 源码（main + test） */
    private static List<Path> moduleJavaSources() {
        Path module = RedLineScanner.moduleDir();
        List<Path> out = new ArrayList<>();
        for (String root : List.of("src/main/java", "src/test/java")) {
            Path dir = module.resolve(root.replace('/', java.io.File.separatorChar));
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> stream = Files.walk(dir)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".java"))
                        .forEach(out::add);
            } catch (IOException e) {
                // 目录读不到就跳过，断言里的「非空」守卫会兜住
            }
        }
        out.sort(java.util.Comparator.comparing(Path::toString));
        return out;
    }

    /** Service 实现类源码 */
    private static List<Path> serviceImplSources() {
        List<Path> out = new ArrayList<>();
        for (Path file : moduleJavaSources()) {
            String rel = moduleRel(file);
            if (rel.contains("/service/impl/") && rel.endsWith(".java")) {
                out.add(file);
            }
        }
        return out;
    }

    /** 学习端 / 家长端前端文件 */
    private static List<Path> learningFrontendFiles() {
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
    private static List<Path> learningPageFiles() {
        List<Path> out = new ArrayList<>();
        for (Path file : learningFrontendFiles()) {
            if ("index.vue".equals(file.getFileName().toString())) {
                out.add(file);
            }
        }
        return out;
    }

    /** SQL 真源目录下的脚本 */
    private static List<Path> sqlScripts() {
        Path dir = RedLineScanner.repoRoot().resolve(SQL_SCRIPT_DIR.replace('/', java.io.File.separatorChar));
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".sql"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }
}
