package org.jeecg.modules.study.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 时区契约守卫。
 *
 * <p><b>为什么需要这条断言</b><br>
 * 应用把 JDBC 的 {@code serverTimezone} 写死成 {@code Asia/Shanghai}，
 * 于是「DB 会话时区」被钉死在 +08:00。此时如果 JVM 跑在别的时区
 * （GitHub 的 ubuntu runner 默认就是 UTC），datetime 列会出现**双向 8 小时漂移**：
 * <ul>
 *   <li>写入：Java 的 Timestamp 按连接时区（+08:00）落库；</li>
 *   <li>读出：DATETIME 按 JVM 时区（UTC）解释 ⇒ 读回来比写进去晚了 8 小时。</li>
 * </ul>
 * 2026-11-04 在 CI 上实测打挂 3 个用例：
 * <ul>
 *   <li>{@code StudyKidAuthServiceTest.login_fiveFailures_locksTenMinutes}
 *       —— 锁定期期望 8~10 分钟，实测 489 分钟；</li>
 *   <li>{@code StudyErrorPoolServiceTest.wrongAnswer_resetsScheduleToSameDay}
 *       —— 排程日期算成明天，撞 {@code uk_schedule_error_date} 唯一键；</li>
 *   <li>{@code StudyErrorPoolServiceTest.completeReview_sameDay_onlyAdvancesOnce}。</li>
 * </ul>
 * 本地 Windows 是 +08:00，所以**本地永远绿、只有 CI 才红** ——
 * 属于最难自查的那一类错。把契约写成断言，环境不对时立刻说清楚，
 * 而不是甩一堆看不懂的断言失败。
 *
 * <p><b>怎么修</b>：跑测试/部署时把 JVM 时区设成与 {@code serverTimezone} 一致，
 * CI 里是 {@code env.TZ=Asia/Shanghai}。
 */
// update-begin---author:mavis ---date:2026-11-04  for：【CI 修复】新增时区契约守卫：JVM 默认时区必须与 JDBC 的 serverTimezone 一致，否则 datetime 列写进去再读出来会整体漂移 8 小时-----------
public class StudyTimeZoneContractTest {

    /** 从 JDBC URL 里抠出 serverTimezone 的值。 */
    private static final Pattern SERVER_TIME_ZONE =
            Pattern.compile("serverTimezone=([A-Za-z0-9_+\\-/:]+)");

    @Test
    @DisplayName("TC-ENV-01 JVM 默认时区必须与 JDBC serverTimezone 对齐")
    void jvmDefaultZoneMustMatchJdbcServerTimeZone() throws IOException {
        String config = readTestDataSourceConfig();
        Matcher matcher = SERVER_TIME_ZONE.matcher(config);
        assertThat(matcher.find())
                .as("application-test.yml 的 JDBC URL 里必须写明 serverTimezone，"
                        + "否则这条守卫无从比对（且说明时区契约已丢失）")
                .isTrue();

        String serverTimeZone = matcher.group(1);
        ZoneId dbZone = ZoneId.of(serverTimeZone);
        ZoneId jvmZone = ZoneId.systemDefault();
        Instant now = Instant.now();

        long dbOffsetMinutes = dbZone.getRules().getOffset(now).getTotalSeconds() / 60L;
        long jvmOffsetMinutes = jvmZone.getRules().getOffset(now).getTotalSeconds() / 60L;

        assertThat(jvmOffsetMinutes)
                .as("JVM 默认时区(%s) 与 JDBC serverTimezone(%s) 必须同一时刻偏移一致；"
                        + "当前相差 %d 分钟 ⇒ datetime 列写进去再读出来会整体漂移，"
                        + "跑测试前请把 JVM 时区设成与 serverTimezone 一致"
                        + "（CI 里是 env.TZ=%s）",
                        jvmZone, serverTimeZone, dbOffsetMinutes - jvmOffsetMinutes, serverTimeZone)
                .isEqualTo(dbOffsetMinutes);
    }

    @Test
    @DisplayName("TC-ENV-02 业务时区固定为 Asia/Shanghai（不随机器时区漂移）")
    void businessZoneIsPinnedToShanghai() {
        // 产品面向国内用户，「今天」必须是北京时间，不能跟着跑测机器的时区走。
        ZoneOffset shanghai = ZoneOffset.ofHours(8);
        ZoneId dbZone = ZoneId.of("Asia/Shanghai");
        assertThat(dbZone.getRules().getOffset(Instant.now()))
                .as("Asia/Shanghai 必须恒为 +08:00（无夏令时）")
                .isEqualTo(shanghai);
    }

    /** 读测试数据源配置（classpath 上的 application-test.yml）。 */
    private static String readTestDataSourceConfig() throws IOException {
        try (InputStream in = StudyTimeZoneContractTest.class.getResourceAsStream("/application-test.yml")) {
            assertThat(in).as("classpath 上必须有 application-test.yml").isNotNull();
            byte[] raw = in.readAllBytes();
            return new String(raw, StandardCharsets.UTF_8);
        }
    }
}
// update-end---author:mavis ---date:2026-11-04  for：【CI 修复】新增时区契约守卫：JVM 默认时区必须与 JDBC 的 serverTimezone 一致，否则 datetime 列写进去再读出来会整体漂移 8 小时-----------
