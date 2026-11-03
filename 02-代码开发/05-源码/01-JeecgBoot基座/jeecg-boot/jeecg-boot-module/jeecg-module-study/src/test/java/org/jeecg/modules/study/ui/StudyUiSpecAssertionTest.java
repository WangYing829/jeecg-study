package org.jeecg.modules.study.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.jeecg.modules.study.redline.RedLineScanner;

/**
 * **UI 规范的可执行断言套件**（T-VX-01）。
 *
 * <p>《产品设计规范》V1.1 的 §13 禁止项（F-01…F-25）、§14 自检清单（42 条）
 * 以及《测试用例》的 UI 类用例，被逐条落成<b>源码级静态断言</b>，
 * 与既有 {@code RedLineAssertionTest} 走同一个路子（静态扫描，不依赖浏览器）。</p>
 *
 * <p><b>扫描范围口径</b>：只扫本项目新增的三个目录——
 * {@code src/views/study/**}、{@code src/layouts/study/**}、{@code src/components/study/**}。
 * 上游管理端那几百个页面不在本项目管辖范围内，不扫。</p>
 *
 * <p><b>路径解析复用</b>：{@link RedLineScanner#frontendRoot()} 与
 * {@link RedLineScanner#read(Path)} 直接复用，不另写一套路径解析。</p>
 *
 * <p><b>闭合口径</b>：可静态判定的条目全部闭合；
 * 需要浏览器实测的（TC-08-18 多视口溢出、TC-08-20 截图取证）在本类里
 * 只留可静态化的部分，其余在《测试用例》标注为「需浏览器实测」。</p>
 *
 * @author jeecg-study
 */
class StudyUiSpecAssertionTest {

    // ------------------------------------------------------------------ 扫描范围

    /** 学习端视图目录 */
    private static final String VIEWS_STUDY = "views/study";

    /** 学习端布局目录 */
    private static final String LAYOUTS_STUDY = "layouts/study";

    /** 本项目自研组件目录 */
    private static final String COMPONENTS_STUDY = "components/study";

    /** 家长端视图目录 */
    private static final String VIEWS_PARENT = "views/study/parent";

    /**
     * 管理端视图目录。
     *
     * <p><b>为什么单独拎出来</b>：《产品设计规范》§1.3 明确
     * <b>管理端适用第 3–9 章全部</b>，而第 9–10 章是<b>学习端 / 家长端专项</b>。
     * 因此「学习端不用表格」「学习端不套管理端表单骨架」「学习端一屏一任务」
     * 这类口径<b>不适用于管理端</b>——管理端恰恰<b>必须</b>用 BasicTable、
     * 用管理端表单骨架。把它混进学习端集合，等于用学习端口径去卡管理端，
     * 断言会永远红，而且真正的违规反而被淹没。</p>
     */
    private static final String VIEWS_MANAGE = "views/study/manage";

    /** 统一操作列组件（§8.7）：操作列的标题与渲染都收在它里面 */
    private static final String ACTION_COLUMN_COMPONENT = "components/study/StudyActionColumn.vue";

    /** 枚举彩色标签组件（§8.6）：动态标签色只允许出现在它里面 */
    private static final String ENUM_TAG_COMPONENT = "components/study/StudyEnumTag.vue";

    /** 只给家长端用的自研组件（圆角按管理端上限 8px 收） */
    private static final List<String> PARENT_SHARED_COMPONENTS =
            Arrays.asList("StudyDataList.vue", "StudyStatCards.vue", "StudyParentShell.vue");

    // ------------------------------------------------------------------ 词典

    /** 排名类措辞（R-04，任何位置都不允许出现在用户可见文案里） */
    private static final List<String> RANK_WORDS =
            Arrays.asList("排名", "排行", "百分位", "超过同学", "名次", "超过…同学");

    /** 家庭内比较措辞（R-04 强化） */
    private static final List<String> FAMILY_COMPARE_WORDS =
            Arrays.asList("谁的孩子更", "比上次", "比上个月", "比另一个孩子", "比别的孩子", "更进步");

    /** 并列对比元素（R-04 / TC-41-11） */
    private static final List<String> SIDE_BY_SIDE_WORDS =
            Arrays.asList("对比", "并列", "相比", "谁更", "哪个孩子", "另一个孩子", "别的孩子");

    /** 学习端负向表述（§10.3 强制规则 4） */
    private static final List<String> NEGATIVE_COPY =
            Arrays.asList("太笨", "错误次数", "失败次数", "太粗心", "真笨");

    // ------------------------------------------------------------------ 允许值

    /** 字号阶梯（§5.2 + 学习端 §10.1 放宽到 18px） */
    private static final Set<String> ALLOWED_FONT_SIZE = Set.of("12", "13", "14", "15", "16", "18", "20");

    /** 间距刻度（§6.1） */
    private static final Set<String> ALLOWED_SPACING = Set.of("0", "4", "8", "10", "12", "16", "24", "32");

    /** 行高（§5.4） */
    private static final Set<String> ALLOWED_LINE_HEIGHT = Set.of("1.5715", "1.4", "1.2");

    /** 允许的断点（§12.1） */
    private static final Set<String> ALLOWED_BREAKPOINT = Set.of("480", "576", "768", "992", "1200", "1600");

    /** 允许的圆角（§7.1） */
    private static final Set<String> ALLOWED_RADIUS = Set.of("2", "3", "4", "6", "8", "12");

    /** 允许的阴影（§7.2，只允许这三种） */
    private static final List<String> ALLOWED_BOX_SHADOW = List.of(
            "0 2px 8px rgba(0, 0, 0, 0.08)",
            "0 2px 12px 0 rgba(0, 0, 0, 0.1)",
            "0 1px 2px rgba(0, 0, 0, 0.03)");

    /** antd 标签预设名（§8.6） */
    private static final Set<String> ALLOWED_TAG_COLOR =
            Set.of("success", "warning", "error", "blue", "processing", "default", "info");

    // ------------------------------------------------------------------ 正则

