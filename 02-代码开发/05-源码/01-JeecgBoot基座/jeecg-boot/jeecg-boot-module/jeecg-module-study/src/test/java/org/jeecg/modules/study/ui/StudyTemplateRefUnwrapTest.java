package org.jeecg.modules.study.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
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

/**
 * **模板里不得用「嵌套对象」访问 ref** 的可执行断言套件。
 *
 * <p><b>真实踩过的坑（2026-10-31，P2 浏览器取证 TC-08-20 截图时发现）</b>：
 * Vue 3 只对 setup 返回对象的<b>顶层</b>属性做 ref 自动解包（{@code proxyRefs}）。
 * 把一个装着 ref 的普通对象整个返回（{@code return { eye }}），模板里写
 * {@code eye.restRequired} 拿到的是 <b>Ref 对象本身</b>，不是 {@code ref.value}——
 * Ref 实例恒为真，于是：</p>
 * <ul>
 *   <li>护眼休息遮罩<b>永远盖住整个学习端</b>，孩子连题都做不了；</li>
 *   <li>点「我休息好了」也解不开（服务端已经返回 {@code restRequired=false}，
 *       但模板仍然判真）；</li>
 *   <li>服务端实测 {@code restRequired=false}，浏览器却显示遮罩——
 *       <b>接口对、页面错</b>，静态检查与后端单测全都看不出来。</li>
 * </ul>
 *
 * <p>本类把这条约束落成源码级断言：</p>
 * <ol>
 *   <li>从 {@code StudyEyeState.ts} 里解析出 {@code useStudyEyeState()} 真正暴露的 ref 成员；</li>
 *   <li>扫描学习端全部 {@code .vue} 的 <b>template 段</b>，禁止出现
 *       {@code 某对象.某ref成员} 这种「非 .value 的嵌套访问」；</li>
 *   <li>额外要求学习端布局把护眼遮罩的入参绑到<b>顶层 ref</b> 上。</li>
 * </ol>
 *
 * @author jeecg-study
 */
class StudyTemplateRefUnwrapTest {

    /** 护眼状态模块（相对前端 src） */
    private static final String EYE_STATE = "components/study/StudyEyeState.ts";

    /** 护眼护罩组件（相对前端 src） */
    private static final String EYE_SHIELD = "components/study/StudyEyeShield.vue";

    /** 学习端布局（相对前端 src） */
    private static final String STUDY_LAYOUT = "layouts/study/index.vue";

    /** 护眼状态里创建的 ref 变量 */
    private static final Pattern REF_DECL = Pattern.compile("const\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*ref(?:<[^>]*>)?\\s*\\(");

    /** useStudyEyeState 的返回对象 */
    private static final Pattern FACTORY_RETURN = Pattern.compile(
            "export\\s+function\\s+useStudyEyeState\\s*\\(\\)[\\s\\S]*?return\\s*\\{([^}]*)\\}");

    /** template 段 */
    private static final Pattern TEMPLATE_BLOCK = Pattern.compile("(?s)<template>(.*?)</template>");

    /** 组件里的定时器——护眼遮罩不允许自动消失（R-03） */
    private static final Pattern TIMER = Pattern.compile("setTimeout|setInterval");

    // ================================================================= 扫描有效性

    @Test
    @DisplayName("扫描有效性：护眼状态模块、护罩组件、学习端布局都必须存在且非空")
    void sourceFilesMustExist() {
        Path state = RedLineScanner.frontendRoot().resolve(EYE_STATE);
        Path shield = RedLineScanner.frontendRoot().resolve(EYE_SHIELD);
        Path layout = RedLineScanner.frontendRoot().resolve(STUDY_LAYOUT);

        assertThat(Files.isRegularFile(state)).as("护眼状态模块必须存在：" + EYE_STATE).isTrue();
        assertThat(Files.isRegularFile(shield)).as("护眼护罩组件必须存在：" + EYE_SHIELD).isTrue();
        assertThat(Files.isRegularFile(layout)).as("学习端布局必须存在：" + STUDY_LAYOUT).isTrue();

        assertThat(RedLineScanner.read(state).trim()).as("扫描有效性：护眼状态模块内容非空").isNotEmpty();
        assertThat(RedLineScanner.read(shield).trim()).as("扫描有效性：护眼护罩组件内容非空").isNotEmpty();
        assertThat(RedLineScanner.read(layout).trim()).as("扫描有效性：学习端布局内容非空").isNotEmpty();

        assertThat(studyVueFiles())
                .as("扫描有效性：学习端 .vue 集合必须非空，否则「逐文件断言」会退化成空跑")
                .isNotEmpty();

        assertThat(exposedRefMembers())
                .as("扫描有效性：必须能从 " + EYE_STATE + " 解析出 useStudyEyeState 暴露的 ref 成员")
                .isNotEmpty();
    }

    // ================================================================= 断言 1：学习端布局的护眼入参必须绑顶层 ref

