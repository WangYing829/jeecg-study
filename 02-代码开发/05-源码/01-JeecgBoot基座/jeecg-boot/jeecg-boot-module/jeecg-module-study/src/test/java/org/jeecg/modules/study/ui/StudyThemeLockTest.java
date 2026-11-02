package org.jeecg.modules.study.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.jeecg.modules.study.redline.RedLineScanner;

/**
 * **TC-08-22「学习端主题锁定」的可执行断言套件**。
 *
 * <p>需求原文：<b>系统切 dark 后进入学习端，学习端仍为浅色，无半深半浅</b>
 * （设计规范 §4.2③：学习端与孩子端锁定浅色，根节点显式 {@code data-theme="light"}）。</p>
 *
 * <p><b>真实踩过的坑（2026-10-31，P2 浏览器取证时发现）</b>：
 * 全局主题初始化是<b>异步</b>的——{@code src/logics/initAppConfig.ts:53} 调用
 * {@code updateDarkTheme(darkMode)}，它可能在学习端布局挂载<b>之后</b>才跑完，
 * 把根节点的 {@code data-theme} 改回 {@code dark} 并加上 {@code dark} class。
 * 原实现只在 {@code onMounted} 里锁一次，于是被整个冲掉：
 * 实测同一个孩子端里 7 个页面有 1 个是暗的（谁加载慢谁输，纯竞态）。</p>
 *
 * <p>修复：改成「观察者兜底」——用 {@code MutationObserver} 盯着根节点的
 * {@code data-theme} / {@code class}，一旦被改暗就立刻压回浅色；
 * 卸载时按<b>当前全局暗色状态</b>还原，而不是挂载时的快照。</p>
 *
 * <p>本类把这条约束落成源码级断言：<b>只要有人把「观察者兜底」删掉，
 * 或者把卸载还原改回「挂载快照」，断言立刻变红</b>——避免这个竞态再悄悄回来。</p>
 *
 * @author jeecg-study
 */
class StudyThemeLockTest {

    /** 学习端主题锁定模块（相对前端 src） */
    private static final String THEME_LOCK = "components/study/StudyThemeLock.ts";

    /** 学习端布局（相对前端 src） */
    private static final String STUDY_LAYOUT = "layouts/study/index.vue";

    /** 全局暗色开关写入的裸 localStorage 键 */
    private static final String GLOBAL_DARK_KEY = "__APP__DARK__MODE__";

    /** MutationObserver 兜底 */
    private static final Pattern MUTATION_OBSERVER = Pattern.compile("new\\s+MutationObserver");

    /** 强制浅色的动作：写 data-theme=light */
    private static final Pattern FORCE_LIGHT = Pattern.compile(
            "setAttribute\\(\\s*['\"]data-theme['\"]\\s*,\\s*['\"]light['\"]");

    /** 移除 dark class */
    private static final Pattern REMOVE_DARK = Pattern.compile("classList\\.remove\\(\\s*['\"]dark['\"]");

    /** 卸载还原时的「挂载快照」变量——用了就说明又退回了旧写法 */
    private static final Pattern STALE_SNAPSHOT = Pattern.compile(
            "previous(DarkClass|Theme)\\s*[:=]");

    // ================================================================= 扫描有效性

    @Test
    @DisplayName("扫描有效性：主题锁定模块与学习端布局必须都存在")
    void themeLockFilesMustExist() {
        Path lock = RedLineScanner.frontendRoot().resolve(THEME_LOCK);
        Path layout = RedLineScanner.frontendRoot().resolve(STUDY_LAYOUT);
        assertThat(Files.isRegularFile(lock)).as("主题锁定模块必须存在：" + THEME_LOCK).isTrue();
        assertThat(Files.isRegularFile(layout)).as("学习端布局必须存在：" + STUDY_LAYOUT).isTrue();

        String lockContent = RedLineScanner.read(lock);
        String layoutContent = RedLineScanner.read(layout);
        assertThat(lockContent.trim()).as("扫描有效性：主题锁定模块内容非空").isNotEmpty();
        assertThat(layoutContent.trim()).as("扫描有效性：学习端布局内容非空").isNotEmpty();

        assertThat(layoutContent)
                .as("学习端布局必须真正调用 useStudyThemeLock，否则锁定根本没生效")
                .contains("useStudyThemeLock");
    }

    // ================================================================= 断言：观察者兜底

    @Test
    @DisplayName("TC-08-22：主题锁定必须有 MutationObserver 兜底（防全局异步暗色初始化冲掉）")
    void themeLockMustObserveRootAttributes() {
        String content = RedLineScanner.read(RedLineScanner.frontendRoot().resolve(THEME_LOCK));

        assertThat(MUTATION_OBSERVER.matcher(content).find())
                .as("TC-08-22：全局主题初始化是异步的（logics/initAppConfig.ts:53 → updateDarkTheme），"
                        + "只锁一次会被冲掉导致学习端偶发变暗；必须有 MutationObserver 兜底")
                .isTrue();

        assertThat(content)
                .as("观察器必须监听根节点的 data-theme / class 两个属性")
                .contains("attributeFilter")
                .contains("data-theme")
                .contains("'class'");

        assertThat(content)
                .as("观察器必须在卸载时断开，否则离开学习端后仍会压暗全局主题")
                .contains("disconnect");

        assertThat(FORCE_LIGHT.matcher(content).find())
                .as("锁定必须把根节点写成 data-theme=\"light\"")
                .isTrue();

        assertThat(REMOVE_DARK.matcher(content).find())
                .as("锁定必须移除根节点的 dark class（否则暗色样式依然命中）")
                .isTrue();
    }