    private static final Pattern HEX_COLOR = Pattern.compile("#[0-9a-fA-F]{3,8}(?![0-9a-zA-Z])");
    private static final Pattern FONT_SIZE = Pattern.compile("font-size\\s*:\\s*(\\d+(?:\\.\\d+)?)px");
    private static final Pattern FONT_WEIGHT = Pattern.compile("font-weight\\s*:\\s*(\\d+)");
    private static final Pattern BORDER_RADIUS = Pattern.compile("border-radius\\s*:\\s*([^;}]+)");
    private static final Pattern BOX_SHADOW = Pattern.compile("box-shadow\\s*:\\s*([^;}]+)");
    private static final Pattern SPACING =
            Pattern.compile("(?<![a-z-])(padding|margin|gap|row-gap|column-gap)(-[a-z]+)?\\s*:\\s*([^;}]+)");
    private static final Pattern BORDER = Pattern.compile("(?<![a-z-])border(?!-radius)(-[a-z]+)?\\s*:\\s*([^;}]+)");
    private static final Pattern LINE_HEIGHT = Pattern.compile("line-height\\s*:\\s*([0-9.]+)");
    private static final Pattern MEDIA = Pattern.compile("@media\\s*\\(([^)]*)\\)");
    private static final Pattern Z_INDEX = Pattern.compile("z-index\\s*:\\s*(\\d+)");
    private static final Pattern ICON_ATTR = Pattern.compile("(?<![A-Za-z])icon\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern PX_NUMBER = Pattern.compile("(\\d+(?:\\.\\d+)?)px");
    private static final Pattern VUE_TEXT = Pattern.compile(">([^<>{}]*[\\u4e00-\\u9fa5][^<>{}]*)<");
    private static final Pattern QUOTED = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern FOOTER_SLOT = Pattern.compile("<template\\s+#footer>([\\s\\S]*?)</template>");

    // ================================================================= 基础

    @Test
    @DisplayName("TC-08-20 前置：本项目新增页面已就位（否则后续断言全是空跑）")
    void precondition_projectPagesExist() {
        assertThat(filesUnder(VIEWS_STUDY)).as("src/views/study/** 必须有页面").isNotEmpty();
        assertThat(filesUnder(LAYOUTS_STUDY)).as("src/layouts/study/** 必须有学习端布局").isNotEmpty();
        assertThat(filesUnder(COMPONENTS_STUDY)).as("src/components/study/** 必须有自研组件").isNotEmpty();

        List<String> expected = Arrays.asList(
                "views/study/practice/index.vue",
                "views/study/word/index.vue",
                "views/study/grammar/index.vue",
                "views/study/writing/index.vue",
                "views/study/eye/index.vue",
                "views/study/parent/report/index.vue",
                "views/study/parent/error/index.vue",
                "views/study/parent/feedback/index.vue",
                "layouts/study/index.vue",
                "components/study/StudyEyeShield.vue",
                "components/study/StudyBlueLightSwitch.vue",
                "components/study/StudyDataList.vue",
                "components/study/StudyStatCards.vue",
                "components/study/StudyParentShell.vue");
        List<String> actual = projectFiles().stream().map(StudyUiSpecAssertionTest::rel).toList();
        assertThat(actual).as("本项目新增的页面与组件清单").containsAll(expected);
    }

    // ================================================================= TC-08-19 移动端表格转卡片

    @Test
    @DisplayName("TC-08-19 列表组件在 ≤992px 转卡片（桌面表格 + 窄屏卡片双形态）")
    void tc08_19_tableTurnsIntoCardOnNarrowScreen() {
        Path list = component("StudyDataList.vue");
        assertThat(list).as("StudyDataList 必须存在").isNotNull();

        String content = read(list);
        assertThat(content)
                .as("TC-08-19 / §12.2：必须存在 ≤992px 的媒体查询（上游移动端阈值是 992px）")
                .contains("@media (max-width: 992px)");
        assertThat(content)
                .as("TC-08-19：窄屏下桌面表格必须转卡片（表格隐藏 + 卡片可见）")
                .contains("study-data-list__table")
                .contains("study-data-list__card");
        assertThat(content)
                .as("TC-08-19：窄屏媒体查询里必须把表格转成卡片形态")
                .containsPattern("(?s)@media\\s*\\(max-width:\\s*992px\\).*?__table\\s*\\{[^}]*display:\\s*none[^}]*\\}");
    }

    @Test
    @DisplayName("TC-08-19 学习端页面不使用表格（一屏一任务，§9.7）")
    void tc08_19_learningSideHasNoTable() {
        List<String> hits = new ArrayList<>();
        for (Path file : learningFiles()) {
            String content = read(file);
            if (content.contains("<table") || content.contains("<a-table") || content.contains("BasicTable")) {
                hits.add(rel(file));
            }
        }
        assertThat(hits).as("学习端不使用表格（§9.7：学习端用卡片流）").isEmpty();
    }

    @Test
    @DisplayName("TC-08-19 家长端页面走可转卡片的双形态列表组件")
    void tc08_19_parentSideUsesCardCapableList() {
        List<String> hits = new ArrayList<>();
        for (Path file : parentFiles()) {
            String content = read(file);
            // 只有「定义了列表列」的页面才需要列表组件
            boolean hasList = content.contains("Columns = [");
            if (hasList && !content.contains("StudyDataList")) {
                hits.add(rel(file) + " 未使用 StudyDataList");
            }
        }
        assertThat(hits)
                .as("TC-08-19：家长端列表必须走 StudyDataList（≤992px 自动转卡片）")
                .isEmpty();
    }

    @Test
    @DisplayName("F-16 窄屏不做横向滚动（应转卡片）")
    void f16_noHorizontalScrollOnNarrowScreen() {
        List<String> hits = new ArrayList<>();
        for (Path file : projectFiles()) {
            String content = read(file);
            if (content.contains("overflow-x: auto") || content.contains("overflow-x:auto")
                    || content.contains("overflow-x: scroll") || content.contains("overflow-x:scroll")) {
                hits.add(rel(file));
            }
        }
        assertThat(hits).as("F-16：移动端表格转卡片，不做横向滚动").isEmpty();
    }

    @Test
    @DisplayName("F-15 表格首列只放短标签（≤12 字）")
    void f15_firstColumnIsShortLabel() {
        List<String> hits = new ArrayList<>();
        Pattern columns = Pattern.compile("Columns\\s*=\\s*\\[([\\s\\S]*?)\\];");
        Matcher block = columns.matcher("");
        for (Path file : projectFiles()) {
            String content = read(file);
            block = columns.matcher(content);
            while (block.find()) {
                Matcher title = Pattern.compile("title:\\s*'([^']*)'").matcher(block.group(1));
                if (title.find()) {
                    String head = title.group(1);
                    if (head.length() > 12) {
                        hits.add(rel(file) + " 首列「" + head + "」长度 " + head.length());
                    }
                }
            }
        }
        assertThat(hits).as("F-15 / §8.3：表格首列建议 ≤12 字").isEmpty();
    }

    // ================================================================= TC-08-20 F-01…F-25

    @Test
    @DisplayName("F-01 不得硬编码 hex 色值（走 less 变量或 antd token）")
    void f01_noHardcodedHexColor() {
        List<String> hits = new ArrayList<>();
        for (Path file : projectFiles()) {
            Matcher m = HEX_COLOR.matcher(read(file));
            while (m.find()) {
                hits.add(rel(file) + " → " + m.group());
            }
        }
        assertThat(hits)
                .as("F-01 / §4.2①：新页面凡使用颜色必须走 CSS 变量或 less 变量，不得硬编码 hex")
                .isEmpty();
    }

    @Test
    @DisplayName("F-02 不得自定义 font-family")
    void f02_noCustomFontFamily() {
        List<String> hits = new ArrayList<>();
        for (Path file : projectFiles()) {
            if (read(file).contains("font-family")) {
                hits.add(rel(file));
            }
        }
        assertThat(hits).as("F-02 / §5.1①：字体族继承 antd token，不在业务代码里重新声明").isEmpty();
    }

    @Test
    @DisplayName("F-03 字号只用 §5.2 阶梯（学习端按 §10.1 放宽到 18px）")
    void f03_fontSizeUsesLadder() {
        List<String> hits = new ArrayList<>();
        for (Path file : projectFiles()) {
            Matcher m = FONT_SIZE.matcher(read(file));
            while (m.find()) {
                if (!ALLOWED_FONT_SIZE.contains(m.group(1))) {
                    hits.add(rel(file) + " → font-size: " + m.group(1) + "px");
                }
            }
        }
        assertThat(hits).as("F-03 / §5.2：字号只能取 " + ALLOWED_FONT_SIZE).isEmpty();
    }

    @Test
    @DisplayName("F-04 字重不得 ≥700")
    void f04_fontWeightBelow700() {
        List<String> hits = new ArrayList<>();
        for (Path file : projectFiles()) {
            Matcher m = FONT_WEIGHT.matcher(read(file));
            while (m.find()) {
                if (Integer.parseInt(m.group(1)) >= 700) {
                    hits.add(rel(file) + " → font-weight: " + m.group(1));
                }
            }
        }
        assertThat(hits).as("F-04 / §5.3：字重只用 400 / 500 / 600").isEmpty();
    }

    @Test
    @DisplayName("F-05 圆角按端收口（家长端 ≤8px，学习端 ≤12px）")
    void f05_borderRadiusWithinLimit() {
        List<String> hits = new ArrayList<>();
        for (Path file : projectFiles()) {
            int limit = isParentScoped(file) ? 8 : 12;
            Matcher m = BORDER_RADIUS.matcher(read(file));
            while (m.find()) {
                Matcher px = PX_NUMBER.matcher(m.group(1));
                while (px.find()) {
                    double value = Double.parseDouble(px.group(1));
                    if (!ALLOWED_RADIUS.contains(px.group(1))) {
                        hits.add(rel(file) + " → 圆角取值不在阶梯内: " + px.group(1) + "px");
                    } else if (value > limit) {
                        hits.add(rel(file) + " → 圆角 " + px.group(1) + "px 超过本端上限 " + limit + "px");
                    }
                }
            }
        }
        assertThat(hits).as("F-05 / §7.1：管理端 ≤8px，学习端可到 12px").isEmpty();
    }

    @Test
    @DisplayName("F-06 不得自造 box-shadow（只允许 §7.2 三种）")
    void f06_boxShadowOnlyAllowedValues() {
        List<String> hits = new ArrayList<>();
        for (Path file : projectFiles()) {
            Matcher m = BOX_SHADOW.matcher(read(file));
            while (m.find()) {
                String value = m.group(1).trim().replaceAll("\\s+", " ");
                if (!ALLOWED_BOX_SHADOW.contains(value)) {
                    hits.add(rel(file) + " → box-shadow: " + value);
                }
            }
        }
        assertThat(hits).as("F-06 / §7.2：阴影只允许 " + ALLOWED_BOX_SHADOW).isEmpty();
    }

    @Test
    @DisplayName("F-07 家长端与学习端不套管理端列表骨架（管理端自己必须用 BasicTable）")
    void f07_noAdminListSkeleton() {
        List<String> hits = new ArrayList<>();
        // ① 家长端与学习端：不套管理端列表骨架（useListPage / useListTable / 手写 a-table）
        for (Path file : parentAndLearningFiles()) {
            String content = read(file);
            if (content.contains("useListPage") || content.contains("useListTable")
                    || content.contains("<a-table")) {
                hits.add(rel(file));
            }
        }
        // ② 学习端更进一步：连 BasicTable 都不能用（§9.7 学习端用卡片流，不用表格）
        for (Path file : learningFiles()) {
            if (read(file).contains("BasicTable")) {
                hits.add(rel(file) + " 学习端不得使用 BasicTable");
            }
        }
        // ③ 管理端反过来：列表页必须用 BasicTable，不得手写 a-table（§9.1）
        List<Path> manageFiles = filesUnder(VIEWS_MANAGE);
        assertThat(manageFiles).as("扫描有效性：管理端文件集合必须非空，否则会假绿").isNotEmpty();
        for (Path file : manageFiles) {
            String content = read(file);
            if (content.contains("<a-table")) {
                hits.add(rel(file) + " 管理端列表页必须用 BasicTable（§9.1）");
            }
        }
        assertThat(hits)
                .as("F-07 / §9.1：家长端与学习端不适用管理端列表骨架；管理端必须用 BasicTable")
                .isEmpty();
    }

    @Test
    @DisplayName("F-08 不得另做一套分页组件")
    void f08_noSeparatePagination() {
        List<String> hits = new ArrayList<>();
        for (Path file : projectFiles()) {
            String content = read(file);
            if (content.contains("a-pagination") || content.contains("Pagination")) {
                hits.add(rel(file));
            }
        }
        assertThat(hits).as("F-08 / §8.9：分页由表格内置，不另造分页组件").isEmpty();
    }

    @Test
    @DisplayName("F-09 禁止 layout=\"inline\" 表单")
    void f09_noInlineFormLayout() {
        List<String> hits = new ArrayList<>();
        for (Path file : projectFiles()) {
            String content = read(file);
            if (content.contains("layout=\"inline\"") || content.contains("layout: 'inline'")
                    || content.contains("layout: \"inline\"")) {
                hits.add(rel(file));
            }
        }
        assertThat(hits).as("F-09 / §8.4③：禁止 layout=inline（移动端必崩）").isEmpty();
    }

    @Test
    @DisplayName("F-10 学习端不套管理端表单骨架；管理端表单必须沿用 horizontal")
    void f10_noAdminFormSkeleton() {
        List<String> hits = new ArrayList<>();
        // ① 学习端不用管理端表单骨架（学习端是卡片流 + 底部主操作条）
        for (Path file : learningFiles()) {
            String content = read(file);
            if (content.contains("BasicForm") || content.contains("<a-form")) {
                hits.add(rel(file));
            }
        }
        // ② 管理端表单沿用基座默认 layout="horizontal"，不得强制改成 vertical（§8.4①）
        //
        //   V1.1 已把旧版「表单一律 vertical」的写法改正为「管理端用基座默认 horizontal」，
        //   管理端再写 :layout="vertical" 属于违反基座默认。
        for (Path file : filesUnder(VIEWS_MANAGE)) {
            String content = read(file);
            if (content.contains("<a-form")
                    && (content.contains(":layout=\"vertical\"") || content.contains("layout: 'vertical'")
                            || content.contains("layout=\"vertical\""))) {
                hits.add(rel(file) + " → 管理端表单必须沿用基座默认 horizontal（§8.4①）");
            }
        }
        assertThat(hits)
                .as("F-10 / §9.1 / §8.4①：学习端不引入管理端表单骨架；管理端表单用默认 horizontal")
                .isEmpty();
    }

    @Test
    @DisplayName("F-11 弹窗必须提供 :body-style")
    void f11_modalMustProvideBodyStyle() {
        List<String> hits = new ArrayList<>();
        for (Path file : projectFiles()) {
            String content = read(file);
            boolean hasModal = content.contains("<a-modal") || content.contains("<BasicModal");
            if (hasModal && !content.contains("body-style") && !content.contains("bodyStyle")) {
                hits.add(rel(file));
            }
        }
        assertThat(hits).as("F-11 / §8.5：弹窗内容留白必须由 :body-style 提供").isEmpty();
    }

    @Test
    @DisplayName("F-12 禁止裸 <button>，必须用 a-button")
    void f12_noRawButtonTag() {
        List<String> hits = new ArrayList<>();
        for (Path file : projectFiles()) {
            if (read(file).contains("<button")) {
                hits.add(rel(file));
            }
        }
        assertThat(hits).as("F-12 / §8.1：按钮必须用 a-button（BasicButton）").isEmpty();
    }

    @Test
    @DisplayName("F-13 图标只用 <Icon icon=\"ant-design:xxx\" />，禁内联 SVG / emoji")
    void f13_onlyIconComponentIcons() {
        List<String> hits = new ArrayList<>();
        for (Path file : projectFiles()) {
            String content = read(file);
            if (content.contains("<svg") || content.contains("<img")) {
                hits.add(rel(file) + " 含内联 SVG / img");
            }
            Matcher m = ICON_ATTR.matcher(content);
            while (m.find()) {
                String icon = m.group(1).trim();
                if (!icon.isEmpty() && !icon.startsWith("ant-design:")) {
                    hits.add(rel(file) + " → icon=" + icon);
                }
            }
        }
        assertThat(hits).as("F-13 / §8.8：图标只用 ant-design:* 前缀，禁内联 SVG 与 emoji").isEmpty();
    }

    @Test
    @DisplayName("F-14 操作列必须走 StudyActionColumn（页面不手写操作列）")
    void f14_noHandWrittenActionColumn() {
        List<String> hits = new ArrayList<>();
        for (Path file : projectFiles()) {
            String path = rel(file);
            // 组件自身定义操作列标题是规范要求的，不算「手写操作列」
            if (path.equals(ACTION_COLUMN_COMPONENT)) {
                continue;
            }
            String content = read(file);
            boolean hasActionColumn = content.contains("title: '操作'") || content.contains("title: \"操作\"")
                    || content.contains(">操作<");
            if (hasActionColumn && !content.contains("StudyActionColumn")) {
                hits.add(path);
            }
        }
        assertThat(hits).as("F-14 / §8.7：操作列必须走 StudyActionColumn").isEmpty();
    }

    @Test
    @DisplayName("F-17 不得引用死代码 src/design/ant/table.less")
    void f17_noDeadTableLessImport() {
        List<String> hits = new ArrayList<>();
        for (Path file : projectFiles()) {
            if (read(file).contains("ant/table.less")) {
                hits.add(rel(file));
            }
        }
        assertThat(hits).as("F-17：table.less 的 import 已被注释，是死代码").isEmpty();
    }

    @Test
    @DisplayName("F-19 不得写 outline:none；学习端须自补 focus-visible 焦点环")
    void f19_noOutlineNoneAndHasFocusVisible() {
        List<String> hits = new ArrayList<>();
        for (Path file : projectFiles()) {
            String content = read(file);
            if (content.contains("outline: none") || content.contains("outline:none")) {
                hits.add(rel(file) + " 含 outline:none");
            }
        }
        assertThat(hits).as("F-19 / §11.4④：业务代码里不得再写 outline:none").isEmpty();

        for (String shell : List.of("layouts/study/index.vue", "components/study/StudyParentShell.vue")) {
            Path file = RedLineScanner.frontendRoot().resolve(shell);
            String content = read(file);
            assertThat(content)
                    .as("§11.4① / 自检 #24：" + shell + " 必须为可点击元素自补 focus-visible 焦点环")
                    .contains(":focus-visible")
                    .contains("outline: 2px solid");
        }
    }

    @Test
    @DisplayName("F-20 自造 z-index 不得越界（浮层落在 1000–3000）")
    void f20_zIndexWithinAllowedRange() {
        List<String> hits = new ArrayList<>();
        for (Path file : projectFiles()) {
            Matcher m = Z_INDEX.matcher(read(file));
            while (m.find()) {
                int value = Integer.parseInt(m.group(1));
                if (value > 3000) {
                    hits.add(rel(file) + " → z-index: " + value + "（上限 3000，进度条 99999 专用）");
                }
            }
        }
        assertThat(hits).as("F-20 / §7.4：浮层 z-index 必须落在 1000–3000").isEmpty();

        Path shield = component("StudyEyeShield.vue");
        Matcher m = Z_INDEX.matcher(read(shield));
        boolean found = false;
        while (m.find()) {
            found = true;
            int value = Integer.parseInt(m.group(1));
            assertThat(value)
                    .as("§10.5：护眼遮罩 z-index 必须落在 1000–3000 之间")
                    .isBetween(1000, 3000);
        }
        assertThat(found).as("护眼遮罩必须显式声明 z-index").isTrue();
    }

    @Test
    @DisplayName("F-21 学习端不得出现排行 / 分数比较类文案")
    void f21_learningSideHasNoRankingCopy() {
        List<String> hits = copyHits(learningFiles(), RANK_WORDS);
        assertThat(hits).as("F-21 / R-04：学习端不得出现排名类措辞").isEmpty();
    }

    @Test
    @DisplayName("F-22 学习端答错反馈不得大面积纯红")
    void f22_learningSideNoSolidRedBackground() {
        List<String> hits = new ArrayList<>();
        for (Path file : learningFiles()) {
            String content = read(file);
            Matcher bg = Pattern.compile("background(?:-color)?\\s*:\\s*([^;}]+)").matcher(content);
            while (bg.find()) {
                String value = bg.group(1);
                if (value.contains("@error-color") || value.contains("var(--error-color)")
                        || value.contains("@primary-6") || HEX_COLOR.matcher(value).find()) {
                    hits.add(rel(file) + " → background: " + value.trim());
                }
            }
        }
        assertThat(hits)
                .as("F-22 / §10.3：答错用柔和底色（@danger-background-color），不用大面积纯红")
                .isEmpty();
    }

    @Test
    @DisplayName("F-23 学习端答案区在未作答时不得存在于 DOM")
    void f23_answerAreaNotInDomBeforeAnswered() {
        Path practice = RedLineScanner.frontendRoot().resolve("views/study/practice/index.vue");
        String content = read(practice);

        assertThat(content)
                .as("F-23 / §10.6：未作答时解析区必须整块不渲染（v-if），不能只是藏起来")
                .doesNotContain("v-show")
                .doesNotContain("display: none")
                .doesNotContain("display:none");
        assertThat(content)
                .as("F-23：反馈 / 讲解区必须由「已作答」状态驱动渲染")
                .containsPattern("v-if=\"result\"");
        assertThat(content)
                .as("F-23 / §10.6：未作答时不得出现「查看答案 / 正确答案」入口")
                .doesNotContain("正确答案")
                .doesNotContain("查看答案")
                .doesNotContain("showAnswer")
                .doesNotContain("revealAnswer");
    }

    @Test
    @DisplayName("F-24 家长端不得出现家长间对比 / 家庭内比较措辞")
    void f24_parentSideHasNoComparisonCopy() {
        List<String> hits = copyHits(parentFiles(), RANK_WORDS);
        hits.addAll(copyHits(parentFiles(), FAMILY_COMPARE_WORDS));
        assertThat(hits).as("F-24 / R-04：家长端不得出现排名或家庭内比较措辞").isEmpty();
    }

    @Test
    @DisplayName("F-25 学习端 / 孩子端不得提供 Excel 导入导出")
    void f25_learningSideHasNoExcelImportExport() {
        List<String> hits = new ArrayList<>();
        for (Path file : learningFiles()) {
            String content = read(file);
            if (content.contains("ExcelButtons") || content.contains("JUploadButton")
                    || content.contains("导入") || content.contains("导出")
                    || content.contains("/import") || content.contains("/export")) {
                hits.add(rel(file));
            }
        }
        assertThat(hits).as("F-25 / §9.3：学习端与孩子端不提供 Excel 导入导出").isEmpty();
    }

    // ================================================================= §14 自检清单

    @Test
    @DisplayName("自检 #4 行高只用 1.5715 / 1.4 / 1.2")
    void checklist04_lineHeightUsesScale() {
        List<String> hits = new ArrayList<>();
        for (Path file : projectFiles()) {
            Matcher m = LINE_HEIGHT.matcher(read(file));
            while (m.find()) {
                if (!ALLOWED_LINE_HEIGHT.contains(m.group(1))) {
                    hits.add(rel(file) + " → line-height: " + m.group(1));
                }
            }
        }
        assertThat(hits).as("§5.4：正文 1.5715、标题 1.4、大号数字 1.2").isEmpty();
    }

    @Test
    @DisplayName("自检 #5 间距只用 4/8/10/12/16/24/32")
    void checklist05_spacingUsesScale() {
        List<String> hits = new ArrayList<>();
        for (Path file : projectFiles()) {
            Matcher m = SPACING.matcher(read(file));
            while (m.find()) {
                Matcher px = PX_NUMBER.matcher(m.group(3));
                while (px.find()) {
                    if (!ALLOWED_SPACING.contains(px.group(1))) {
                        hits.add(rel(file) + " → " + m.group(1)
                                + (m.group(2) == null ? "" : m.group(2)) + ": " + m.group(3).trim()
                                + "（含 " + px.group(1) + "px）");
                    }
                }
            }
        }
        assertThat(hits).as("§6.1：间距刻度只有 " + ALLOWED_SPACING).isEmpty();
    }

    @Test
    @DisplayName("自检 #8 边框宽度显式写 1px")
    void checklist08_borderWidthIsOnePixel() {
        List<String> hits = new ArrayList<>();
        for (Path file : projectFiles()) {
            Matcher m = BORDER.matcher(read(file));
            while (m.find()) {
                Matcher px = PX_NUMBER.matcher(m.group(2));
                while (px.find()) {
                    if (!"1".equals(px.group(1))) {
                        hits.add(rel(file) + " → border: " + m.group(2).trim());
                    }
                }
            }
        }
        assertThat(hits).as("§7.3：全局 border-width 为 0，每个边框都要显式写 1px").isEmpty();
    }

    @Test
    @DisplayName("自检 #9 列表页栅格 gutter=16，列宽 12/24，不依赖 antd 响应式断点")
    void checklist09_gridGutterAndColumns() {
        List<String> hits = new ArrayList<>();

        // ① gutter 必须是 16（§6.2）
        for (Path file : projectFiles()) {
            Matcher m = Pattern.compile(":gutter\\s*=\\s*\"?\\[?\\s*(\\d+)").matcher(read(file));
            while (m.find()) {
                if (!"16".equals(m.group(1))) {
                    hits.add(rel(file) + " → :gutter=" + m.group(1));
                }
            }
        }

        // ② 禁 antd 响应式断点属性
        //
        //   .ant-col 被锁成 width:100%（src/design/index.less:9），
        //   靠 xs/sm/md/lg/xl/xxl 自动分列根本不成立——响应式列必须自己写媒体查询（§12.1）。
        //   ⚠ 旧版断言把 a-row / a-col 一刀切禁掉了，但 §6.2 明确允许
        //   「a-row :gutter="16" + a-col :span="12|24"」，禁的只是断点属性。
        for (Path file : projectFiles()) {
            String content = read(file);
            for (String breakpoint : Arrays.asList("xs", "sm", "md", "lg", "xl", "xxl")) {
                if (content.contains(":" + breakpoint + "=")) {
                    hits.add(rel(file) + " → 使用了 antd 响应式断点属性 :" + breakpoint);
                }
            }
        }

        // ③ 列宽只能 12 / 24（§6.2）
        for (Path file : projectFiles()) {
            Matcher m = Pattern.compile(":span\\s*=\\s*\"?(\\d+)").matcher(read(file));
            while (m.find()) {
                if (!"12".equals(m.group(1)) && !"24".equals(m.group(1))) {
                    hits.add(rel(file) + " → :span=" + m.group(1));
                }
            }
            Matcher s = Pattern.compile("(?<![\\w-])span\\s*:\\s*(\\d+)").matcher(read(file));
            while (s.find()) {
                if (!"12".equals(s.group(1)) && !"24".equals(s.group(1))) {
                    hits.add(rel(file) + " → span: " + s.group(1));
                }
            }
        }

        assertThat(hits).as("§6.2 / §12.1：gutter=16、列宽 12/24，响应式列自己写媒体查询").isEmpty();
    }

    @Test
    @DisplayName("自检 #16 标签色只用 antd 预设名（动态色统一走 StudyEnumTag）")
    void checklist16_tagColorUsesPresetName() {
        List<String> hits = new ArrayList<>();

        for (Path file : projectFiles()) {
            String path = rel(file);
            String content = read(file);
            // 逐个 <a-tag> 标签看：动态色（:color）只允许出现在 StudyEnumTag 里
            Matcher tags = Pattern.compile("<a-tag[^>]*>").matcher(content);
            while (tags.find()) {
                String tag = tags.group();
                Matcher dynamic = Pattern.compile(":color\\s*=\\s*\"([^\"]*)\"").matcher(tag);
                if (dynamic.find()) {
                    if (!path.equals(ENUM_TAG_COMPONENT)) {
                        hits.add(path + " → 动态标签色 " + dynamic.group(1) + "（统一走 StudyEnumTag）");
                    }
                    continue;
                }
                Matcher literal = Pattern.compile("(?<![:\\w-])color\\s*=\\s*\"([^\"]*)\"").matcher(tag);
                if (literal.find() && !ALLOWED_TAG_COLOR.contains(literal.group(1))) {
                    hits.add(path + " → color=" + literal.group(1));
                }
            }
        }

        // StudyEnumTag 的候选色板必须只用 antd 预设名（§8.6 同名同色 hash 映射）
        Path enumTag = RedLineScanner.frontendRoot().resolve(ENUM_TAG_COMPONENT);
        assertThat(Files.isRegularFile(enumTag))
                .as("统一枚举标签组件必须存在：" + ENUM_TAG_COMPONENT)
                .isTrue();
        String enumContent = read(enumTag);
        Matcher arrays = Pattern.compile("\\[[^\\]]*\\]").matcher(enumContent);
        while (arrays.find()) {
            Matcher colors = Pattern.compile("'([a-z]+)'").matcher(arrays.group());
            while (colors.find()) {
                if (!ALLOWED_TAG_COLOR.contains(colors.group(1))) {
                    hits.add(ENUM_TAG_COMPONENT + " → 候选色 " + colors.group(1) + " 不在 antd 预设名集合内");
                }
            }
        }

        assertThat(hits).as("§8.6：标签色只用 antd 预设名，不传裸 hex，动态色统一走 StudyEnumTag").isEmpty();
    }

    @Test
    @DisplayName("自检 #17/#18 有列表的页面必须有 a-empty 空态与加载态")
    void checklist17_emptyStateAndLoading() {
        List<String> emptyHits = new ArrayList<>();
        List<String> loadingHits = new ArrayList<>();
        for (String page : List.of(
                "views/study/practice/index.vue",
                "views/study/word/index.vue",
                "views/study/grammar/index.vue",
                "views/study/writing/index.vue",
                "views/study/parent/report/index.vue",
                "views/study/parent/error/index.vue")) {
            String content = read(RedLineScanner.frontendRoot().resolve(page));
            if (!content.contains("a-empty")) {
                emptyHits.add(page);
            }
            if (!content.contains("a-spin") && !content.contains(":loading")) {
                loadingHits.add(page);
            }
        }
        assertThat(emptyHits).as("§11.1：有列表的页面必须用 a-empty 做空态").isEmpty();
        assertThat(loadingHits).as("§11.2：加载态用 :loading 或 a-spin").isEmpty();
    }

    @Test
    @DisplayName("自检 #19 越权提示不得伪装成「数据不存在」")
    void checklist19_forbiddenCopyNotDisguised() {
        List<String> hits = new ArrayList<>();
        for (Path file : projectFiles()) {
            // 必须先剥注释：注释里为了说明「不要这么写」而引用的字面量，
            //   不是用户看得见的文案，不该算违规（本仓既有纪律）
            String content = stripComments(read(file));
            if (content.contains("数据不存在")) {
                hits.add(rel(file) + " 含「数据不存在」");
            }
        }
        assertThat(hits).as("§11.3：越权提示统一是「无权查看该孩子的数据」").isEmpty();

        boolean hasForbiddenCopy = parentFiles().stream()
                .map(StudyUiSpecAssertionTest::read)
                .map(StudyUiSpecAssertionTest::stripComments)
                .anyMatch(content -> content.contains("无权查看该孩子的数据"));
        assertThat(hasForbiddenCopy).as("§11.3：家长端必须给出「无权查看该孩子的数据」提示").isTrue();
    }

    @Test
    @DisplayName("自检 #23 z-index 浮层在 1000–3000（见 F-20）")
    void checklist23_zIndexRange() {
        assertThat(learningFiles()).as("学习端必须有页面参与浮层检查").isNotEmpty();
        List<String> hits = new ArrayList<>();
        for (Path file : projectFiles()) {
            Matcher m = Z_INDEX.matcher(read(file));
            while (m.find()) {
                if (Integer.parseInt(m.group(1)) > 3000) {
                    hits.add(rel(file) + " → z-index: " + m.group(1));
                }
            }
        }
        assertThat(hits).as("§7.4：浮层 z-index 必须在 1000–3000").isEmpty();
    }

    @Test
    @DisplayName("自检 #25 媒体查询只用上游 6 个断点")
    void checklist25_breakpointsAreBaseSix() {
        List<String> hits = new ArrayList<>();
        for (Path file : projectFiles()) {
            Matcher m = MEDIA.matcher(read(file));
            while (m.find()) {
                Matcher px = PX_NUMBER.matcher(m.group(1));
                while (px.find()) {
                    if (!ALLOWED_BREAKPOINT.contains(px.group(1))) {
                        hits.add(rel(file) + " → @media (" + m.group(1).trim() + ")");
                    }
                }
            }
        }
        assertThat(hits).as("§12.1：断点只能是 " + ALLOWED_BREAKPOINT).isEmpty();
    }

    @Test
    @DisplayName("自检 #26/#27 布局归属：学习端用 layouts/study，家长端不加载管理端顶栏与侧栏")
    void checklist26_layoutOwnership() {
        List<String> hits = new ArrayList<>();
        for (Path file : projectFiles()) {
            String content = read(file);
            if (content.contains("/@/layouts/default")) {
                hits.add(rel(file) + " 引入了管理端布局");
            }
            if (content.contains("LayoutHeader") || content.contains("LayoutSideBar")
                    || content.contains("LayoutMultipleHeader")) {
                hits.add(rel(file) + " 引入了管理端顶栏 / 侧栏");
            }
        }
        assertThat(hits).as("§9.5–§9.7：学习端与家长端都不加载管理端顶栏与侧栏").isEmpty();

        for (Path file : learningPageFiles()) {
            assertThat(read(file))
                    .as("§9.7：学习端页面必须走 layouts/study 独立全屏布局")
                    .contains("/@/layouts/study/index.vue");
        }
        for (Path file : parentPageFiles()) {
            assertThat(read(file))
                    .as("§9.6：家长端页面必须走 StudyParentShell（顶部孩子切换条）")
                    .contains("StudyParentShell");
        }
    }

    @Test
    @DisplayName("自检 #28 家长端只读：无新增 / 编辑 / 删除入口")
    void checklist28_parentSideIsReadOnly() {
        List<String> hits = copyHits(parentFiles(), Arrays.asList("新增", "编辑", "删除"));
        assertThat(hits)
                .as("§9.6：家长端只读（写操作仅限家庭 / 成员 / 孩子档案 / 关联 / PIN 重置）")
                .isEmpty();
    }

    @Test
    @DisplayName("自检 #29 家长端顶部有孩子切换条，且只能切到已关联的孩子")
    void checklist29_parentHasChildSwitch() {
        Path shell = component("StudyParentShell.vue");
        String content = read(shell);
        assertThat(content)
                .as("§9.6 / 自检 #29：家长端外壳必须有孩子切换入口")
                .contains("切换孩子");
        assertThat(content)
                .as("自检 #29：切换项来自已关联孩子列表，不能自由输入")
                .contains("childOptions");
    }

    @Test
    @DisplayName("自检 #33 学习端一屏一任务（不使用多列栅格）")
    void checklist33_oneScreenOneTask() {
        List<String> hits = new ArrayList<>();
        Pattern multiColumn = Pattern.compile("grid-template-columns\\s*:\\s*repeat\\(\\s*[2-9]");
        for (Path file : learningPageFiles()) {
            String content = read(file);
            if (content.contains("a-row") || content.contains("a-col")) {
                hits.add(rel(file) + " 使用了 antd 栅格");
            }
            if (multiColumn.matcher(content).find()) {
                hits.add(rel(file) + " 使用了多列网格");
            }
        }
        assertThat(hits).as("§10.2：学习端页面不铺多列栅格，一屏一个主任务").isEmpty();
    }

    @Test
    @DisplayName("自检 #34 学习端底部主操作条唯一且高度 ≥44px")
    void checklist34_singlePrimaryActionInFooter() {
        for (Path file : learningPageFiles()) {
            String content = read(file);
            Matcher footer = FOOTER_SLOT.matcher(content);
            assertThat(footer.find())
                    .as("§10.2：学习端页面必须把主操作放进底部主操作条（<template #footer>）")
                    .isTrue();
            assertThat(footer.group(1))
                    .as("§10.2：底部主操作条里必须有唯一的主按钮")
                    .contains("type=\"primary\"");

            int primaryCount = countMatches(content, "type=\"primary\"");
            assertThat(primaryCount)
                    .as("§10.2：一屏只允许一个主按钮（" + rel(file) + "）")
                    .isEqualTo(1);
        }
    }

    @Test
    @DisplayName("自检 #36 反馈区显示判定来源，判定依据可展开")
    void checklist36_judgeSourceAndBasisVisible() {
        String content = read(RedLineScanner.frontendRoot().resolve("views/study/practice/index.vue"));
        assertThat(content)
                .as("§10.3 强制规则 1：规则判定显示「按答案判定」")
                .contains("按答案判定");
        assertThat(content)
                .as("§10.3 强制规则 1：含模型时显示「AI 辅助讲解，判定以规则为准」")
                .contains("AI 辅助讲解，判定以规则为准");
        assertThat(content)
                .as("§10.3 强制规则 2：判定依据必须可展开查看")
                .contains("判定依据")
                .contains("basisVisible");
    }

    @Test
    @DisplayName("自检 #37 学习端文案无负向表述")
    void checklist37_noNegativeCopy() {
        List<String> hits = copyHits(learningFiles(), NEGATIVE_COPY);
        assertThat(hits).as("§10.3 强制规则 4：不出现「你太笨了」「错误次数：3」这类负向表述").isEmpty();
    }

    @Test
    @DisplayName("自检 #38 休息提醒阻断式，遮罩不可自动消失")
    void checklist38_restShieldIsBlocking() {
        Path shield = component("StudyEyeShield.vue");
        String content = read(shield);
        assertThat(content)
                .as("§10.5 / R-03：护眼遮罩内不得有任何定时器（不可自动消失）")
                .doesNotContain("setTimeout")
                .doesNotContain("setInterval")
                .doesNotContain("requestAnimationFrame");
        assertThat(content)
                .as("§10.5：遮罩必须由服务端下发的 restRequired 驱动")
                .contains("restRequired");
        assertThat(content)
                .as("R-03：清除遮罩只能由显式确认触发")
                .contains("confirm");

        // 清除 restRequired 的写点必须只在「显式确认」这一条路径上
        List<String> clearHits = new ArrayList<>();
        for (Path file : projectFiles()) {
            String fileContent = read(file);
            int index = fileContent.indexOf("restRequired.value = false");
            while (index >= 0) {
                String around = fileContent.substring(Math.max(0, index - 400), index);
                if (!around.contains("confirm")) {
                    clearHits.add(rel(file) + " → restRequired.value = false 不在确认路径上");
                }
                index = fileContent.indexOf("restRequired.value = false", index + 1);
            }
        }
        assertThat(clearHits).as("R-03：遮罩只能由用户显式确认清除，不得自动清除").isEmpty();
    }

    @Test
    @DisplayName("自检 #39 动效 ≤600ms，无闪白 / 抖动")
    void checklist39_animationUnder600ms() {
        List<String> hits = new ArrayList<>();
        for (Path file : projectFiles()) {
            Matcher m = Pattern.compile("(transition|animation)(-[a-z]+)?\\s*:\\s*([^;}]+)").matcher(read(file));
            while (m.find()) {
                Matcher px = PX_NUMBER.matcher(m.group(3));
                while (px.find()) {
                    if (Double.parseDouble(px.group(1)) > 600) {
                        hits.add(rel(file) + " → " + m.group(3).trim());
                    }
                }
            }
        }
        assertThat(hits).as("§10.4：轻量动效 ≤600ms").isEmpty();
    }

    @Test
    @DisplayName("自检 #40 学习端触控目标 ≥44×44px")
    void checklist40_touchTargetAtLeast44() {
        for (Path file : learningPageFiles()) {
            assertThat(read(file))
                    .as("§10.1 / §11.4②：学习端页面的可点击元素必须 ≥44px（" + rel(file) + "）")
                    .contains("min-height: 44px");
        }
    }

    @Test
    @DisplayName("自检 #42 学习端 / 孩子端不提供 Excel 导入导出（见 F-25）")
    void checklist42_noImportExport() {
        assertThat(learningFiles()).as("学习端页面必须参与检查").isNotEmpty();
        List<String> hits = copyHits(learningFiles(), Arrays.asList("导入", "导出"));
        assertThat(hits).as("自检 #42：学习端 / 孩子端不提供 Excel 导入导出").isEmpty();
    }

    // ================================================================= TC-08-21 暗色模式

    @Test
    @DisplayName("TC-08-21 项目页面在暗色模式下无残留硬编码亮色")
    void tc08_21_noHardcodedLightColor() {
        List<String> hits = new ArrayList<>();
        for (Path file : projectFiles()) {
            String content = read(file);
            Matcher m = Pattern.compile("(?<![-\\w@])white\\b").matcher(content);
            while (m.find()) {
                // white-space 是 CSS 属性名，不是颜色值：
                //   旧判定是「前后 40 字符里有没有 white-space」，
                //   而窗口右边界落在 "white-" 之后，完整的 "white-space" 根本不在窗口里
                //   ⇒ 只要页面写了 white-space: nowrap 就被误判成硬编码亮色。
                //   正确判据是「这个 white 是不是 white-space 的开头」。
                if (!content.startsWith("white-space", m.start())) {
                    int start = Math.max(0, m.start() - 40);
                    String around = content.substring(start, m.start() + 6);
                    hits.add(rel(file) + " → 出现字面量 white：" + around.trim());
                }
            }
        }
        assertThat(hits)
                .as("TC-08-21 / §4.2①：颜色必须走变量，暗色模式下不能残留硬编码亮色")
                .isEmpty();
    }

    @Test
    @DisplayName("TC-08-21 家长端颜色全部走 less 变量 / CSS 变量")
    void tc08_21_parentSideColorsComeFromVariables() {
        List<String> hits = new ArrayList<>();
        for (Path file : parentFiles()) {
            Matcher m = Pattern.compile("(?<![-\\w])(color|background-color|border-color|fill|stroke)\\s*:\\s*([^;}]+)")
                    .matcher(read(file));
            while (m.find()) {
                String value = m.group(2).trim();
                boolean isVariable = value.startsWith("@") || value.startsWith("var(") || value.startsWith("inherit")
                        || value.startsWith("transparent") || value.startsWith("currentColor")
                        || value.startsWith("none") || isLessColorFunctionOfVariable(value);
                if (!isVariable) {
                    hits.add(rel(file) + " → " + m.group(1) + ": " + value);
                }
            }
        }
        assertThat(hits).as("TC-08-21：家长端颜色必须走变量，否则暗色模式会出现半深半浅").isEmpty();
    }

    // ================================================================= TC-08-22 学习端主题锁定

    @Test
    @DisplayName("TC-08-22 学习端主题锁定为浅色")
    void tc08_22_themeLockedToLight() {
        Path layout = RedLineScanner.frontendRoot().resolve("layouts/study/index.vue");
        String content = read(layout);
        assertThat(content)
                .as("§4.2③ / §9.7：学习端根节点必须显式 data-theme=\"light\"")
                .contains("data-theme=\"light\"");
        assertThat(content)
                .as("§4.2③：学习端布局必须调用主题锁定实现")
                .contains("useStudyThemeLock");

        Path lock = RedLineScanner.frontendRoot().resolve("components/study/StudyThemeLock.ts");
        String lockContent = read(lock);
        assertThat(lockContent)
                .as("§4.2③：主题锁定必须把根节点写成 light 并移除 dark 类")
                .contains("setAttribute('data-theme', 'light')")
                .contains("classList.remove('dark')");
    }

    @Test
    @DisplayName("TC-08-22 学习端不提供主题切换入口")
    void tc08_22_noThemeSwitchEntry() {
        List<String> banned = Arrays.asList(
                "AppDarkModeToggle", "toggleTheme", "setDarkMode", "darkMode", "主题切换", "DarkModeToggle");
        List<String> hits = new ArrayList<>();
        for (Path file : learningFiles()) {
            String content = read(file);
            for (String token : banned) {
                if (content.contains(token)) {
                    hits.add(rel(file) + " → " + token);
                }
            }
        }
        assertThat(hits)
                .as("§4.2③ / TC-08-22：学习端锁定浅色，不提供主题切换入口")
                .isEmpty();
    }

    @Test
    @DisplayName("TC-08-22 家长端跟随全局暗色开关（不得自行锁定）")
    void tc08_22_parentSideFollowsGlobalTheme() {
        Path shell = component("StudyParentShell.vue");
        assertThat(read(shell))
                .as("§9.6：家长端主题跟随全局暗色开关，不做 data-theme 锁定")
                .doesNotContain("data-theme=\"light\"")
                .doesNotContain("useStudyThemeLock");
    }

    // ================================================================= TC-41-10 / TC-41-11

    @Test
    @DisplayName("TC-41-10 家长端文案无家庭内比较措辞")
    void tc41_10_parentCopyHasNoFamilyComparison() {
        List<String> hits = copyHits(parentFiles(), FAMILY_COMPARE_WORDS);
        assertThat(hits).as("TC-41-10 / R04-6：家长端文案命中数必须为 0").isEmpty();
    }

    @Test
    @DisplayName("TC-41-11 家长端与孩子端不存在孩子并排对比元素")
    void tc41_11_noSideBySideChildComparison() {
        List<String> hits = copyHits(projectFiles(), SIDE_BY_SIDE_WORDS);
        assertThat(hits).as("TC-41-11：不得出现并排对比 / 谁更的文案").isEmpty();

        List<String> codeHits = new ArrayList<>();
        for (Path file : projectFiles()) {
            String content = read(file);
            for (String token : Arrays.asList("compare", "Compare", "percentile", "rank", "Rank")) {
                if (content.contains(token)) {
                    codeHits.add(rel(file) + " → " + token);
                }
            }
        }
        assertThat(codeHits)
                .as("TC-41-11：不得存在对比 / 排名类结构与标识（类名、变量名、接口路径）")
                .isEmpty();
    }

    // ================================================================= 管理端列表内核不变量（D-3 / D-4）

    /**
     * 管理端内容维护页的<b>通用内核</b>。
     *
     * <p>《前端页面清单》里的 9 个管理端内容维护页结构同构，共用一个内核
     * {@code views/study/manage/ManageContentPage.vue}，各页只提供「列配置 + 数据源」。</p>
     */
    private static final String MANAGE_KERNEL = "views/study/manage/ManageContentPage.vue";

    @Test
    @DisplayName("D-3 管理端列表内核必须给每列补 dataIndex（只写 key 会让整列渲染为空）")
    void manageListKernelMustSupplyDataIndex() {
        Path kernel = RedLineScanner.frontendRoot().resolve(MANAGE_KERNEL);
        assertThat(Files.isRegularFile(kernel)).as("管理端内容维护内核必须存在：" + MANAGE_KERNEL).isTrue();

        String content = read(kernel);
        assertThat(content)
                .as("antd 表格按 record[dataIndex] 取值，底座 useColumns 只处理 key→dataIndex 的显示名、"
                        + "不会把 key 当取值字段 —— 内核必须补 dataIndex 兜底，否则每格都是 record[undefined]")
                .containsPattern("dataIndex\\s*:\\s*item\\.dataIndex\\s*\\|\\|\\s*item\\.key");
    }

    @Test
    @DisplayName("D-3 管理端列表的操作列只能由内核补一处（不得手工再插一列 action）")
    void manageListMustNotDuplicateActionColumn() {
        Path kernel = RedLineScanner.frontendRoot().resolve(MANAGE_KERNEL);
        assertThat(Files.isRegularFile(kernel)).as("管理端内容维护内核必须存在：" + MANAGE_KERNEL).isTrue();

        String content = read(kernel);
        Matcher block = Pattern.compile("const\\s+tableColumns\\s*=\\s*computed\\(\\(\\)\\s*=>\\s*\\{([\\s\\S]*?)\\}\\);")
                .matcher(content);
        assertThat(block.find()).as("必须能定位到 tableColumns 计算属性").isTrue();
        assertThat(block.group(1))
                .as("tableColumns 里不得手工插 action 列（BasicTable 的 actionColumn 已经会补一列，"
                        + "手工再插一列就是两列操作列）")
                .doesNotContain("action");

        assertThat(content)
                .as("show-action-column 必须跟着 detailBuilder 走（只读页不留一列空操作）")
                .containsPattern(":show-action-column\\s*=\\s*\"hasActionColumn\"");
        assertThat(content)
                .as("hasActionColumn 必须由 detailBuilder 决定")
                .containsPattern("hasActionColumn\\s*=\\s*computed\\(\\(\\)\\s*=>\\s*Boolean\\(props\\.detailBuilder\\)\\)");
    }

    @Test
    @DisplayName("D-3 管理端薄配置页的每一列都必须有 key（dataIndex 兜底靠它）")
    void manageThinPagesColumnsMustCarryKey() {
        List<Path> manageFiles = filesUnder(VIEWS_MANAGE);
        assertThat(manageFiles).as("扫描有效性：管理端文件集合必须非空，否则会假绿").isNotEmpty();

        Pattern columnsBlock = Pattern.compile("columns\\s*:\\s*StudyManageColumn\\[\\]\\s*=\\s*\\[([\\s\\S]*?)\\]\\s*;");
        Pattern item = Pattern.compile("\\{([^{}]*)\\}");
        Pattern hasKey = Pattern.compile("\\bkey\\s*:");

        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (Path file : manageFiles) {
            String content = read(file);
            Matcher block = columnsBlock.matcher(content);
            while (block.find()) {
                Matcher objects = item.matcher(block.group(1));
                while (objects.find()) {
                    checked++;
                    if (!hasKey.matcher(objects.group(1)).find()) {
                        problems.add(rel(file) + " → 列定义缺 key：" + objects.group(1).replaceAll("\\s+", " ").trim());
                    }
                }
            }
        }

        assertThat(checked).as("扫描有效性：必须至少扫描到一个列定义").isGreaterThan(0);
        assertThat(problems).as("每一列都必须写 key（内核用 key 兜底成 dataIndex）").isEmpty();
    }

    @Test
    @DisplayName("D-4 管理端内核必须用序号守卫丢弃过期响应（否则表格有数据却挂着错误提示）")
    void manageKernelMustGuardStaleResponses() {
        Path kernel = RedLineScanner.frontendRoot().resolve(MANAGE_KERNEL);
        assertThat(Files.isRegularFile(kernel)).as("管理端内容维护内核必须存在：" + MANAGE_KERNEL).isTrue();

        String content = read(kernel);
        assertThat(content)
                .as("页面挂载时内核先 reload、薄配置页的 onMounted 后跑，"
                        + "第一次请求必然失败；过期响应不得再写 errorText（必须有序号守卫）")
                .containsPattern("reloadSeq");
        assertThat(content)
                .as("成功路径必须清掉 errorText（否则数据加载成功后仍挂着上一条错误提示）")
                .containsPattern("errorText\\.value\\s*=\\s*''");
    }

    // ================================================================= 工具

    private static Path component(String name) {
        Path file = RedLineScanner.frontendRoot().resolve(COMPONENTS_STUDY).resolve(name);
        return Files.isRegularFile(file) ? file : null;
    }

    private static List<Path> filesUnder(String relativeDir) {
        Path root = RedLineScanner.frontendRoot().resolve(relativeDir);
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return name.endsWith(".vue") || name.endsWith(".ts") || name.endsWith(".tsx")
                                || name.endsWith(".js") || name.endsWith(".jsx");
                    })
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** 本项目新增的全部前端文件 */
    private static List<Path> projectFiles() {
        List<Path> out = new ArrayList<>();
        out.addAll(filesUnder(VIEWS_STUDY));
        out.addAll(filesUnder(LAYOUTS_STUDY));
        out.addAll(filesUnder(COMPONENTS_STUDY));
        return out.stream().distinct().sorted().toList();
    }

