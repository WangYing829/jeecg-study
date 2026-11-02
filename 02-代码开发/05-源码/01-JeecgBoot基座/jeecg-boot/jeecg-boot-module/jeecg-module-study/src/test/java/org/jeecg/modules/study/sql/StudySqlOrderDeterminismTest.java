package org.jeecg.modules.study.sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.jeecg.modules.study.redline.RedLineScanner;

/**
 * **SQL 排序确定性的可执行断言套件**。
 *
 * <p>TC-03-01 要求「课内单元顺序一致」。首轮 {@code mvn test} 复跑时暴露
 * {@code StudyWordServiceTest.unitMapsToTextbookOneToOne} 偶发失败：
 * {@code StudyWordMapper.selectUnitStats} 的 {@code ORDER BY MIN(unit_order)}
 * 没有唯一兜底键，两个单元的最小序号并列时，结果顺序随执行计划漂移。
 * 同一轮排查还发现家长端日序列聚合、薄弱考点 TOP-N、相关题目截断等同类写法。</p>
 *
 * <p>本类把「排序必须确定」落成源码级静态断言，规则只有三条，都是
 * <b>必要条件</b>、零误报：</p>
 * <ul>
 *   <li><b>SQL-A</b>：含 {@code GROUP BY} 的 SQL 必须带 {@code ORDER BY}
 *       —— 聚合结果的行顺序不能随执行计划漂移（家长端日序列趋势图尤其依赖它）。</li>
 *   <li><b>SQL-B</b>：含 {@code LIMIT n}（n &gt; 1 或参数化）的 SQL 必须带 {@code ORDER BY}
 *       —— 无序截断等于随机丢弃数据。</li>
 *   <li><b>SQL-C</b>：含 {@code LIMIT 1} 的 SQL，其 {@code ORDER BY} 的末位排序键
 *       必须落在该表的唯一索引上 —— 「最近一条」在并列时必须可判定。</li>
 * </ul>
 *
 * <p><b>唯一性判定依据真实库结构</b>：{@link #UNIQUE_INDEXES} 里的唯一索引清单
 * <b>不再手写</b>，而是由脚本 {@code 05-公共资源/2026103021-唯一索引清单生成.py}
 * 从 {@code information_schema.STATISTICS} 连库导出、写成 classpath 资源
 * {@code study-unique-indexes.json}；组合唯一索引（如 {@code uk_family_user(family_id,user_id)}）
 * 与分区表（{@code study_practice_record_YYYYMM}）都已按前缀规则处理。
 * 手写快照会过期、来源不可追溯，所以改成脚本化产物（ADR-031 · P4）：
 * 加表 / 改索引后重跑脚本即可，清单与真实库的一致性由脚本的 {@code --check} 负责。</p>
 *
 * <p><b>扫描范围</b>：只扫本模块 {@code src/main/java}（{@link RedLineScanner#studyMainRoot()}），
 * 不扫上游基座，避免把上游几百个 demo 页面/Mapper 拉进来。</p>
 *
 * @author jeecg-study
 */
class StudySqlOrderDeterminismTest {

    // update-begin---author:mavis ---date:2026-10-31  for：【TC-03-01】唯一索引清单覆盖性断言要区分「静态表名」与「MyBatis 动态表名占位符」——${tableName} 是按月分表的合法写法（运行时 study_practice_record_yyyyMM，表族已在清单内），把它当真实表名会凭空造出红线违规；未登记的占位符仍算违规，不放宽-----------
    // update-end---author:mavis ---date:2026-10-31  for：【TC-03-01】唯一索引清单覆盖性断言要区分「静态表名」与「MyBatis 动态表名占位符」——${tableName} 是按月分表的合法写法（运行时 study_practice_record_yyyyMM，表族已在清单内），把它当真实表名会凭空造出红线违规；未登记的占位符仍算违规，不放宽-----------

    // ------------------------------------------------------------------ 扫描范围

    /** 主源码根（由 RedLineScanner 统一解析，私有运行器与 mvn test 同口径） */
    private static final Path MAIN_ROOT = RedLineScanner.studyMainRoot();

