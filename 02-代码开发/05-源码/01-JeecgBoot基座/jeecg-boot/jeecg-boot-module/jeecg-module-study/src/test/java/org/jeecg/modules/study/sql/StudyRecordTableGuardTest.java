package org.jeecg.modules.study.sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.jeecg.modules.study.common.record.StudyRecordTableGuard;
import org.jeecg.modules.study.redline.RedLineScanner;
import org.jeecg.modules.study.support.StudyTestSupport;

/**
 * **作答记录按月分表**的可执行断言套件（ADR-045）。
 *
 * <p><b>它守的是什么</b>：作答记录按月分表（{@code study_practice_record_yyyyMM}），
 * 当月表由运维月度任务（{@code 05-partition.sql}）建。2026-11-03 排查发现三件事：</p>
 * <ol>
 *   <li><b>兜底只有数学域有</b>：{@code StudyMathRecordMapper} 里带着一段
 *       「不存在才建」的 DDL，练习域 / 练习取题 / 背单词域<b>一个都没有</b>；</li>
 *   <li><b>兜底挂错了位置</b>：它挂在数学域的写入口上，
 *       而练习域「取一道孩子没做过的题」那条 SQL 带
 *       {@code NOT EXISTS (SELECT 1 FROM ${tableName} ...)} 子查询 ——
 *       <b>当月表不存在时连题都取不到</b>，不只是「记不上分」；</li>
 *   <li><b>月度任务根本没建</b>：本机 238 个计划任务里没有对应项，
 *       {@code 05-partition.sql} 第 12 行注释写着「每月 1 日由定时任务执行（幂等）」，
 *       但那个任务不存在。</li>
 * </ol>
 *
 * <p>本类把三条不变量落成断言：</p>
 * <ul>
 *   <li><b>静态</b>：表族字面量只允许出现在守卫里；运行期建表 DDL 只允许有一份；
 *       实体不得硬编码某个月份的表名；</li>
 *   <li><b>静态</b>：运行期 DDL 与 {@code 05-partition.sql} 的列集合必须一致（三处同步）；</li>
 *   <li><b>DB 支撑</b>：当月分表必须已存在（运维月度任务的产物）；
 *       守卫的兜底建表在真实 MySQL 上必须能跑通且列结构正确。</li>
 * </ul>
 *
 * <p><b>为什么静态断言必须剥掉注释再扫</b>：表族名在 Javadoc 里到处都出现
 * （说明「它是按月分表的」），不剥注释的话这些文档字样会把断言淹成假红；
 * 反过来，只剥注释不剥字符串的话，{@code @TableName("study_practice_record_202610")}
 * 这种真雷又会被当成注释放过。两条都剥，才是对着<b>可执行代码</b>说话。</p>
 *
 * @author jeecg-study
 */
class StudyRecordTableGuardTest extends StudyTestSupport {

    /** 作答记录表族前缀 —— 测试侧独立写死，用来钉住命名契约（不被实现代码牵着走） */
    private static final String TABLE_FAMILY_PREFIX = "study_practice_record_";

    /** 月份格式（与 05-partition.sql 的 DATE_FORMAT(NOW(), '%Y%m') 一致） */
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    /** 守卫所在文件（相对 study 主源码根，小写正斜杠） */
    private static final String GUARD_FILE = "common/record/studyrecordtableguard.java";

    /** 运行期建表 Mapper 所在文件（相对 study 主源码根，小写正斜杠） */
    private static final String TABLE_MAPPER_FILE = "common/record/mapper/studyrecordtablemapper.java";

    /** 月度任务脚本（相对仓库根） */
    private static final String PARTITION_SQL = "02-代码开发/02-数据库/03-脚本/05-partition.sql";

    /** 列定义：反引号包列名 + 类型（索引里的列不带类型，因此不会误命中） */
    private static final Pattern COLUMN_DEF =
            Pattern.compile("`(\\w+)`\\s+(?:varchar|int|tinyint|json|date|datetime)\\b");

