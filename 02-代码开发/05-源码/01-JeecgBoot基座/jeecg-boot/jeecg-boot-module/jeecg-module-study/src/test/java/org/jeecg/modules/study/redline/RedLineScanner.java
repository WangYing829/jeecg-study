package org.jeecg.modules.study.redline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.shiro.authz.annotation.RequiresPermissions;

/**
 * 源码 / 数据库扫描器（红线断言的公共工具）。
 *
 * <p>红线断言有三种实现形态，这里提供前两种的公共能力：</p>
 * <ol>
 *   <li><b>源码静态扫描</b>：扫字符串字面量与前端模板文本，找违规措辞 / 隐藏式实现 / AI 依赖；</li>
 *   <li><b>反射扫描</b>：扫 VO 字段名，找越权下发的字段；</li>
 *   <li><b>数据库扫描</b>：由测试类直接用 JdbcTemplate 查。</li>
 * </ol>
 *
 * <p><b>扫描范围的口径</b>（避免误报）：</p>
 * <ul>
 *   <li>后端：study 模块的 {@code src/main/java} —— 100% 是本项目代码，可严格扫；</li>
 *   <li>前端：<b>与学习端 / 家长端相关的文件</b>（路径含 study / parent / kid）严格扫；
 *       其余上游管理端页面只扫「强违规词」（百分位 / 排行 / 谁的孩子更 …）。</li>
 * </ul>
 *
 * @author jeecg-study
 */
public final class RedLineScanner {

    /**
     * Java 字符串字面量。
     *
     * <p><b>必须用占有量词（{@code ++} / {@code *+}）</b>：原来的
     * {@code "((?:[^\"\\\\]|\\\\.)*)"} 在上游大文件（如 825KB 的 canvas-editor.js）
     * 上会触发灾难性回溯 → {@code StackOverflowError}，把红线套件直接打挂。
     * 占有量词禁止回溯，复杂度降到线性。</p>
     */
    private static final Pattern JAVA_STRING = Pattern.compile("\"((?:[^\"\\\\]++|\\\\.)*+)\"");

    /**
     * 前端模板文本（Vue 模板里的纯文本节点）。
     *
     * <p><b>必须用懒惰量词（{@code *?}）</b>：贪婪的 {@code [^<>{}]*} 会一路吞掉
     * 中文再回退，叠加成指数级回溯。改成懒惰后逐字符推进，线性且匹配结果等价。</p>
     */
    private static final Pattern VUE_TEXT =
            Pattern.compile(">([^<>{}]*?[\\u4e00-\\u9fa5][^<>{}]*?)<");

    /** AI 客户端包前缀（判定层禁止出现） */
    private static final List<String> AI_CLIENT_PACKAGES = Arrays.asList(
            "org.springframework.ai",
            "com.alibaba.dashscope",
            "com.alibaba.cloud.ai",
            "com.theokanning.openai",
            "com.langchain4j",
            "com.zhipu",
            "com.baichuan",
            "cn.hutool.ai",
            "tencentcloud.ai",
            "openai.");

    private RedLineScanner() {
    }

    // ------------------------------------------------------------------ 路径

    /** 测试工作目录（surefire 下 = 模块目录） */
    public static Path moduleDir() {
        return Paths.get("").toAbsolutePath();
    }

    /** study 模块主源码根 */
    public static Path studyMainRoot() {
        return moduleDir().resolve("src").resolve("main").resolve("java")
                .resolve("org").resolve("jeecg").resolve("modules").resolve("study");
    }

    /** 仓库根 */
    public static Path repoRoot() {
        Path p = moduleDir();
        for (int i = 0; i < 6; i++) {
            p = p.getParent();
        }
        return p;
    }

    /** 前端源码根 */
    public static Path frontendRoot() {
        return repoRoot().resolve("02-代码开发").resolve("05-源码")
                .resolve("01-JeecgBoot基座").resolve("jeecgboot-vue3").resolve("src");
    }

    // ------------------------------------------------------------------ 文件

    public static List<Path> javaSources() {
        return listFiles(studyMainRoot(), ".java");
    }