    @Test
    @DisplayName("学习端布局的护眼遮罩入参必须绑到 setup 顶层的 ref（不能写 eye.restRequired）")
    void studyLayoutMustBindTopLevelRef() {
        String template = templateOf(RedLineScanner.frontendRoot().resolve(STUDY_LAYOUT));
        assertThat(template).as("学习端布局必须有 template 段").isNotEmpty();

        Set<String> members = exposedRefMembers();

        assertThat(bindExpression(template, "rest-required"))
                .as("学习端布局必须绑定 :rest-required")
                .isNotNull();
        assertThat(bindExpression(template, "rest-prompt"))
                .as("学习端布局必须绑定 :rest-prompt")
                .isNotNull();

        List<String> problems = new ArrayList<>();
        for (String prop : new String[] { "rest-required", "rest-prompt" }) {
            String expr = bindExpression(template, prop);
            if (expr == null) {
                problems.add(STUDY_LAYOUT + " → 缺少 :" + prop + " 绑定");
                continue;
            }
            if (expr.contains(".")) {
                problems.add(STUDY_LAYOUT + " → :" + prop + " 绑的是「" + expr + "」，"
                        + "这是嵌套对象上的 ref，Vue 不会解包，拿到的是 Ref 对象本身（恒为真）");
            } else if (!members.contains(expr)) {
                problems.add(STUDY_LAYOUT + " → :" + prop + " 绑的是「" + expr
                        + "」，它不是 useStudyEyeState 暴露的 ref 成员");
            }
        }

        assertThat(problems)
                .as("护眼遮罩的显隐必须由「顶层 ref」驱动：Vue 只解包 setup 返回值的顶层 ref，"
                        + "嵌套对象上的 ref 恒为真 → 遮罩永久盖住学习端，且「我休息好了」解不开（R-03 变死锁）")
                .isEmpty();
    }

    // ================================================================= 断言 2：学习端模板不得嵌套访问 ref

    @Test
    @DisplayName("学习端模板不得用「对象.ref成员」访问 ref（Vue 不解包，恒为真）")
    void studyTemplatesMustNotAccessNestedRefs() {
        Set<String> members = exposedRefMembers();
        List<String> problems = new ArrayList<>();

        for (Path file : studyVueFiles()) {
            String template = templateOf(file);
            if (template.isEmpty()) {
                continue;
            }
            for (String member : members) {
                // X.member 但后面不是 .value —— 说明是「没解包的 ref」
                Pattern nested = Pattern.compile("\\b([A-Za-z_][A-Za-z0-9_]*)\\." + Pattern.quote(member) + "\\b(?!\\.value)");
                Matcher matcher = nested.matcher(template);
                while (matcher.find()) {
                    problems.add(RedLineScanner.frontendRelative(file) + " → 模板里出现「"
                            + matcher.group(1) + "." + member + "」"
                            + "（嵌套 ref 不会被 Vue 自动解包，恒为真；请改成顶层 ref 或显式 .value）");
                }
            }
        }

        assertThat(problems)
                .as("学习端模板里的 ref 必须放在 setup 返回对象的顶层，或显式写 .value；"
                        + "嵌套访问会拿到 Ref 对象本身，恒为真（护眼遮罩曾因此永久遮挡整个学习端）")
                .isEmpty();
    }

    // ================================================================= 断言 3：护眼遮罩只能由用户显式确认清除

    @Test
    @DisplayName("R-03：护眼遮罩不得自动消失，显隐只能由 restRequired 驱动")
    void eyeShieldMustNotAutoDismiss() {
        Path shield = RedLineScanner.frontendRoot().resolve(EYE_SHIELD);
        String content = RedLineScanner.read(shield);

        assertThat(content)
                .as("护眼遮罩必须由 restRequired 驱动显隐")
                .contains("v-if=\"restRequired\"");

        assertThat(TIMER.matcher(content).find())
                .as("护眼遮罩内不允许任何定时器：遮罩只能由用户点「我休息好了」清除（R-03）")
                .isFalse();

        assertThat(content)
                .as("护眼遮罩必须保留「我休息好了」这个唯一的清除入口")
                .contains("我休息好了");
    }

    // ------------------------------------------------------------------ 工具

    /**
     * 解析 {@code useStudyEyeState()} 真正暴露的 ref 成员名。
     *
     * @return ref 成员名集合（例如 restRequired / restPrompt / blueLightOn / sessionId）
     */
    private static Set<String> exposedRefMembers() {
        String content = RedLineScanner.read(RedLineScanner.frontendRoot().resolve(EYE_STATE));
        Set<String> refs = new LinkedHashSet<>();
        Matcher refMatcher = REF_DECL.matcher(content);
        while (refMatcher.find()) {
            refs.add(refMatcher.group(1));
        }
        Set<String> out = new LinkedHashSet<>();
        Matcher retMatcher = FACTORY_RETURN.matcher(content);
        if (retMatcher.find()) {
            for (String part : retMatcher.group(1).split(",")) {
                String name = part.trim();
                if (refs.contains(name)) {
                    out.add(name);
                }
            }
        }
        return out;
    }

    /** 取 {@code :prop="expr"} 形式的绑定表达式；没有则返回 null */
    private static String bindExpression(String template, String prop) {
        Matcher matcher = Pattern.compile(":\\s*" + Pattern.quote(prop) + "\\s*=\\s*\"([^\"]*)\"").matcher(template);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    /** 取 template 段正文 */
    private static String templateOf(Path file) {
        String content = RedLineScanner.read(file);
        Matcher matcher = TEMPLATE_BLOCK.matcher(content);
        return matcher.find() ? matcher.group(1) : "";
    }

    /** 学习端范围内的全部 .vue（views/study、layouts/study、components/study） */
    private static List<Path> studyVueFiles() {
        List<Path> out = new ArrayList<>();
        Path root = RedLineScanner.frontendRoot();
        for (String dir : new String[] { "views/study", "layouts/study", "components/study" }) {
            Path base = root.resolve(dir.replace('/', java.io.File.separatorChar));
            if (!Files.isDirectory(base)) {
                continue;
            }
            try (Stream<Path> stream = Files.walk(base)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".vue"))
                        .sorted()
                        .forEach(out::add);
            } catch (IOException e) {
                // 目录读不到就跳过，不让扫描崩掉
                continue;
            }
        }
        return out;
    }
}