    /** package 声明 */
    private static final Pattern PACKAGE_DECL =
            Pattern.compile("^package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);

    /** 实体上的硬编码月份表名（@TableName("study_practice_record_202610") 这类哑雷） */
    private static final Pattern HARDCODED_MONTH_TABLE =
            Pattern.compile("@TableName\\s*\\(\\s*\"[^\"]*_20\\d{4}\"\\s*\\)");

    @Autowired
    private StudyRecordTableGuard guard;

    // ================================================================= 静态：表名唯一真源

    @Test
    @DisplayName("ADR-045 表族字面量只允许出现在守卫里（其余域不得自己拼表名）")
    void tableFamilyLiteralMustLiveOnlyInGuard() {
        List<String> offenders = new ArrayList<>();
        int found = 0;
        for (java.nio.file.Path file : RedLineScanner.javaSources()) {
            String code = stripComments(RedLineScanner.read(file));
            if (!code.contains(TABLE_FAMILY_PREFIX)) {
                continue;
            }
            found++;
            String rel = RedLineScanner.moduleRelative(file);
            if (!GUARD_FILE.equals(rel)) {
                offenders.add(rel);
            }
        }

        assertThat(offenders)
                .as("表族字面量 \"" + TABLE_FAMILY_PREFIX + "\" 只能出现在 " + GUARD_FILE
                        + "（守卫）；其它域自己拼表名 ⇒ 兜底会漏掉那个域")
                .isEmpty();
        // 反证：扫描本身不能是空的（否则这条断言就是假绿）
        assertThat(found)
                .as("扫描有效性：必须至少扫到 1 个含表族字面量的文件（守卫本身），否则断言假绿")
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("ADR-045 运行期建表 DDL 只允许有一份，且必须在 StudyRecordTableMapper")
    void recordTableDdlMustLiveOnlyInRecordTableMapper() {
        List<String> holders = new ArrayList<>();
        for (java.nio.file.Path file : RedLineScanner.javaSources()) {
            String code = stripComments(RedLineScanner.read(file));
            if (!code.contains("CREATE TABLE IF NOT EXISTS")) {
                continue;
            }
            // 认「作答记录分表的建表」：同时含 source_module 与 practice_date 两列
            if (code.contains("source_module") && code.contains("practice_date")) {
                holders.add(RedLineScanner.moduleRelative(file));
            }
        }

        assertThat(holders)
                .as("作答记录分表的运行期建表 DDL 必须唯一（真源），否则「三处同步」无从谈起")
                .containsExactly(TABLE_MAPPER_FILE);
    }

    @Test
    @DisplayName("ADR-045 实体不得硬编码某个月份的表名（哑雷）")
    void noEntityMayHardcodeMonthPartition() {
        List<String> hits = new ArrayList<>();
        for (java.nio.file.Path file : RedLineScanner.javaSources()) {
            String code = stripComments(RedLineScanner.read(file));
            Matcher matcher = HARDCODED_MONTH_TABLE.matcher(code);
            while (matcher.find()) {
                hits.add(RedLineScanner.moduleRelative(file) + " → " + matcher.group(0));
            }
        }

        assertThat(hits)
                .as("实体上的 @TableName 若写死月份（如 study_practice_record_202610），"
                        + "一旦有人给该 Mapper 加上 BaseMapper，MP 会静默把写操作落到那张旧表")
                .isEmpty();
    }

    @Test
    @DisplayName("生产扫描约束：Mapper 接口必须待在 *.mapper 包下，否则启动即失败")
    void mapperInterfacesMustLiveInMapperPackage() {
        List<String> offenders = new ArrayList<>();
        int scanned = 0;
        for (java.nio.file.Path file : RedLineScanner.javaSources()) {
            if (!file.getFileName().toString().endsWith("Mapper.java")) {
                continue;
            }
            scanned++;
            String content = RedLineScanner.read(file);
            Matcher matcher = PACKAGE_DECL.matcher(content);
            if (!matcher.find()) {
                offenders.add(RedLineScanner.moduleRelative(file) + " → 读不到 package 声明");
                continue;
            }
            String pkg = matcher.group(1);
            String lastSegment = pkg.substring(pkg.lastIndexOf('.') + 1);
            // 基座生产扫描：@MapperScan(value={"org.jeecg.**.mapper*"}) —— 末段必须以 mapper 开头
            if (!lastSegment.startsWith("mapper")) {
                offenders.add(RedLineScanner.moduleRelative(file) + " → 包末段 " + lastSegment
                        + " 不以 mapper 开头，生产环境不会被扫描注册");
            }
        }

        assertThat(offenders)
                .as("基座 @MapperScan 只扫 org.jeecg.**.mapper*；Mapper 放在别的包下 ⇒ 生产启动时"
                        + "找不到该 Bean ⇒ 应用起不来（测试用的 @MapperScan 范围更宽，会掩盖这个问题）")
                .isEmpty();
        assertThat(scanned)
                .as("扫描有效性：必须扫到 Mapper 接口，否则这条断言会假绿")
                .isGreaterThan(0);
    }

    @Test
    @DisplayName("三处同步：运行期 DDL 与月度任务脚本 05-partition.sql 的列集合必须一致")
    void runtimeDdlMustMatchPartitionScript() {
        Set<String> fromJava = columnsFromSource(RedLineScanner.read(
                RedLineScanner.studyMainRoot().resolve(TABLE_MAPPER_FILE)));
        Set<String> fromSql = columnsFromSource(RedLineScanner.read(
                RedLineScanner.repoRoot().resolve(PARTITION_SQL)));

        assertThat(fromJava)
                .as("运行期 DDL 的列集合解析不出来，断言会假绿")
                .isNotEmpty();
        assertThat(fromSql)
                .as("05-partition.sql 的列集合解析不出来，断言会假绿")
                .isNotEmpty();
        assertThat(fromJava)
                .as("运行期建表 DDL（StudyRecordTableMapper）与月度任务脚本（05-partition.sql）列集合不一致"
                        + " ⇒ 月度任务建的表与运行期兜底建的表结构会分叉")
                .isEqualTo(fromSql);
    }

    // ================================================================= DB 支撑：当月分表

    @Test
    @DisplayName("跨月不复发：当月分表必须已存在（月度任务 05-partition.sql 的产物）")
    void currentMonthRecordTableMustExistBeforeGuardTouchesIt() {
        String table = currentMonthTable();
        if (!tableExists(table)) {
            LocalDateTime now = LocalDateTime.now();
            if (now.getDayOfMonth() == 1 && now.getHour() < 4) {
                // 月份切换的头几个小时里，月度任务可能还没跑 —— 这不是缺陷
                Assumptions.assumeTrue(false,
                        "月初 4 小时宽限期：月度任务可能尚未执行，当月分表 " + table + " 暂缺");
            }
        }

        assertThat(tableExists(table))
                .as("当月分表 " + table + " 不存在 ⇒ 月度任务（05-partition.sql）未执行或失败；"
                        + "跨月后练习域取题 SQL（NOT EXISTS 子查询）会直接失败，孩子连题都取不到")
                .isTrue();
    }

    @Test
    @DisplayName("兜底有效：守卫「拿表名即确保存在」在真实库上跑得通")
    void guardMustEnsureCurrentMonthTableExists() {
        String table = guard.currentTableName();

        assertThat(table)
                .as("守卫返回的表名必须符合「study_practice_record_yyyyMM」契约")
                .isEqualTo(currentMonthTable());
        assertThat(tableExists(table))
                .as("守卫声称已确保当月分表存在，但库里没有 —— 建表 DDL 可能执行失败")
                .isTrue();
    }

    @Test
    @DisplayName("兜底可用：守卫的建表 DDL 在真实 MySQL 上能建出正确结构的表")
    void guardMustCreateTableWithExpectedColumns() {
        String probe = "study_record_ddl_probe_ut";
        try {
            guard.ensureTable(probe);

            assertThat(tableExists(probe))
                    .as("守卫的建表 DDL 在真实 MySQL 上执行失败（probe=" + probe + "）")
                    .isTrue();
            assertThat(columnsOf(probe))
                    .as("守卫建出来的表列结构与 DDL 源不一致")
                    .isEqualTo(columnsFromSource(RedLineScanner.read(
                            RedLineScanner.studyMainRoot().resolve(TABLE_MAPPER_FILE))));
        } finally {
            jdbc.execute("DROP TABLE IF EXISTS " + probe);
            guard.invalidate();
        }
    }

    @Test
    @DisplayName("三处同步：真实当月分表的列结构与运行期 DDL 源一致")
    void currentMonthTableColumnsMustMatchDdlSource() {
        String table = currentMonthTable();
        Set<String> fromDb = columnsOf(table);
        Set<String> fromDdl = columnsFromSource(RedLineScanner.read(
                RedLineScanner.studyMainRoot().resolve(TABLE_MAPPER_FILE)));

        assertThat(fromDb)
                .as("当月分表 " + table + " 的列集合解析不出来，断言会假绿")
                .isNotEmpty();
        assertThat(fromDb)
                .as("真实分表 " + table + " 的列结构与运行期 DDL 源不一致 ⇒ 三处已分叉")
                .isEqualTo(fromDdl);
    }

    // ================================================================= 工具

    /**
     * 当月分表名（<b>独立于实现代码</b>写死规则，用来钉住命名契约）。
     */
    private String currentMonthTable() {
        return TABLE_FAMILY_PREFIX + YearMonth.now().format(MONTH);
    }

    /**
     * 表是否存在。
     */
    private boolean tableExists(String table) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                Integer.class, table);
        return n != null && n > 0;
    }

    /**
     * 取表的列集合（真实库）。
     */
    private Set<String> columnsOf(String table) {
        List<String> columns = jdbc.queryForList(
                "SELECT COLUMN_NAME FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? "
                        + "ORDER BY ORDINAL_POSITION",
                String.class, table);
        return new LinkedHashSet<>(columns);
    }

    /**
     * 从源码文本里解析列定义集合。
     *
     * <p>列定义的形态是「反引号包列名 + 类型」，索引里的列不带类型 ⇒ 不会误命中。</p>
     */
    private Set<String> columnsFromSource(String source) {
        Set<String> columns = new LinkedHashSet<>();
        Matcher matcher = COLUMN_DEF.matcher(source);
        while (matcher.find()) {
            columns.add(matcher.group(1));
        }
        return columns;
    }

    /**
     * 剥掉块注释与行注释，只留可执行代码。
     *
     * <p>先剥块注释再剥行注释：Javadoc 里出现 {@code //} 时，
     * 先剥行注释会把整段 Javadoc 之后的真代码也吃掉。</p>
     */
    private String stripComments(String source) {
        String noBlock = source.replaceAll("(?s)/\\*.*?\\*/", " ");
        return noBlock.replaceAll("(?m)//.*$", " ");
    }
}