    /**
     * 学习端（孩子端）范围：layouts/study + views/study（不含 parent、不含 manage）。
     *
     * <p>管理端不在这个集合里——理由见 {@link #VIEWS_MANAGE}。</p>
     */
    private static List<Path> learningFiles() {
        List<Path> out = new ArrayList<>(filesUnder(LAYOUTS_STUDY));
        for (Path file : filesUnder(VIEWS_STUDY)) {
            String path = rel(file);
            if (path.contains("views/study/parent/") || path.contains("views/study/manage/")) {
                continue;
            }
            out.add(file);
        }
        for (Path file : filesUnder(COMPONENTS_STUDY)) {
            if (!PARENT_SHARED_COMPONENTS.contains(file.getFileName().toString())) {
                out.add(file);
            }
        }
        return out.stream().distinct().sorted().toList();
    }

    /** 家长端 + 学习端（都不适用管理端列表骨架与表单骨架的那一端） */
    private static List<Path> parentAndLearningFiles() {
        List<Path> out = new ArrayList<>(learningFiles());
        out.addAll(parentFiles());
        return out.stream().distinct().sorted().toList();
    }

    /** 家长端范围 */
    private static List<Path> parentFiles() {
        return filesUnder(VIEWS_PARENT);
    }

    /** 家长端会用到的共享组件 */
    private static List<Path> parentSharedComponents() {
        List<Path> out = new ArrayList<>();
        for (Path file : filesUnder(COMPONENTS_STUDY)) {
            if (PARENT_SHARED_COMPONENTS.contains(file.getFileName().toString())) {
                out.add(file);
            }
        }
        return out;
    }