    // ------------------------------------------------------------------ 正则

    /** {@code @Select(...)} 注解整体（含多行字符串拼接） */
    private static final Pattern SELECT_ANNOTATION =
            Pattern.compile("@Select\\s*\\((.*?)\\)\\s*(?:\\r?\\n|$)", Pattern.DOTALL);

    /** Java 字符串字面量（处理转义双引号） */
    private static final Pattern STRING_LITERAL =
            Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

    /** GROUP BY */
    private static final Pattern GROUP_BY = Pattern.compile("\\bGROUP\\s+BY\\b", Pattern.CASE_INSENSITIVE);

    /** ORDER BY */
    private static final Pattern ORDER_BY = Pattern.compile("\\bORDER\\s+BY\\b", Pattern.CASE_INSENSITIVE);

    /** LIMIT n 或 LIMIT #{param} */
    private static final Pattern LIMIT =
            Pattern.compile("\\bLIMIT\\s+(\\d+|#\\{[A-Za-z0-9_]+\\})", Pattern.CASE_INSENSITIVE);

    /** ORDER BY 段：到 LIMIT / 结尾为止 */
    private static final Pattern ORDER_SEGMENT =
            Pattern.compile("\\bORDER\\s+BY\\b(.*?)(?:\\bLIMIT\\b|$)", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    /** DISTINCT */
    private static final Pattern DISTINCT = Pattern.compile("\\bDISTINCT\\b", Pattern.CASE_INSENSITIVE);

    /** 等值唯一条件（列名 = #{参数}） */
    private static final Pattern EQUALITY_PREDICATE =
            Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*#\\{", Pattern.CASE_INSENSITIVE);

    /** 主表名（FROM study_xxx，可带别名） */
    private static final Pattern MAIN_TABLE =
            Pattern.compile("\\bFROM\\s+(study_[a-z0-9_]+|\\$\\{[A-Za-z0-9_]+\\})", Pattern.CASE_INSENSITIVE);

    /** 主键列名（实测全部业务表 PRIMARY 均为 id） */
    private static final String PRIMARY_KEY_COLUMN = "id";

    /** 动态表名占位符前缀（MyBatis {@code ${}} 拼接） */
    private static final String PLACEHOLDER_PREFIX = "${";

    /**
     * 动态表名占位符参数名（小写）→ 运行时表族。
     *
     * <p>实测（2026-10-31）：本模块只有<b>按月分表</b>用 {@code ${tableName}} 拼接，
     * 出现在 4 个 Mapper（练习记录 / 练习题记录 / 数学记录 / 单词练习记录），
     * 运行时表名为 {@code study_practice_record_yyyyMM}，表族 {@code study_practice_record}
     * 已在唯一索引清单内（分区表按最长前缀匹配）。</p>
     *
     * <p>新增动态表时必须在这里登记，否则覆盖性断言会报「未登记的动态表名占位符」——
     * 这是<b>刻意收紧</b>：占位符的运行时表名静态不可知，若放任不管，
     * 清单过期时这类 SQL 就成了检测盲区。</p>
     */
    private static final Map<String, String> DYNAMIC_TABLE_FAMILIES = Map.of(
            "tablename", "study_practice_record");

    // ------------------------------------------------------------------ 唯一索引清单

    /** 生成的唯一索引清单资源（由 05-公共资源/2026103021-唯一索引清单生成.py 产出） */
    private static final String UNIQUE_INDEX_RESOURCE = "/study-unique-indexes.json";

    /** 清单加载失败原因（正常为空；静态初始化顺序要求先于 UNIQUE_INDEXES 声明） */
    private static final java.util.concurrent.atomic.AtomicReference<String> LOAD_ERROR =
            new java.util.concurrent.atomic.AtomicReference<>();

    /**
     * 各业务表的唯一索引（列集合）。
     *
     * <p>来源：<b>生成产物</b> —— classpath 资源 {@code study-unique-indexes.json}，
     * 由脚本 {@code 05-公共资源/2026103021-唯一索引清单生成.py} 从
     * {@code information_schema.STATISTICS}（{@code NON_UNIQUE = 0}）连库导出。
     * 资源缺失或解析失败时清单为空，由「唯一索引清单有效性」用例明确报错，
     * 不会让 SQL-C 悄悄失去豁免依据。</p>
     */
    private static final Map<String, List<Set<String>>> UNIQUE_INDEXES = loadUniqueIndexes();

    /** 清单加载失败原因（正常为 null），供用例给出可读的失败信息 */
    private static final String UNIQUE_INDEX_LOAD_ERROR = loadError();

    /**
     * 读取生成的唯一索引清单。
     *
     * @return 表名 → 唯一索引列集合列表；加载失败返回空 Map
     */
    private static Map<String, List<Set<String>>> loadUniqueIndexes() {
        Map<String, List<Set<String>>> out = new LinkedHashMap<>();
        try (InputStream in = StudySqlOrderDeterminismTest.class
                .getResourceAsStream(UNIQUE_INDEX_RESOURCE)) {
            if (in == null) {
                LOAD_ERROR.set("清单资源缺失：" + UNIQUE_INDEX_RESOURCE
                        + "，请运行 05-公共资源/2026103021-唯一索引清单生成.py 生成");
                return out;
            }
            JsonNode root = new ObjectMapper().readTree(in);
            JsonNode tables = root.path("tables");
            Iterator<String> names = tables.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                List<Set<String>> indexes = new ArrayList<>();
                for (JsonNode index : tables.path(name)) {
                    Set<String> columns = new LinkedHashSet<>();
                    for (JsonNode column : index) {
                        columns.add(column.asText());
                    }
                    if (!columns.isEmpty()) {
                        indexes.add(columns);
                    }
                }
                out.put(name, indexes);
            }
        } catch (Exception e) {
            LOAD_ERROR.set("清单资源解析失败：" + e.getMessage());
            return new LinkedHashMap<>();
        }
        return out;
    }