    // ================================================================= 断言：卸载还原

    @Test
    @DisplayName("TC-08-22：卸载时按「当前全局暗色状态」还原，而不是挂载时的快照")
    void themeLockMustRestoreByCurrentGlobalState() {
        String content = RedLineScanner.read(RedLineScanner.frontendRoot().resolve(THEME_LOCK));

        assertThat(content)
                .as("还原必须读全局暗色开关的真实来源：" + GLOBAL_DARK_KEY)
                .contains(GLOBAL_DARK_KEY);

        assertThat(STALE_SNAPSHOT.matcher(content).find())
                .as("不得再用「挂载时的快照」还原：学习端挂载期间全局开关可能被改成 dark，"
                        + "还原旧快照会把家长端 / 管理端留在浅色（半深半浅）")
                .isFalse();
    }

    // ================================================================= 断言：学习端不提供切换入口

    @Test
    @DisplayName("TC-08-22 / 设计规范 §4.2③：学习端不提供浅色 / 深色切换入口")
    void studyEndMustNotOfferThemeSwitch() {
        Path layout = RedLineScanner.frontendRoot().resolve(STUDY_LAYOUT);
        String content = RedLineScanner.read(layout);
        assertThat(content)
                .as("学习端布局不得引入暗色切换组件（AppDarkModeToggle）")
                .doesNotContain("AppDarkModeToggle");

        Path lock = RedLineScanner.frontendRoot().resolve(THEME_LOCK);
        String lockContent = RedLineScanner.read(lock);
        assertThat(lockContent)
                .as("主题锁定模块只做「锁定」，不提供切换入口")
                .doesNotContain("AppDarkModeToggle");
    }

    // ================================================================= 断言：学习端页面必须真的挂上 StudyLayout

    /** {@code components: { ... StudyLayout ... }} */
    private static final Pattern COMPONENTS_WITH_LAYOUT = Pattern.compile(
            "components\\s*:\\s*\\{[^}]*StudyLayout");

    /**
     * 每个学习端页面都<b>必须真的挂上</b> StudyLayout 布局。
     *
     * <p><b>真实踩过的坑（2026-10-31，P2 浏览器取证 TC-08-22 暗色遍历时发现）</b>：
     * {@code views/study/writing/scenario/index.vue} 用 {@code defineComponent + setup()}
     * 写法，却<b>漏了 {@code components} 注册</b>。局部 import 的组件在 Options API 写法下
     * 不会自动注册，于是 Vue 把 {@code <StudyLayout>} 当成未知 HTML 元素渲染——
     * <b>页面内容照样出得来，但布局外壳整层丢失</b>：</p>
     * <ul>
     *   <li>学习端顶栏（模块名 / 连续天数 / 护眼状态）不见了；</li>
     *   <li><b>护眼护罩没挂载</b>（R-03 失效）；</li>
     *   <li><b>底部主操作条没渲染</b>——第三步「写自己的经历」的提交按钮就在 footer slot 里，
     *       等于<b>孩子写完作文根本提交不了</b>；</li>
     *   <li>主题锁定没执行 → 暗色下学习端变暗（TC-08-22 失效）。</li>
     * </ul>
     *
     * <p>这个缺陷在静态检查里完全看不出来（页面能渲染、接口能通、单测全绿），
     * 只有真正把页面渲染出来、在暗色下走一遍才会暴露——所以这条断言必须写死。</p>
     */
    @Test
    @DisplayName("学习端页面必须注册 StudyLayout 组件（否则布局外壳整层丢失）")
    void everyStudyPageMustRegisterStudyLayout() {
        List<Path> pages = studyPageFiles();
        assertThat(pages)
                .as("扫描有效性：学习端页面集合必须非空，否则「逐页断言」会退化成空跑")
                .isNotEmpty();

        List<String> problems = new ArrayList<>();
        for (Path page : pages) {
            String rel = RedLineScanner.frontendRelative(page);
            String content = RedLineScanner.read(page);
            if (!content.contains("<StudyLayout")) {
                continue;
            }
            // <script setup> 写法会自动注册局部 import 的组件；Options API 写法必须显式注册
            boolean scriptSetup = content.contains("<script setup");
            boolean registered = COMPONENTS_WITH_LAYOUT.matcher(content).find();
            if (!scriptSetup && !registered) {
                problems.add(rel + " → 用了 <StudyLayout> 却没有在 components 里注册"
                        + "（局部 import 在 defineComponent 写法下不会自动注册，"
                        + "会导致顶栏 / 护眼护罩 / 底部主操作条 / 主题锁定整层丢失）");
            }
        }

        assertThat(problems)
                .as("学习端页面必须真正挂上 StudyLayout：未注册时 Vue 会把 <StudyLayout> 当未知元素渲染，"
                        + "页面内容出得来但护眼护罩（R-03）、底部提交按钮、主题锁定（TC-08-22）全部失效")
                .isEmpty();
    }

    // ------------------------------------------------------------------ 工具

    /** 学习端页面（{@code views/study} 下所有 {@code index.vue}） */
    private static List<Path> studyPageFiles() {
        List<Path> out = new ArrayList<>();
        Path root = RedLineScanner.frontendRoot().resolve("views").resolve("study");
        if (!Files.isDirectory(root)) {
            return out;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> "index.vue".equals(p.getFileName().toString()))
                    .sorted()
                    .forEach(out::add);
        } catch (IOException e) {
            return out;
        }
        return out;
    }
}
