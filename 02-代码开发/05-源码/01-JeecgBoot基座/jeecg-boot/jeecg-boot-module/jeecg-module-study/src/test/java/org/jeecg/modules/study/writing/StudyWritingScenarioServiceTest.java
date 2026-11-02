package org.jeecg.modules.study.writing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.study.support.StudyTestSupport;
import org.jeecg.modules.study.writing.service.StudyWritingScenarioService;
import org.jeecg.modules.study.writing.vo.WritingScenarioListVO;
import org.jeecg.modules.study.writing.vo.WritingScenarioThemeListVO;
import org.jeecg.modules.study.writing.vo.WritingScenarioThemeVO;
import org.jeecg.modules.study.writing.vo.WritingScenarioVO;

/**
 * 写作情境库自动化测试（F-WRT-03 / TC-06-10 … TC-06-15）。
 *
 * <p>验的是三件必须守住的事：</p>
 * <ul>
 *   <li><b>同一主题下给的是不同处境</b>（不同年龄、不同细节），而不是同一个处境换皮；</li>
 *   <li><b>只给处境不给金句</b> —— 响应里没有任何可以直接抄走的句子类字段；</li>
 *   <li><b>情境库是内容表</b> —— 表里没有 child_id，不同主题的情境互不串。</li>
 * </ul>
 *
 * <p><b>前置</b>：本类在 {@code @BeforeAll} 里检查 {@code study_writing_scenario}
 * 是否存在；不存在就执行增量脚本 {@code 02-代码开发/02-数据库/03-脚本/12-writing-scenario.sql}
 * （幂等）。脚本找不到时直接失败，并给出可执行的提示，<b>绝不静默跳过</b>。</p>
 *
 * <p><b>数据隔离</b>：本类自己造的数据业务ID 一律以 {@code UT} 开头，
 * 并在 {@code @AfterEach} 里删干净，不留残余。</p>
 *
 * @author jeecg-study
 */
class StudyWritingScenarioServiceTest extends StudyTestSupport {

    /** 增量脚本相对仓库根的路径 */
    private static final String SCRIPT_RELATIVE =
            "02-代码开发/02-数据库/03-脚本/12-writing-scenario.sql";

    /** 表名 */
    private static final String TABLE = "study_writing_scenario";

    /** 本轮测试用的主题 A 编码 */
    private String themeA;

    /** 本轮测试用的主题 B 编码 */
    private String themeB;

    /** 本轮测试用的情境业务ID */
    private final List<String> scenarioCodes = new ArrayList<>();

    @Autowired
    private StudyWritingScenarioService scenarioService;

    // ------------------------------------------------------------------ 夹具

    @BeforeAll
    static void markBeforeAll() {
        // 表存在性检查必须在实例方法里做（要拿到注入的 jdbc），
        // 因此放在 @BeforeEach 的 seedScenarios() 里，见 ensureScenarioTable()。
    }

    @BeforeEach
    void seedScenarios() throws IOException {
        ensureScenarioTable();

        String suffix = unique();
        themeA = "UTT_A_" + suffix;
        themeB = "UTT_B_" + suffix;

        insert(themeA, "UT主题甲", "UTS_A1_" + suffix, "UT处境一", "小学低年级", 6, 8, "G1", 1,
                "[\"线索一\",\"线索二\",\"线索三\"]");
        insert(themeA, "UT主题甲", "UTS_A2_" + suffix, "UT处境二", "初中", 13, 15, "G8", 4,
                "[\"线索四\",\"线索五\"]");
        insert(themeB, "UT主题乙", "UTS_B1_" + suffix, "UT处境三", "小学中年级", 9, 11, "G4", 2,
                "[\"线索六\"]");
    }

    @AfterEach
    void cleanScenarios() {
        jdbc.update("DELETE FROM " + TABLE + " WHERE scenario_code LIKE 'UTS_%'");
        jdbc.update("DELETE FROM " + TABLE + " WHERE scenario_code LIKE 'UT%'");
    }

    // ------------------------------------------------------------------ 用例

    @Test
    @DisplayName("TC-06-10 同一主题下给的是不同处境（年龄 / 细节都不同）")
    void sameThemeGivesDifferentSituations() {
        WritingScenarioListVO vo = scenarioService.listScenarios(themeA, null);

        assertThat(vo).as("同一主题下应能取到多个情境").isNotNull();
        assertThat(vo.getItems()).as("主题 A 下应有两条情境").hasSize(2);
        assertThat(vo.getTotal()).isEqualTo(2);

        WritingScenarioVO first = vo.getItems().get(0);
        WritingScenarioVO second = vo.getItems().get(1);
        assertThat(first.getTitle()).isNotEqualTo(second.getTitle());
        assertThat(first.getAgeStage()).isNotEqualTo(second.getAgeStage());
        assertThat(first.getAgeMin()).isNotEqualTo(second.getAgeMin());
        assertThat(first.getStageCode()).isNotEqualTo(second.getStageCode());
        assertThat(first.getDetailClues()).isNotEqualTo(second.getDetailClues());
    }