    /** 学习端页面（views/study 下的 index.vue，不含 parent、不含 manage） */
    private static List<Path> learningPageFiles() {
        List<Path> out = new ArrayList<>();
        for (Path file : filesUnder(VIEWS_STUDY)) {
            String path = rel(file);
            if (path.contains("views/study/parent/") || path.contains("views/study/manage/")) {
                continue;
            }
            if (!path.endsWith("index.vue")) {
                continue;
            }
            out.add(file);
        }
        return out;
    }

    /** 家长端页面（views/study/parent 下的 index.vue，不含 composable） */
    private static List<Path> parentPageFiles() {
        List<Path> out = new ArrayList<>();
        for (Path file : parentFiles()) {
            if (rel(file).endsWith("index.vue")) {
                out.add(file);
            }
        }
        return out;
    }

    /** 是否属于家长端口径（家长端页面 + 家长端共用组件） */
    private static boolean isParentScoped(Path file) {
        String path = rel(file);
        if (path.contains("views/study/parent/")) {
            return true;
        }
        return PARENT_SHARED_COMPONENTS.contains(file.getFileName().toString());
    }

    /**
     * 去掉 HTML 注释、块注释与行注释。
     *
     * <p><b>字面量扫描类断言必须先剥注释</b>（本仓既有纪律）：
     * 注释里为了说明「这些字样不能出现」而引用的字样，不是用户看得见的文案，
     * 不剥注释就会把说明当成违规。</p>
     *
     * @param content 原始文本
     * @return 去掉注释后的文本
     */
    private static String stripComments(String content) {
        String out = content;
        out = out.replaceAll("(?s)<!--.*?-->", " ");
        out = out.replaceAll("(?s)/\\*.*?\\*/", " ");
        out = out.replaceAll("(?m)//[^\\n]*", " ");
        return out;
    }