    /**
     * 取清单加载失败原因。
     *
     * @return 失败原因；正常返回 null
     */
    private static String loadError() {
        return LOAD_ERROR.get();
    }

    // ------------------------------------------------------------------ 用例

    @Test
    @DisplayName("SQL-A 聚合查询必须显式排序：GROUP BY 的 SQL 必须带 ORDER BY")
    void groupByMustBeOrdered() {
        List<String> violations = new ArrayList<>();
        for (SqlCase sql : sqlCases()) {
            if (GROUP_BY.matcher(sql.text).find() && !ORDER_BY.matcher(sql.text).find()) {
                violations.add(sql.where() + " → GROUP BY 缺少 ORDER BY");
            }
        }
        assertThat(violations)
                .as("以下 SQL 含 GROUP BY 却没有 ORDER BY，聚合行顺序会随执行计划漂移")
                .isEmpty();
    }

    @Test
    @DisplayName("SQL-B 截断查询必须显式排序：LIMIT 大于 1 或参数化的 SQL 必须带 ORDER BY")
    void limitedQueryMustBeOrdered() {
        List<String> violations = new ArrayList<>();
        for (SqlCase sql : sqlCases()) {
            String limit = limitOf(sql.text);
            if (limit == null) {
                continue;
            }
            boolean parameterized = limit.startsWith("#{");
            boolean overOne = !parameterized && Integer.parseInt(limit) > 1;
            if ((parameterized || overOne) && !ORDER_BY.matcher(sql.text).find()) {
                violations.add(sql.where() + " → LIMIT " + limit + " 缺少 ORDER BY");
            }
        }
        assertThat(violations)
                .as("以下 SQL 截断了结果集却没有 ORDER BY，被截断哪几行是不确定的")
                .isEmpty();
    }