    @Test
    @DisplayName("TC-06-11 只给处境不给金句：响应里没有可直接抄走的句子类字段")
    void scenarioHasNoCopyableSentenceField() {
        List<String> banned = Arrays.asList(
                "quote", "aphorism", "golden", "modelanswer", "sampleanswer", "referenceanswer",
                "essay", "excerpt", "famousline", "poem", "verse");
        List<String> hits = new ArrayList<>();
        for (Field field : WritingScenarioVO.class.getDeclaredFields()) {
            String name = field.getName().toLowerCase(Locale.ROOT);
            for (String word : banned) {
                if (name.contains(word)) {
                    hits.add("WritingScenarioVO#" + field.getName());
                }
            }
        }
        assertThat(hits).as("情境响应里不得出现可直接抄走的句子类字段").isEmpty();

        WritingScenarioVO scenario = scenarioService.getScenario(scenarioCodes.get(0));
        assertThat(scenario.getSituationDesc()).as("处境描述要写清楚处境").isNotBlank();
        assertThat(scenario.getPromptQuestion()).as("必须有引导填自己经历的提示题").isNotBlank();
        assertThat(scenario.getRestrictionNote()).as("必须写明限制条件").isNotBlank();
        assertThat(scenario.getForbiddenWords()).as("必须写明禁用提示").isNotBlank();
    }

    @Test
    @DisplayName("TC-06-12 不同主题的情境互不串")
    void scenariosOfDifferentThemesDoNotLeak() {
        WritingScenarioListVO onlyA = scenarioService.listScenarios(themeA, null);
        assertThat(onlyA.getItems()).hasSize(2);
        for (WritingScenarioVO item : onlyA.getItems()) {
            assertThat(item.getThemeCode()).isEqualTo(themeA);
            assertThat(item.getScenarioCode()).doesNotContain(scenarioCodes.get(2));
        }

        WritingScenarioListVO onlyB = scenarioService.listScenarios(themeB, null);
        assertThat(onlyB.getItems()).hasSize(1);
        assertThat(onlyB.getItems().get(0).getThemeCode()).isEqualTo(themeB);
    }

    @Test
    @DisplayName("TC-06-13 情境详情能解析出细节线索（JSON 数组 → 列表）")
    void detailCluesAreParsedIntoList() {
        WritingScenarioVO scenario = scenarioService.getScenario(scenarioCodes.get(0));

        assertThat(scenario.getScenarioCode()).isEqualTo(scenarioCodes.get(0));
        assertThat(scenario.getDetailClues()).as("细节线索应被解析成数组").containsExactly("线索一", "线索二", "线索三");
    }

