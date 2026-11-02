package org.jeecg.modules.study.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.jeecg.modules.study.redline.RedLineScanner;

/**
 * **家长端外壳必须是「独立全屏层」** 的可执行断言套件。
 *
 * <p><b>真实踩过的坑（2026-10-31，P2 浏览器取证 TC-08-20 截图时发现）</b>：
 * 家长端 3 个页面在 {@code < 992px}（手机 / 平板）下整片不可用——
 * 上游管理端布局在窄屏时把侧栏渲染成<b>常开的抽屉 + 遮罩</b>
 * （{@code layouts/default/sider/index.vue:2-12}，{@code :open="!getCollapsed"}），
 * 抽屉 210px 宽直接压在内容上、遮罩把整页压灰，家长端看到的是「半张灰 + 一块黑」。
 * 桌面视口（≥992px）看不出来，所以只在浏览器取证时才暴露。</p>
 *
 * <p>本类把「家长端不依赖管理端布局」的约束落成源码级断言：</p>
 * <ol>
 *   <li>家长端外壳必须是<b>全屏固定层</b>（{@code position: fixed} + {@code inset: 0}），
 *       z-index 必须高于 antd 抽屉遮罩（1000）；</li>
 *   <li>外壳必须<b>自带页面导航</b>——家长端没有侧栏，顶部条是唯一的页面入口；</li>
 *   <li>外壳必须提供<b>登出入口</b>——顶部条盖住管理端顶栏后，家长端没有别的退出路径；</li>
 *   <li>家长端 3 个页面<b>必须</b>都走这个外壳，不允许有页面绕过。</li>
 * </ol>
 *
 * @author jeecg-study
 */
class StudyParentShellTest {

    /** 家长端外壳组件（相对前端 src） */
    private static final String PARENT_SHELL = "components/study/StudyParentShell.vue";

    /** 家长端 3 个页面（相对前端 src） */
    private static final List<String> PARENT_PAGES = List.of(
            "views/study/parent/report/index.vue",
            "views/study/parent/error/index.vue",
            "views/study/parent/feedback/index.vue");

    /** 家长端页面路由（导航必须覆盖全） */
    private static final List<String> PARENT_ROUTES = List.of(
            "/study-parent/report",
            "/study-parent/error",
            "/study-parent/feedback");

    /** z-index 声明 */
    private static final Pattern Z_INDEX = Pattern.compile("z-index\\s*:\\s*(\\d+)");

    /** antd 抽屉遮罩的 z-index：外壳必须压过它 */
    private static final int DRAWER_MASK_Z_INDEX = 1000;

    // ================================================================= 扫描有效性

    @Test
    @DisplayName("扫描有效性：家长端外壳与 3 个家长端页面都必须存在且非空")
    void sourceFilesMustExist() {
        Path shell = RedLineScanner.frontendRoot().resolve(PARENT_SHELL);
        assertThat(Files.isRegularFile(shell)).as("家长端外壳必须存在：" + PARENT_SHELL).isTrue();
        assertThat(RedLineScanner.read(shell).trim()).as("扫描有效性：家长端外壳内容非空").isNotEmpty();

        for (String page : PARENT_PAGES) {
            Path file = RedLineScanner.frontendRoot().resolve(page);
            assertThat(Files.isRegularFile(file)).as("家长端页面必须存在：" + page).isTrue();
            assertThat(RedLineScanner.read(file).trim()).as("扫描有效性：家长端页面内容非空：" + page).isNotEmpty();
        }
    }

    // ================================================================= 断言 1：全屏固定层

    @Test
    @DisplayName("家长端外壳必须是全屏固定层，且 z-index 高于管理端抽屉遮罩")
    void parentShellMustCoverAdminLayout() {
        // 先剥注释：文档里可以写「本外壳是 position: fixed」，判定必须只针对真实样式代码
        String shell = stripComments(RedLineScanner.read(RedLineScanner.frontendRoot().resolve(PARENT_SHELL)));

        assertThat(shell)
                .as("家长端外壳必须 position: fixed：管理端在 <992px 时把侧栏渲染成常开的抽屉 + 遮罩，"
                        + "不脱离文档流就会被整片盖住（家长端在手机上直接不可用）")
                .contains("position: fixed");
        assertThat(shell)
                .as("家长端外壳必须 inset: 0（占满视口），不能只占内容区")
                .contains("inset: 0");

        Matcher matcher = Z_INDEX.matcher(shell);
        List<Integer> zIndexes = new ArrayList<>();
        while (matcher.find()) {
            zIndexes.add(Integer.parseInt(matcher.group(1)));
        }

        assertThat(zIndexes)
                .as("扫描有效性：家长端外壳必须声明 z-index")
                .isNotEmpty();
        assertThat(zIndexes.stream().max(Integer::compareTo).orElse(0))
                .as("家长端外壳的 z-index 必须高于 antd 抽屉遮罩（" + DRAWER_MASK_Z_INDEX + "），"
                        + "否则窄屏下内容仍会被抽屉与遮罩盖住")
                .isGreaterThanOrEqualTo(DRAWER_MASK_Z_INDEX);
    }

    // ================================================================= 断言 2：自带页面导航

    @Test
    @DisplayName("家长端外壳必须自带页面导航（家长端无侧栏，顶部条是唯一入口）")
    void parentShellMustProvideNavigation() {
        String shell = RedLineScanner.read(RedLineScanner.frontendRoot().resolve(PARENT_SHELL));

        List<String> missing = new ArrayList<>();
        for (String route : PARENT_ROUTES) {
            if (!shell.contains(route)) {
                missing.add(route);
            }
        }

        assertThat(missing)
                .as("家长端没有侧栏（设计规范 §9.6 / 自检清单 #27），外壳必须把 3 个页面的入口都带上，"
                        + "否则家长在页面之间无处可去；缺：" + missing)
                .isEmpty();

        assertThat(shell)
                .as("导航必须用 router-link 渲染（不能只写死路径字符串）")
                .contains("router-link");
    }

    // ================================================================= 断言 3：登出入口

    @Test
    @DisplayName("家长端外壳必须提供登出入口")
    void parentShellMustProvideLogout() {
        String shell = RedLineScanner.read(RedLineScanner.frontendRoot().resolve(PARENT_SHELL));

        assertThat(shell)
                .as("家长端外壳盖住管理端顶栏后，家长端就没有别的退出路径了，必须自带登出入口")
                .contains("confirmLoginOut");
    }

    // ================================================================= 断言 4：3 个页面都走外壳

    @Test
    @DisplayName("家长端 3 个页面都必须走家长端外壳")
    void allParentPagesMustUseShell() {
        List<String> problems = new ArrayList<>();
        for (String page : PARENT_PAGES) {
            String content = RedLineScanner.read(RedLineScanner.frontendRoot().resolve(page));
            if (!content.contains("StudyParentShell")) {
                problems.add(page + " → 没有使用家长端外壳 StudyParentShell");
            }
        }

        assertThat(problems)
                .as("家长端页面必须统一走家长端外壳，否则会出现「有的页面有导航、有的页面没有」")
                .isEmpty();
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