    public static List<Path> frontendFiles() {
        Path root = frontendRoot();
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        return listFiles(root, ".vue", ".ts", ".tsx", ".js", ".jsx");
    }

    /**
     * <b>本项目管辖的前端目录</b>（相对 {@code frontendRoot()}）。
     *
     * <p>红线只约束本产品自己的页面。上游 JeecgBoot 基座的几百个 demo / 仪表盘页面
     * 不属于本产品，且按约定「上游基座零改动」——把它们纳入红线范围，
     * 等于要求我们改上游才能让测试变绿，这既不可能也不应该。</p>
     */
    public static final List<String> PROJECT_FRONTEND_DIRS =
            List.of("views/study", "layouts/study", "components/study");

    /**
     * 本项目管辖的前端文件（学习端页面 + 布局 + 自研组件）。
     *
     * <p>⚠ 不要用「绝对路径里含 study/parent/kid」来判断范围：仓库路径
     * {@code 20261030-jeecg-study} 本身就含 "study"，会导致**上游所有页面**
     * 都被当成学习端扫，红线断言会命中上游 demo 页（排行榜、分析看板等）。</p>
     *
     * @return 本项目前端文件列表
     */
    public static List<Path> projectFrontendFiles() {
        Path root = frontendRoot();
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        List<Path> out = new ArrayList<>();
        for (String dir : PROJECT_FRONTEND_DIRS) {
            for (Path file : listFiles(root.resolve(dir), ".vue", ".ts", ".tsx", ".js", ".jsx")) {
                out.add(file);
            }
        }
        out.sort(Comparator.comparing(Path::toString));
        return out;
    }

    /**
     * 前端文件相对 {@code frontendRoot()} 的路径（小写，正斜杠）。
     *
     * @param file 前端文件
     * @return 相对路径；不在前端根下时返回绝对路径
     */
    public static String frontendRelative(Path file) {
        Path root = frontendRoot();
        try {
            return root.relativize(file.toAbsolutePath()).toString()
                    .replace('\\', '/').toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return file.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        }
    }

    /**
     * 模块内源码相对 {@code studyMainRoot()} 的路径（小写，正斜杠）。
     *
     * @param file 源码文件
     * @return 相对路径
     */
    public static String moduleRelative(Path file) {
        Path root = studyMainRoot();
        try {
            return root.relativize(file.toAbsolutePath()).toString()
                    .replace('\\', '/').toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return file.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        }
    }