    /**
     * less 颜色函数作用在<b>变量</b>上（{@code fade(@primary-color, 8%)} 这类）。
     *
     * <p>这类写法<b>不是硬编码色值</b>——底色来自 less 变量，改一处全局生效，
     * 与「直接写 rgba(0,0,0,0.45)」性质不同。旧的判定只认「值以 @ 开头」，
     * 会把变量派生色误判成硬编码，因此放宽到这里。</p>
     *
     * @param value CSS 声明值
     * @return true = 变量派生的颜色
     */
    private static boolean isLessColorFunctionOfVariable(String value) {
        return Pattern.compile(
                "^(fade|fadein|fadeout|darken|lighten|saturate|desaturate|spin|mix|shade|tint)\\(\\s*@[^,)]+")
                .matcher(value)
                .find();
    }

    private static String read(Path file) {
        return file == null ? "" : RedLineScanner.read(file);
    }

    private static String rel(Path file) {
        return RedLineScanner.frontendRoot().relativize(file).toString().replace('\\', '/');
    }

    /** 提取用户可见中文文案（模板文本 + 引号字符串），与红线扫描口径一致 */
    private static List<String> chineseTexts(String content) {
        List<String> out = new ArrayList<>();
        Matcher m = VUE_TEXT.matcher(content);
        while (m.find()) {
            out.add(m.group(1));
        }
        Matcher q = QUOTED.matcher(content);
        while (q.find()) {
            String raw = q.group(1);
            if (raw.codePoints().anyMatch(c -> c >= 0x4e00 && c <= 0x9fa5)) {
                out.add(raw);
            }
        }
        return out;
    }