    @Test
    @DisplayName("SQL-C 最近一条必须可判定：LIMIT 1 的排序末位键必须落在该表唯一索引上")
    void limitOneMustBreakTieWithUniqueColumn() {
        List<String> violations = new ArrayList<>();
        for (SqlCase sql : sqlCases()) {
            String limit = limitOf(sql.text);
            if (limit == null || !"1".equals(limit)) {
                continue;
            }
            if (DISTINCT.matcher(sql.text).find()) {
                // SELECT DISTINCT 的排序列本身唯一
                continue;
            }
            if (matchedByUniqueEquality(sql.text)) {
                // 等值条件已覆盖该表的唯一索引，结果天然唯一
                continue;
            }
            String orderSegment = orderSegment(sql.text);
            if (orderSegment == null) {
                violations.add(sql.where() + " → LIMIT 1 缺少 ORDER BY");
                continue;
            }
            if (!endsWithUniqueColumn(orderSegment, sql.table)) {
                violations.add(sql.where() + " → LIMIT 1 的 ORDER BY 末位键不在该表唯一索引上：" + orderSegment.trim());
            }
        }
        assertThat(violations)
                .as("以下 LIMIT 1 查询的排序末位键不唯一，并列时「最近一条」不可判定")
                .isEmpty();
    }

    @Test
    @DisplayName("扫描有效性：必须真的扫到 SQL，否则上面三条断言会假绿")
    void scannerMustFindSql() {
        List<SqlCase> cases = sqlCases();
        assertThat(cases)
                .as("扫描不到任何 @Select SQL，前置断言形同虚设")
                .hasSizeGreaterThan(30);
        Set<String> mappers = new LinkedHashSet<>();
        for (SqlCase sql : cases) {
            mappers.add(sql.file);
        }
        assertThat(mappers)
                .as("应覆盖到多个 Mapper 文件")
                .hasSizeGreaterThan(5);
    }

    @Test
    @DisplayName("唯一索引清单有效性：清单必须由脚本从真实库导出，不能靠记忆")
    void uniqueIndexCatalogMustBeAlive() {
        assertThat(UNIQUE_INDEXES)
                .as("唯一索引清单为空或加载失败（%s），SQL-C 的豁免会全部失效；"
                        + "请运行 05-公共资源/2026103021-唯一索引清单生成.py 重新生成",
                        UNIQUE_INDEX_LOAD_ERROR)
                .isNotEmpty();
        for (Map.Entry<String, List<Set<String>>> entry : UNIQUE_INDEXES.entrySet()) {
            assertThat(entry.getValue())
                    .as("表 %s 的唯一索引清单不能为空", entry.getKey())
                    .isNotEmpty();
            for (Set<String> columns : entry.getValue()) {
                assertThat(columns)
                        .as("表 %s 的唯一索引列不能为空", entry.getKey())
                        .isNotEmpty();
            }
        }
        // 组合唯一索引必须以「列集合」形式登记，否则 family_id+user_id 会被误判成不唯一
        assertThat(UNIQUE_INDEXES.get("study_family_member"))
                .as("uk_family_user 是组合唯一索引，必须登记为 family_id+user_id")
                .contains(Set.of("family_id", "user_id"));
    }