    private static List<Path> listFiles(Path root, String... suffixes) {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        for (String suffix : suffixes) {
                            if (name.endsWith(suffix)) {
                                return true;
                            }
                        }
                        return false;
                    })
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    public static String read(Path path) {
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    // ------------------------------------------------------------------ 扫描

    /**
     * 扫 Java 源码里的中文字符串字面量，返回命中「禁止措辞」的位置描述。
     *
     * @param forbidden 禁止措辞（子串匹配）
     * @param roots     限定扫描的包路径片段；null = 全模块
     * @return 命中描述列表，空列表 = 通过
     */
    public static List<String> scanJavaCopy(List<String> forbidden, List<String> roots) {
        List<String> hits = new ArrayList<>();
        for (Path file : javaSources()) {
            if (roots != null && !roots.stream().anyMatch(r -> file.toString().replace('\\', '/').contains(r))) {
                continue;
            }
            String content = read(file);
            for (String literal : chineseStringLiterals(content)) {
                for (String word : forbidden) {
                    if (literal.contains(word)) {
                        hits.add(rel(file) + " → 「" + literal.trim() + "」含「" + word + "」");
                    }
                }
            }
        }
        return hits;
    }

    /**
     * 扫前端源码的中文文案（模板文本 + 引号字符串），返回命中位置。
     *
     * @param forbidden      禁止措辞
     * @param onlyStudyScope true = 只扫与学习端 / 家长端相关的文件
     */
    public static List<String> scanFrontendCopy(List<String> forbidden, boolean onlyStudyScope) {
        List<String> hits = new ArrayList<>();
        for (Path file : projectFrontendFiles()) {
            String path = frontendRelative(file);
            boolean studyScope = path.startsWith("views/study/") || path.startsWith("layouts/study/")
                    || path.startsWith("components/study/");
            if (onlyStudyScope && !studyScope) {
                continue;
            }
            String content = read(file);
            for (String literal : frontendChineseTexts(content)) {
                for (String word : forbidden) {
                    if (literal.contains(word)) {
                        hits.add(rel(file) + " → 「" + literal.trim() + "」含「" + word + "」");
                    }
                }
            }
        }
        return hits;
    }

    /**
     * 扫源码里是否出现「隐藏式答案」实现。
     *
     * <p>两种形态都算违规：</p>
     * <ul>
     *   <li>{@code v-show="false"} / {@code v-show="!xxx"} 这类用指令把答案藏起来；</li>
     *   <li>{@code display:none} 直接把答案容器从布局里摘掉。</li>
     * </ul>
     *
     * @return 命中位置，空列表 = 通过
     */
    public static List<String> scanHiddenAnswerImplementation() {
        List<String> hits = new ArrayList<>();
        List<String> hideTokens = Arrays.asList("v-show", "display:none", "display: none");
        List<String> answerTokens = Arrays.asList("answer", "analysis", "correctanswer", "正确答案", "答案", "解析");
        for (Path file : projectFrontendFiles()) {
            String content = read(file).toLowerCase(Locale.ROOT);
            boolean hasHide = hideTokens.stream().anyMatch(content::contains);
            boolean hasAnswer = answerTokens.stream().anyMatch(content::contains);
            if (hasHide && hasAnswer) {
                hits.add(rel(file) + " 同时出现隐藏指令与答案标识，疑似隐藏式答案实现");
            }
        }
        return hits;
    }

    /**
     * 扫判定层（包路径含 judge）的 AI 客户端依赖。
     *
     * @return 命中位置，空列表 = 通过
     */
    public static List<String> scanJudgeAiDependency() {
        List<String> hits = new ArrayList<>();
        for (Path file : javaSources()) {
            String path = file.toString().replace('\\', '/');
            if (!path.contains("/judge")) {
                continue;
            }
            String content = read(file);
            for (String line : content.split("\n")) {
                String trimmed = line.trim();
                if (!trimmed.startsWith("import ")) {
                    continue;
                }
                for (String pkg : AI_CLIENT_PACKAGES) {
                    if (trimmed.toLowerCase(Locale.ROOT).contains(pkg.toLowerCase(Locale.ROOT))) {
                        hits.add(rel(file) + " → " + trimmed);
                    }
                }
            }
        }
        return hits;
    }

    // ------------------------------------------------------------------ 反射

    /**
     * 已编译 class 的根目录。
     *
     * <p>默认 {@code moduleDir()/target/classes}（{@code mvn test} 下就是这个）。
     * 但用私有运行器跑时 {@code target/classes} 是**上一次 mvn 构建的陈旧产物**，
     * VO 字段类红线断言会基于旧代码下结论；
     * 此时可用系统属性 {@code -Dstudy.classes.dir=<目录>} 指向本次编译产物。</p>
     *
     * @return class 根目录
     */
    public static Path classesDir() {
        String override = System.getProperty("study.classes.dir");
        if (override != null && !override.trim().isEmpty()) {
            return Paths.get(override.trim()).toAbsolutePath();
        }
        return moduleDir().resolve("target").resolve("classes");
    }

    /**
     * 列出 study 模块里所有 {@code vo} 包下的类（已编译）。
     *
     * @return 类列表
     */
    public static List<Class<?>> voClasses() {
        Path classes = classesDir();
        List<Class<?>> out = new ArrayList<>();
        if (!Files.isDirectory(classes)) {
            return out;
        }
        try (Stream<Path> stream = Files.walk(classes)) {
            List<Path> files = stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().replace('\\', '/').contains("/vo/"))
                    .filter(p -> p.getFileName().toString().endsWith(".class"))
                    .sorted()
                    .toList();
            for (Path file : files) {
                String rel = classes.relativize(file).toString().replace('\\', '/');
                String className = rel.substring(0, rel.length() - ".class".length()).replace('/', '.');
                try {
                    out.add(Class.forName(className, false, RedLineScanner.class.getClassLoader()));
                } catch (Throwable ignored) {
                    // 加载不了的类跳过
                }
            }
        } catch (IOException e) {
            return out;
        }
        return out;
    }

    /**
     * 类的字段名（小写）。
     */
    public static List<String> fieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields()).map(f -> f.getName().toLowerCase(Locale.ROOT)).toList();
    }

    /**
     * 类的包路径（小写，含点分隔）。
     */
    public static String packageOf(Class<?> type) {
        return type.getPackageName().toLowerCase(Locale.ROOT);
    }

    /**
     * 是响应 VO（排除入参类）。
     */
    public static boolean isResponseVo(Class<?> type) {
        String name = type.getSimpleName().toLowerCase(Locale.ROOT);
        return !(name.contains("request") || name.contains("command") || name.contains("query")
                || name.contains("param") || name.contains("criteria"));
    }

    /**
     * 按端点特征挑出响应 VO。
     *
     * <p>三种挑法都基于<b>真实的 Controller 方法签名与注解</b>，不是按类名猜：</p>
     * <ul>
     *   <li>{@code parentEndpointVos()} —— 权限码以 {@code study:parent:} 开头的端点；</li>
     *   <li>{@code kidEndpointVos()} —— 权限码以 {@code study:kid:} 开头的端点；</li>
     *   <li>{@code endpointVosByPath(String)} —— 路径含指定片段的端点。</li>
     * </ul>
     *
     * @return 响应 VO 列表
     */
    public static List<Class<?>> parentEndpointVos() {
        return endpointVosByPermissionPrefix("study:parent:");
    }

    public static List<Class<?>> kidEndpointVos() {
        return endpointVosByPermissionPrefix("study:kid:");
    }

    public static List<Class<?>> endpointVosByPath(String pathFragment) {
        List<Class<?>> out = new ArrayList<>();
        for (Method method : controllerMethods()) {
            String path = requestPath(method);
            if (path != null && path.toLowerCase(Locale.ROOT).contains(pathFragment.toLowerCase(Locale.ROOT))) {
                Class<?> vo = resultType(method);
                if (vo != null && !out.contains(vo)) {
                    out.add(vo);
                }
            }
        }
        return out;
    }

    private static List<Class<?>> endpointVosByPermissionPrefix(String prefix) {
        List<Class<?>> out = new ArrayList<>();
        for (Method method : controllerMethods()) {
            RequiresPermissions perm = method.getAnnotation(RequiresPermissions.class);
            if (perm == null) {
                continue;
            }
            boolean hit = Arrays.stream(perm.value()).anyMatch(v -> v.startsWith(prefix));
            if (!hit) {
                continue;
            }
            Class<?> vo = resultType(method);
            if (vo != null && !out.contains(vo)) {
                out.add(vo);
            }
        }
        return out;
    }

    /**
 * 列出全部端点路径（含类级 {@code @RequestMapping} 前缀与方法级映射）。
 *
 * @return 端点路径列表
 */