    /** 扫描中文文案里的禁止措辞 */
    private static List<String> copyHits(List<Path> files, List<String> forbidden) {
        Set<String> hits = new LinkedHashSet<>();
        for (Path file : files) {
            // 先剥注释：只扫用户真正看得见的文案
            for (String text : chineseTexts(stripComments(read(file)))) {
                for (String word : forbidden) {
                    if (text.contains(word)) {
                        hits.add(rel(file) + " → 「" + text.trim() + "」含「" + word + "」");
                    }
                }
            }
        }
        return new ArrayList<>(hits);
    }

    private static int countMatches(String content, String token) {
        int count = 0;
        int index = content.indexOf(token);
        while (index >= 0) {
            count++;
            index = content.indexOf(token, index + token.length());
        }
        return count;
    }

    @ParameterizedTest(name = "TC-08-18 占位：{0} 需浏览器实测，未自动化")
    @ValueSource(strings = {"1280", "1366", "1920"})
    @DisplayName("TC-08-18 桌面无横向溢出（静态部分：不得出现固定超宽容器）")
    void tc08_18_noFixedOverwideContainer(String viewport) {
        List<String> hits = new ArrayList<>();
        for (Path file : projectFiles()) {
            Matcher m = Pattern.compile("min-width\\s*:\\s*(\\d+)px").matcher(read(file));
            while (m.find()) {
                if (Integer.parseInt(m.group(1)) > 992) {
                    hits.add(rel(file) + " → min-width: " + m.group(1) + "px");
                }
            }
        }
        assertThat(hits)
                .as("TC-08-18 静态部分：不得出现超过移动端阈值的固定最小宽度（视口 " + viewport
                        + " 的 scrollWidth===clientWidth 仍需浏览器实测）")
                .isEmpty();
    }
}