    /**
     * 清单的<b>覆盖性</b>必须也被验：清单里没有的表，SQL-C 的「等值命中唯一索引」豁免对它们无效。
     *
     * <p>真实踩过的坑（2026-10-31）：语法域上线 4 张新表后，清单还是旧的（19 张），
     * {@code study_grammar_game_question} 不在其中，于是
     * {@code WHERE question_code = ?} 这种**本来就唯一**的查询被判成「LIMIT 1 不可判定」，
     * 凭空造出一条红线违规——看起来像生产缺陷，其实是指纹清单过期。</p>
     *
     * <p>所以这里断言：<b>被扫描到的每一张表都必须能在清单里找到唯一索引</b>；
     * 找不到就是清单过期，必须重新生成，而不是去改 SQL 或改断言迁就。</p>
     */
    @Test
    @DisplayName("唯一索引清单覆盖性：被扫描 SQL 用到的表必须都能在清单里查到")
    void uniqueIndexCatalogMustCoverScannedTables() {
        Set<String> tables = new LinkedHashSet<>();
        for (SqlCase sql : sqlCases()) {
            if (sql.table != null && !sql.table.isEmpty()) {
                tables.add(sql.table);
            }
        }
        assertThat(tables)
                .as("扫描不到任何主表名，覆盖性断言形同虚设")
                .isNotEmpty();

        List<String> missing = new ArrayList<>();
        for (String table : tables) {
            if (table.startsWith(PLACEHOLDER_PREFIX)) {
                // 动态表名占位符：静态无法得知运行时表名，改为按「占位符 → 表族」映射校验。
                // 未登记的占位符直接算违规——否则清单过期时这类 SQL 是检测盲区。
                String param = table.substring(PLACEHOLDER_PREFIX.length(), table.length() - 1)
                        .toLowerCase();
                String family = DYNAMIC_TABLE_FAMILIES.get(param);
                if (family == null) {
                    missing.add(table + "（未登记的动态表名占位符，无法验证其运行时表族）");
                } else if (uniqueIndexesOf(family).isEmpty()) {
                    missing.add(table + "（运行时表族 " + family + " 不在唯一索引清单里）");
                }
                continue;
            }
            // 用 uniqueIndexesOf 而不是 containsKey：分区表要按最长前缀匹配
            if (uniqueIndexesOf(table).isEmpty()) {
                missing.add(table);
            }
        }
        assertThat(missing)
                .as("以下表不在唯一索引清单里，SQL-C 的「等值命中唯一索引」豁免对它们无效，"
                        + "会把本来就唯一的 LIMIT 1 误报成不确定；"
                        + "请运行 05-公共资源/2026103021-唯一索引清单生成.py 重新生成清单")
                .isEmpty();
    }

    // ------------------------------------------------------------------ 工具