public static List<String> endpointPaths() {
    List<String> out = new ArrayList<>();
    for (Method method : controllerMethods()) {
        String path = requestPath(method);
        if (path != null) {
            out.add(path);
        }
    }
    return out;
}

private static List<Method> controllerMethods() {        List<Method> out = new ArrayList<>();
        Path classes = classesDir();
        if (!Files.isDirectory(classes)) {
            return out;
        }
        try (Stream<Path> stream = Files.walk(classes)) {
            List<Path> files = stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith("Controller.class"))
                    .sorted()
                    .toList();
            for (Path file : files) {
                String rel = classes.relativize(file).toString().replace('\\', '/');
                String className = rel.substring(0, rel.length() - ".class".length()).replace('/', '.');
                try {
                    Class<?> type = Class.forName(className, false, RedLineScanner.class.getClassLoader());
                    out.addAll(Arrays.asList(type.getDeclaredMethods()));
                } catch (Throwable ignored) {
                    // 忽略加载不了的类
                }
            }
        } catch (IOException e) {
            return out;
        }
        return out;
    }

    /**
     * 取端点的完整请求路径（类级前缀 + 方法级映射）。
     *
     * <p><b>这里踩过一个坑</b>：早先的实现是「遍历方法上的注解，取第一个带 {@code value}
     * 属性的」，而 {@code @Operation} 自带 {@code value() default ""} —— 于是取到的是空串，
     * {@code endpointVosByPath("/next")} 一个端点都匹配不到，
     * R01-1「取题接口不得含答案字段」变成了<b>空通过</b>，红线形同虚设。</p>
     *
     * <p>现在只认 Spring 的映射注解，并跳过空值；类级 {@code @RequestMapping} 前缀一并拼上，
     * 这样「路径片段」匹配到的就是真实端点。</p>
     *
     * @param method 控制器方法
     * @return 完整路径；不是映射方法返回 null
     */
    private static String requestPath(Method method) {
        String mapping = null;
        for (java.lang.annotation.Annotation annotation : method.getAnnotations()) {
            Class<?> type = annotation.annotationType();
            if (!isMappingAnnotation(type)) {
                continue;
            }
            try {
                Method m = type.getMethod("value");
                Object value = m.invoke(annotation);
                if (value instanceof String[] arr && arr.length > 0 && !arr[0].trim().isEmpty()) {
                    mapping = arr[0].trim();
                    break;
                }
            } catch (Throwable ignored) {
                // 没有 value 属性的注解，忽略
            }
        }
        if (mapping == null) {
            return null;
        }
        return classPrefix(method.getDeclaringClass()) + mapping;
    }

    /**
     * 是否是 Spring 的映射注解。
     */
    private static boolean isMappingAnnotation(Class<?> type) {
        return type == org.springframework.web.bind.annotation.RequestMapping.class
                || type == org.springframework.web.bind.annotation.GetMapping.class
                || type == org.springframework.web.bind.annotation.PostMapping.class
                || type == org.springframework.web.bind.annotation.PutMapping.class
                || type == org.springframework.web.bind.annotation.DeleteMapping.class
                || type == org.springframework.web.bind.annotation.PatchMapping.class;
    }

    /**
     * 类级 {@code @RequestMapping} 前缀。
     */
    private static String classPrefix(Class<?> declaring) {
        if (declaring == null) {
            return "";
        }
        org.springframework.web.bind.annotation.RequestMapping classMapping =
                declaring.getAnnotation(org.springframework.web.bind.annotation.RequestMapping.class);
        if (classMapping == null || classMapping.value().length == 0) {
            return "";
        }
        String prefix = classMapping.value()[0];
        return prefix == null ? "" : prefix.trim();
    }

    private static Class<?> resultType(Method method) {
        Type generic = method.getGenericReturnType();
        if (!(generic instanceof ParameterizedType)) {
            return null;
        }
        Type[] args = ((ParameterizedType) generic).getActualTypeArguments();
        if (args.length == 0) {
            return null;
        }
        Type target = args[0];
        if (target instanceof Class) {
            return (Class<?>) target;
        }
        if (target instanceof ParameterizedType) {
            Type raw = ((ParameterizedType) target).getRawType();
            if (raw instanceof Class) {
                return (Class<?>) raw;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ 内部

    private static List<String> chineseStringLiterals(String content) {
        List<String> out = new ArrayList<>();
        Matcher m = JAVA_STRING.matcher(content);
        while (m.find()) {
            String raw = m.group(1);
            if (raw.codePoints().anyMatch(c -> c >= 0x4e00 && c <= 0x9fa5)) {
                out.add(raw);
            }
        }
        return out;
    }

    private static List<String> frontendChineseTexts(String content) {
        List<String> out = new ArrayList<>();
        Matcher m = VUE_TEXT.matcher(content);
        while (m.find()) {
            out.add(m.group(1));
        }
        Matcher s = JAVA_STRING.matcher(content);
        while (s.find()) {
            String raw = s.group(1);
            if (raw.codePoints().anyMatch(c -> c >= 0x4e00 && c <= 0x9fa5)) {
                out.add(raw);
            }
        }
        return out;
    }

    private static String rel(Path file) {
        Path root = repoRoot();
        try {
            return root.relativize(file).toString().replace('\\', '/');
        } catch (IllegalArgumentException e) {
            return file.toString();
        }
    }
}
