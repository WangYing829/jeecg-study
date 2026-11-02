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
 * **护眼状态必须是「全局唯一的一份」** 的可执行断言套件。
 *
 * <p><b>真实踩过的坑（2026-10-31，P2 浏览器取证 TC-08-20 截图时发现）</b>：
 * 护眼设置页读不到「提醒间隔」，页面永远显示「还没有设置提醒间隔」。连查三轮才定位清楚，
 * 根因是<b>组件之间传值方向</b>连环踩错：</p>
 * <ol>
 *   <li><b>provide / inject 方向反了</b>：页面模板里写的是 {@code <StudyLayout>}，
 *       布局是页面的<b>子</b>组件；而 provide / inject 的方向是「父 provide → 子 inject」，
 *       布局 provide 的东西页面（父级）永远 inject 不到，只会拿到 null；</li>
 *   <li><b>defineExpose + 模板 ref 也不可靠</b>：模板 ref 拿到的是 Vue 的<b>深层 reactive 代理</b>，
 *       代理会把嵌套对象里的 ref <b>自动解包</b>——{@code eye.eyeIntervalMin} 拿到的是数字而不是 Ref，
 *       再写 {@code .value} 就永远是 undefined；</li>
 *   <li><b>生命周期钩子绑错组件</b>：护眼会话的 {@code start()} 若写在状态工厂里，
 *       {@code onMounted} 会绑定到「第一个调用工厂的组件」身上；
 *       路由从别的学习页切到护眼页时，布局挂载不再触发 {@code start()}，会话静默停摆。</li>
 * </ol>
 *
 * <p>本类把这三条约束落成源码级断言：</p>
 * <ol>
 *   <li>护眼状态模块必须有<b>模块级单例缓存</b>，并导出一个共享入口；</li>
 *   <li>布局与护眼设置页必须调用<b>同一个</b>共享入口；</li>
 *   <li>护眼状态模块内<b>不得</b>注册 {@code onMounted} / {@code onBeforeUnmount}（生命周期归布局）；</li>
 *   <li>布局必须显式持有会话的 start / stop；</li>
 *   <li>护眼设置页<b>不得</b>用 inject / defineExpose 取护眼状态。</li>
 * </ol>
 *
 * @author jeecg-study
 */
class StudyEyeSharedStateTest {

    /** 护眼状态模块（相对前端 src） */
    private static final String EYE_STATE = "components/study/StudyEyeState.ts";

    /** 学习端布局（相对前端 src） */
    private static final String STUDY_LAYOUT = "layouts/study/index.vue";

    /** 护眼设置页（相对前端 src） */
    private static final String EYE_PAGE = "views/study/eye/index.vue";

    /** 模块级单例缓存：let xxx: StudyEyeState | null = null; */
    private static final Pattern SINGLETON_CACHE = Pattern.compile(
            "let\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*:\\s*StudyEyeState\\s*\\|\\s*null\\s*=\\s*null");

    /** 导出的函数 */
    private static final Pattern EXPORTED_FUNCTION = Pattern.compile(
            "export\\s+function\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");

    /** 生命周期钩子 */
    private static final Pattern LIFECYCLE_HOOK = Pattern.compile("onMounted|onBeforeUnmount");

    // ================================================================= 扫描有效性

    @Test
    @DisplayName("扫描有效性：护眼状态模块、学习端布局、护眼设置页都必须存在且非空")
    void sourceFilesMustExist() {
        Path state = RedLineScanner.frontendRoot().resolve(EYE_STATE);
        Path layout = RedLineScanner.frontendRoot().resolve(STUDY_LAYOUT);
        Path page = RedLineScanner.frontendRoot().resolve(EYE_PAGE);

        assertThat(Files.isRegularFile(state)).as("护眼状态模块必须存在：" + EYE_STATE).isTrue();
        assertThat(Files.isRegularFile(layout)).as("学习端布局必须存在：" + STUDY_LAYOUT).isTrue();
        assertThat(Files.isRegularFile(page)).as("护眼设置页必须存在：" + EYE_PAGE).isTrue();

        assertThat(RedLineScanner.read(state).trim()).as("扫描有效性：护眼状态模块内容非空").isNotEmpty();
        assertThat(RedLineScanner.read(layout).trim()).as("扫描有效性：学习端布局内容非空").isNotEmpty();
        assertThat(RedLineScanner.read(page).trim()).as("扫描有效性：护眼设置页内容非空").isNotEmpty();

        assertThat(singletonCacheName())
                .as("扫描有效性：必须能从 " + EYE_STATE + " 解析出模块级单例缓存变量")
                .isNotEmpty();
        assertThat(sharedAccessorName())
                .as("扫描有效性：必须能从 " + EYE_STATE + " 解析出共享护眼状态的导出入口")
                .isNotEmpty();
    }

    // ================================================================= 断言 1：全局唯一实例

    @Test
    @DisplayName("护眼状态必须是全局唯一的一份：布局与护眼设置页必须调用同一个共享入口")
    void eyeStateMustBeSharedSingleton() {
        String cache = singletonCacheName();
        String accessor = sharedAccessorName();

        Path layout = RedLineScanner.frontendRoot().resolve(STUDY_LAYOUT);
        Path page = RedLineScanner.frontendRoot().resolve(EYE_PAGE);

        List<String> problems = new ArrayList<>();

        if (!RedLineScanner.read(layout).contains(accessor)) {
            problems.add(STUDY_LAYOUT + " → 没有调用共享护眼状态入口「" + accessor + "」");
        }
        if (!RedLineScanner.read(page).contains(accessor)) {
            problems.add(EYE_PAGE + " → 没有调用共享护眼状态入口「" + accessor + "」");
        }
        // 单例缓存必须真的被共享入口用上（否则只是声明了一个没人用的变量）
        String state = RedLineScanner.read(RedLineScanner.frontendRoot().resolve(EYE_STATE));
        if (!state.contains(cache)) {
            problems.add(EYE_STATE + " → 单例缓存变量「" + cache + "」没有被共享入口使用");
        }

        assertThat(problems)
                .as("护眼状态必须全局唯一：布局与护眼设置页各建一份的话，"
                        + "页面读到的永远是另一个实例（提醒间隔读不到、蓝光开关存不进去）")
                .isEmpty();
    }