    @Test
    @DisplayName("TC-06-14 主题清单按主题聚合并给出条数")
    void themeListGroupsScenariosByTheme() {
        WritingScenarioThemeListVO vo = scenarioService.listThemes(null);

        assertThat(vo).isNotNull();
        assertThat(vo.getItems()).isNotEmpty();

        WritingScenarioThemeVO found = null;
        for (WritingScenarioThemeVO theme : vo.getItems()) {
            if (themeA.equals(theme.getThemeCode())) {
                found = theme;
                break;
            }
        }
        assertThat(found).as("主题清单里应能看到本轮造的主题").isNotNull();
        assertThat(found.getThemeName()).isEqualTo("UT主题甲");
        assertThat(found.getScenarioCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("TC-06-15 情境库是内容表：表里没有 child_id 列")
    void scenarioTableHasNoChildIdColumn() {
        Integer childIdColumns = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = 'child_id'",
                Integer.class, TABLE);

        assertThat(childIdColumns)
                .as("情境库是全局共享的内容表，不能有 child_id 列（内容表全局共享 / 行为表按孩子隔离）")
                .isZero();
    }

    @Test
    @DisplayName("TC-06-16 取不到的情境返回失败，不返回空壳数据")
    void unknownScenarioIsRejected() {
        JeecgBootException error = catchThrowableOfType(
                () -> scenarioService.getScenario("UTS_NOT_EXIST_" + unique()),
                JeecgBootException.class);

        assertThat(error).as("不存在的情境必须明确报错").isNotNull();
        assertThat(error.getMessage()).contains("情境不存在");
    }

    @Test
    @DisplayName("TC-06-17 空情境编码被拒绝")
    void blankScenarioCodeIsRejected() {
        assertThatThrownBy(() -> scenarioService.getScenario("  "))
                .isInstanceOf(JeecgBootException.class);
    }

    // ------------------------------------------------------------------ 内部

    /**
     * 造一条 UT 情境。
     */
    private void insert(String themeCode, String themeName, String scenarioCode, String title,
                        String ageStage, int ageMin, int ageMax, String stageCode, int difficulty,
                        String detailClue) {
        scenarioCodes.add(scenarioCode);
        jdbc.update("INSERT INTO " + TABLE
                        + " (id, scenario_code, theme_code, theme_name, title, situation_desc,"
                        + " age_stage, age_min, age_max, detail_clue, stage_code, genre, difficulty,"
                        + " prompt_question, restriction_note, forbidden_words, order_weight,"
                        + " source_ref, copyright_type, status, del_flag, create_by, create_time)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 0, ?, NOW())",
                IdWorker.get32UUID(), scenarioCode, themeCode, themeName, title, "UT 处境描述",
                ageStage, ageMin, ageMax, detailClue, stageCode, "写事", difficulty,
                "UT 提示题：那天你的手在做什么？", "只写自己身上发生过的事", "不要写成口号", 10,
                "UT 自编", "SELF", "mavis");
    }

    /**
     * 表不存在时执行增量脚本把它建出来（幂等）。
     *
     * <p>脚本找不到时直接失败，并给出可执行的提示，<b>绝不静默跳过</b>。</p>
     */
    private void ensureScenarioTable() throws IOException {
        if (tableExists()) {
            return;
        }
        Path script = locateScript();
        if (script == null) {
            throw new IllegalStateException(
                    "表 " + TABLE + " 不存在，且找不到增量脚本 " + SCRIPT_RELATIVE
                            + "；请先执行该脚本后再跑本用例");
        }
        applyScript(script);
    }

    /**
     * 表是否已存在。
     */
    private boolean tableExists() {
        try {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.TABLES "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?", Integer.class, TABLE);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 定位增量脚本：先从当前工作目录向上找，再做一次带剪枝的向下查找。
     *
     * <p>找不到就返回 null（由调用方给出明确失败原因）。</p>
     */
    private static Path locateScript() {
        Path start = Paths.get("").toAbsolutePath();
        for (Path current = start; current != null; current = current.getParent()) {
            Path candidate = resolveRelative(current, SCRIPT_RELATIVE);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        List<String> skip = Arrays.asList("node_modules", ".git", "target", "dist", ".idea", ".gradle");
        java.util.Deque<Path> queue = new java.util.ArrayDeque<>();
        queue.add(start);
        int visited = 0;
        while (!queue.isEmpty() && visited < 4000) {
            Path dir = queue.poll();
            visited++;
            List<Path> children = new ArrayList<>();
            try (Stream<Path> stream = Files.list(dir)) {
                stream.forEach(children::add);
            } catch (IOException e) {
                continue;
            }
            for (Path child : children) {
                if (!Files.isDirectory(child)) {
                    continue;
                }
                String name = child.getFileName().toString();
                if (skip.contains(name)) {
                    continue;
                }
                Path candidate = resolveRelative(child, SCRIPT_RELATIVE);
                if (Files.isRegularFile(candidate)) {
                    return candidate;
                }
                queue.add(child);
            }
        }
        return null;
    }

    /**
     * 按 {@code /} 分隔的相对路径逐段拼接（{@code Paths.get(String[])} 不存在）。
     */
    private static Path resolveRelative(Path base, String relative) {
        Path current = base;
        for (String segment : relative.split("/")) {
            if (!segment.isEmpty()) {
                current = current.resolve(segment);
            }
        }
        return current;
    }

    /**
     * 执行增量脚本（幂等）。按分号切语句，切分时尊重字符串里的引号。
     */
    private void applyScript(Path script) throws IOException {
        String text = new String(Files.readAllBytes(script), StandardCharsets.UTF_8);
        StringBuilder cleaned = new StringBuilder();
        for (String line : text.split("\n")) {
            if (line.trim().startsWith("--")) {
                continue;
            }
            cleaned.append(line).append('\n');
        }
        for (String statement : splitStatements(cleaned.toString())) {
            jdbc.execute(statement);
        }
    }

    /**
     * 按分号切 SQL（字符串里的分号不切）。
     */
    private static List<String> splitStatements(String sql) {
        List<String> out = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        boolean inString = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (inString) {
                buffer.append(c);
                if (c == '\'') {
                    if (i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                        buffer.append('\'');
                        i++;
                        continue;
                    }
                    inString = false;
                }
                continue;
            }
            if (c == '\'') {
                inString = true;
                buffer.append(c);
                continue;
            }
            if (c == ';') {
                if (!buffer.toString().trim().isEmpty()) {
                    out.add(buffer.toString().trim());
                }
                buffer.setLength(0);
                continue;
            }
            buffer.append(c);
        }
        if (!buffer.toString().trim().isEmpty()) {
            out.add(buffer.toString().trim());
        }
        return out;
    }
}
