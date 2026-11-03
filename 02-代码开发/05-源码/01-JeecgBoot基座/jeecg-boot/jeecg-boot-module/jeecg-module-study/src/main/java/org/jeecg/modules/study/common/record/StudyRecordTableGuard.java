package org.jeecg.modules.study.common.record;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

import org.jeecg.modules.study.common.record.mapper.StudyRecordTableMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 作答记录<b>按月分表</b>的表名守卫 —— <b>全项目唯一</b>的「拿当月分表名」入口。
 *
 * <p><b>它解决什么</b>：作答记录按月分表（{@code study_practice_record_yyyyMM}），
 * 当月表由运维月度任务建。但有三类情况会让人拿到一个<b>还不存在</b>的表名：</p>
 * <ol>
 *   <li>月度任务<b>没建</b>（本机实测：计划任务里根本没有这一项）；</li>
 *   <li>月度任务<b>跑了但失败</b>；</li>
 *   <li><b>跨月首日 00:00</b>那一瞬间，上月的表在、当月的表还不存在。</li>
 * </ol>
 *
 * <p><b>后果不只是「记不上分」</b>：练习域「取一道孩子没做过的题」这条 SQL 里带
 * {@code NOT EXISTS (SELECT 1 FROM ${tableName} ...)} 子查询，
 * 表不存在 ⇒ <b>取题直接失败</b> ⇒ 孩子连题都拿不到。
 * 所以兜底必须挂在<b>「拿表名」</b>这一刻，而不是挂在某几个写入口上。</p>
 *
 * <p><b>为什么用「拿表名即确保」而不是「各写入口自己调 ensure」</b>：
 * 四个域（练习 / 练习题取题 / 数学 / 背单词）都在拼这张表名，
 * 只要有一个入口忘了调兜底，跨月就会炸在一个没人想到的地方。
 * 把兜底<b>收进表名解析</b>，任何一处调用 {@link #currentTableName()} 都自动被保护，
 * 漏不掉。</p>
 *
 * <p><b>失败不阻断</b>：建表失败（最常见是账号没有 DDL 权限）时<b>不抛异常</b>，
 * 只记 WARN —— 后续真正的读写会抛出「表不存在」的原始错误，
 * 那才是运维该看到的根因；在这里抛一个「建表失败」反而会掩盖它。</p>
 *
 * <p><b>历史月份不兜底</b>：{@link #tableNameOf(YearMonth)} 只拼名字、不建表。
 * 上月的表本来就该由上月的任务建好；补建历史表没有意义，还可能掩盖「历史表被误删」。</p>
 *
 * @author jeecg-study
 */
@Component
public class StudyRecordTableGuard {

    private static final Logger log = LoggerFactory.getLogger(StudyRecordTableGuard.class);

    /** 作答记录分表前缀（表族名） */
    public static final String TABLE_PREFIX = "study_practice_record_";

    /** 月份格式 */
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    /** 建表 Mapper（DDL 唯一来源） */
    private final StudyRecordTableMapper tableMapper;

    /** 已确认存在的表名；换月后自然失效（表名不同） */
    private volatile String ensuredTable;

    /** 已尝试但建表失败的表名；记下来避免每次调用都重试一次失败的 DDL */
    private volatile String failedTable;

    /**
     * 构造守卫。
     *
     * @param tableMapper 建表 Mapper
     */
    public StudyRecordTableGuard(StudyRecordTableMapper tableMapper) {
        this.tableMapper = tableMapper;
    }

    /**
     * 当月分表名 —— <b>并确保它已存在</b>。
     *
     * <p>这是全项目取「当月作答记录表名」的<b>唯一</b>入口。
     * 任何读/写当月分表的地方都必须经由本方法，不要自己拼字符串。</p>
     *
     * @return 当月分表名（如 {@code study_practice_record_202611}）
     */
    public String currentTableName() {
        String table = tableNameOf(YearMonth.now());
        ensureTable(table);
        return table;
    }

    /**
     * 指定月份的表名 —— <b>只拼名字，不确保存在</b>。
     *
     * <p>用于读历史月份（上月的连续学习天数等）。历史表由历史任务负责，
     * 缺失时调用方自行容错。</p>
     *
     * @param month 月份
     * @return 月表名
     */
    public String tableNameOf(YearMonth month) {
        return TABLE_PREFIX + month.format(MONTH);
    }

    /**
     * 确保某张月表存在（幂等）。
     *
     * <p>成功则记入缓存（换月后自动失效）；失败则记 WARN 并**不抛异常**，
     * 同时把失败表名记下来，避免每次调用都重试一次注定失败的 DDL。</p>
     *
     * @param table 月表名
     */
    public void ensureTable(String table) {
        if (table == null || table.isEmpty()) {
            return;
        }
        if (table.equals(ensuredTable)) {
            return;
        }
        if (table.equals(failedTable)) {
            return;
        }
        try {
            tableMapper.createTableIfAbsent(table);
            ensuredTable = table;
            failedTable = null;
        } catch (Exception e) {
            failedTable = table;
            log.warn("[StudyRecordTableGuard] 月表 {} 未能确保存在（账号可能没有 DDL 权限）：{}",
                    table, e.getMessage());
        }
    }

    /**
     * 清掉失败缓存，允许下次调用重试建表。
     *
     * <p>运维修好权限后需要它；正常运行时不需要调用。</p>
     */
    public void invalidate() {
        failedTable = null;
        ensuredTable = null;
    }
}