    // ================================================================= 断言 2：生命周期归布局

    @Test
    @DisplayName("护眼状态模块不得注册生命周期钩子：会话 start/stop 必须由学习端布局持有")
    void eyeStateModuleMustNotBindLifecycle() {
        // 先剥注释：文档里可以写「这里不要用 onMounted」，判定必须只针对真实代码
        String state = stripComments(RedLineScanner.read(RedLineScanner.frontendRoot().resolve(EYE_STATE)));
        Matcher matcher = LIFECYCLE_HOOK.matcher(state);

        List<String> hits = new ArrayList<>();
        while (matcher.find()) {
            hits.add(matcher.group());
        }

        assertThat(hits)
                .as("护眼状态模块内不得出现 onMounted / onBeforeUnmount："
                        + "生命周期钩子会绑定到「第一个调用状态工厂的组件」身上，"
                        + "路由切换后布局挂载不再触发 start()，护眼会话静默停摆"
                        + "（页面表现为「还没有设置提醒间隔」）")
                .isEmpty();

        String layout = RedLineScanner.read(RedLineScanner.frontendRoot().resolve(STUDY_LAYOUT));
        assertThat(layout)
                .as("学习端布局必须显式持有护眼会话的启动")
                .contains("onMounted(");
        assertThat(layout)
                .as("学习端布局必须显式启动护眼会话")
                .contains(".start()");
        assertThat(layout)
                .as("学习端布局必须显式持有护眼会话的停止")
                .contains("onBeforeUnmount(");
        assertThat(layout)
                .as("学习端布局必须显式停止护眼会话")
                .contains(".stop()");
    }

    // ================================================================= 断言 3：页面不得靠 inject / expose 取状态

    @Test
    @DisplayName("护眼设置页不得用 inject / defineExpose 取护眼状态")
    void eyePageMustNotUseInjectOrExpose() {
        String page = stripComments(RedLineScanner.read(RedLineScanner.frontendRoot().resolve(EYE_PAGE)));

        List<String> problems = new ArrayList<>();
        if (page.contains("inject(")) {
            problems.add(EYE_PAGE + " → 出现了 inject(...)：布局是页面的**子**组件，"
                    + "provide/inject 方向是「父 provide → 子 inject」，页面永远 inject 不到");
        }
        if (page.contains("STUDY_EYE_STATE_KEY")) {
            problems.add(EYE_PAGE + " → 仍引用注入键 STUDY_EYE_STATE_KEY（同上，方向反了）");
        }
        if (page.contains("defineExpose")) {
            problems.add(EYE_PAGE + " → 出现了 defineExpose：模板 ref 拿到的是深层 reactive 代理，"
                    + "会把嵌套 ref 自动解包，写 .value 反而永远是 undefined");
        }

        assertThat(problems)
                .as("护眼设置页必须通过「共享单例」拿状态："
                        + "provide/inject 方向反了（拿不到），defineExpose + 模板 ref 会被深层 reactive 解包（读到 undefined）")
                .isEmpty();
    }

    // ------------------------------------------------------------------ 工具

    /**
     * 解析模块级单例缓存变量名。
     *
     * @return 缓存变量名；没有则返回空串
     */
    private static String singletonCacheName() {
        Matcher matcher = SINGLETON_CACHE.matcher(RedLineScanner.read(RedLineScanner.frontendRoot().resolve(EYE_STATE)));
        return matcher.find() ? matcher.group(1) : "";
    }

    /**
     * 解析「共享护眼状态」的导出入口名：导出的函数里，函数体引用了单例缓存变量的那个。
     *
     * @return 入口函数名；没有则返回空串
     */
    private static String sharedAccessorName() {
        String cache = singletonCacheName();
        if (cache.isEmpty()) {
            return "";
        }
        String content = RedLineScanner.read(RedLineScanner.frontendRoot().resolve(EYE_STATE));
        Set<String> found = new LinkedHashSet<>();
        Matcher matcher = EXPORTED_FUNCTION.matcher(content);
        while (matcher.find()) {
            String name = matcher.group(1);
            int start = matcher.start();
            int bodyEnd = content.indexOf("\n}", start);
            if (bodyEnd < 0) {
                bodyEnd = content.length();
            }
            if (content.substring(start, bodyEnd).contains(cache)) {
                found.add(name);
            }
        }
        return found.isEmpty() ? "" : found.iterator().next();
    }

    /**
     * 去掉 HTML 注释、块注释与行注释，避免注释里的说明文字被当成代码命中。
     *
     * @param content 原始内容
     * @return 去掉注释后的内容
     */
    private static String stripComments(String content) {
        String out = content;
        out = out.replaceAll("(?s)<!--.*?-->", " ");
        out = out.replaceAll("(?s)/\\*.*?\\*/", " ");
        out = out.replaceAll("(?m)//[^\\n]*", " ");
        return out;
    }
}