    /**
     * 提取全部 {@code @Select} 里的 SQL。
     *
     * @return SQL 列表
     */
    private static List<SqlCase> sqlCases() {
        List<SqlCase> out = new ArrayList<>();
        Path root = MAIN_ROOT;
        if (!Files.isDirectory(root)) {
            return out;
        }
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .forEach(files::add);
        } catch (IOException e) {
            return out;
        }
        for (Path file : files) {
            String source = read(file);
            if (source == null) {
                continue;
            }
            Matcher matcher = SELECT_ANNOTATION.matcher(source);
            while (matcher.find()) {
                String sql = concatLiterals(matcher.group(1));
                if (sql.isEmpty()) {
                    continue;
                }
                out.add(new SqlCase(file.getFileName().toString(), sql));
            }
        }
        return out;
    }

    /**
     * 把注解里的字符串字面量拼成一条 SQL。
     *
     * @param raw 注解参数原文
     * @return SQL 文本
     */
    private static String concatLiterals(String raw) {
        StringBuilder sb = new StringBuilder();
        Matcher matcher = STRING_LITERAL.matcher(raw);
        while (matcher.find()) {
            sb.append(unescape(matcher.group(1))).append(' ');
        }
        return sb.toString();
    }

    /**
     * 反转义 Java 字符串字面量。
     *
     * @param literal 字面量内容
     * @return 解转义结果
     */
    private static String unescape(String literal) {
        return literal.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    /**
     * 读取源文件（UTF-8）。
     *
     * @param path 文件路径
     * @return 文本；失败返回 null
     */
    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 取 LIMIT 的字面量。
     *
     * @param sql SQL
     * @return LIMIT 值；没有返回 null
     */
    private static String limitOf(String sql) {
        Matcher matcher = LIMIT.matcher(sql);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1);
    }

    /**
     * 取 ORDER BY 段。
     *
     * @param sql SQL
     * @return 排序键文本；没有返回 null
     */
    private static String orderSegment(String sql) {
        Matcher matcher = ORDER_SEGMENT.matcher(sql);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1);
    }

    /**
     * 取 SQL 的主表名。
     *
     * @param sql SQL
     * @return 表名；没有返回空串
     */
    private static String tableOf(String sql) {
        Matcher matcher = MAIN_TABLE.matcher(sql);
        if (!matcher.find()) {
            return "";
        }
        return matcher.group(1).toLowerCase();
    }

    /**
     * 取该表的唯一索引清单（分区表按最长前缀匹配）。
     *
     * @param table 表名
     * @return 唯一索引列集合列表；找不到返回空列表
     */
    private static List<Set<String>> uniqueIndexesOf(String table) {
        if (table == null || table.isEmpty()) {
            return List.of();
        }
        List<Set<String>> exact = UNIQUE_INDEXES.get(table);
        if (exact != null) {
            return exact;
        }
        List<Set<String>> best = null;
        int bestLen = -1;
        for (Map.Entry<String, List<Set<String>>> entry : UNIQUE_INDEXES.entrySet()) {
            String prefix = entry.getKey();
            if (table.startsWith(prefix) && prefix.length() > bestLen) {
                best = entry.getValue();
                bestLen = prefix.length();
            }
        }
        return best == null ? List.of() : best;
    }

    /**
     * 排序段末位键是否落在该表的唯一索引上。
     *
     * @param segment 排序段
     * @param table   表名
     * @return true 表示末位键唯一
     */
    private static boolean endsWithUniqueColumn(String segment, String table) {
        List<String> keys = sortKeys(segment);
        if (keys.isEmpty()) {
            return false;
        }
        String last = keys.get(keys.size() - 1);
        if (PRIMARY_KEY_COLUMN.equals(last)) {
            // 实测 19 张业务表的 PRIMARY 均为 id（分区表 study_practice_record_yyyyMM 同），
            // 因此以主键兜底在业务表上恒成立。
            return true;
        }
        List<Set<String>> indexes = uniqueIndexesOf(table);
        for (Set<String> index : indexes) {
            if (index.size() == 1 && index.contains(last)) {
                return true;
            }
            // 组合唯一索引：排序列覆盖全部组合列，且末位键是组合之一 → 整体唯一
            if (index.size() > 1 && keys.containsAll(index) && index.contains(last)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 取出排序段里的排序列名（去别名、去 ASC/DESC）。
     *
     * @param segment 排序段
     * @return 列名列表
     */
    private static List<String> sortKeys(String segment) {
        List<String> out = new ArrayList<>();
        for (String raw : segment.split(",")) {
            String key = raw.trim();
            if (key.isEmpty()) {
                continue;
            }
            key = key.replaceFirst("(?i)\\s+(ASC|DESC)\\s*$", "").trim();
            int dot = key.lastIndexOf('.');
            if (dot >= 0) {
                key = key.substring(dot + 1);
            }
            out.add(key.toLowerCase());
        }
        return out;
    }

    /**
     * 是否存在覆盖唯一索引的等值条件。
     *
     * @param sql SQL
     * @return true 表示结果天然唯一
     */
    private static boolean matchedByUniqueEquality(String sql) {
        Set<String> equality = new LinkedHashSet<>();
        Matcher matcher = EQUALITY_PREDICATE.matcher(sql);
        while (matcher.find()) {
            equality.add(matcher.group(1).toLowerCase());
        }
        if (equality.isEmpty()) {
            return false;
        }
        if (equality.contains(PRIMARY_KEY_COLUMN)) {
            // 等值条件命中主键 → 结果唯一（不依赖表名识别，
            // ${tableName} 这类运行时拼表名的写法也能正确判定）
            return true;
        }
        List<Set<String>> indexes = uniqueIndexesOf(tableOf(sql));
        for (Set<String> index : indexes) {
            if (equality.containsAll(index)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 一条 SQL 及其来源。
     */
    private static final class SqlCase {

        /** 来源文件名 */
        private final String file;

        /** SQL 文本 */
        private final String text;

        /** 主表名 */
        private final String table;

        SqlCase(String file, String text) {
            this.file = file;
            this.text = text;
            this.table = tableOf(text);
        }

        /**
         * 违规描述用的定位串。
         *
         * @return 定位串
         */
        String where() {
            return file + " :: " + firstLine();
        }

        /**
         * SQL 首行摘要。
         *
         * @return 摘要
         */
        private String firstLine() {
            String one = text.replaceAll("\\s+", " ").trim();
            return one.length() > 110 ? one.substring(0, 110) + "…" : one;
        }
    }
}
