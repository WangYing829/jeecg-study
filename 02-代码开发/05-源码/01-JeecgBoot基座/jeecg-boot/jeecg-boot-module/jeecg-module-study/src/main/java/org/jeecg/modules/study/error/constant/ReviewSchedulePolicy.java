package org.jeecg.modules.study.error.constant;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 复习排期策略（T-V0-07）。
 *
 * <p>排期阶梯（服务端计算，前端不参与）：</p>
 * <ul>
 *   <li>答错 → 下次复习 = <b>当天</b>，连续答对次数归零；</li>
 *   <li>答对 → 依次 <b>+1 / +3 / +7 / +14</b> 天（按连续答对次数 1/2/3/≥4）；</li>
 *   <li><b>连续答对 3 次</b>才移出队列（{@code archived_at} 置值、{@code review_status}=已移出）。</li>
 * </ul>
 *
 * <p><b>为什么是 3 次</b>：移出阈值必须 <b>大于</b>排期阶梯的第 3 档，
 * 否则 {@code +7 天} 那一档在业务上永远走不到（做完就移出，没有下一次复习）。
 * 阈值 = 2 时第 3 次答对即被移出，{@code +7} 档不可达；
 * 阈值 = 3 时 {@code +1 / +3 / +7} 三档全部业务可达，{@code +14} 档成为策略层保留档
 * （连对 3 次即毕业，第 4 次不会发生）。</p>
 *
 * @author jeecg-study
 */
public final class ReviewSchedulePolicy {

    /** 连续答对多少次移出复习队列 */
    // update-begin---author:mavis ---date:2026-10-31  for：【TC-02-08 / TC-02-09】归档阈值由 2 改为 3，使 +1/+3/+7 三档排期业务可达-----------
    public static final int ARCHIVE_STREAK = 3;
    // update-end---author:mavis ---date:2026-10-31  for：【TC-02-08 / TC-02-09】归档阈值由 2 改为 3，使 +1/+3/+7 三档排期业务可达-----------

    /** 排期状态：待复习 */
    public static final int SCHEDULE_PENDING = 1;

    /** 排期状态：已完成 */
    public static final int SCHEDULE_COMPLETED = 2;

    /** 排期状态：已顺延 */
    public static final int SCHEDULE_DEFERRED = 3;

    /** 复习状态：待复习 */
    public static final int REVIEW_PENDING = 1;

    /** 复习状态：已移出 */
    public static final int REVIEW_ARCHIVED = 2;

    /** 掌握度：未知 */
    public static final int MASTERY_UNKNOWN = 1;

    /** 掌握度：学习中 */
    public static final int MASTERY_LEARNING = 2;

    /** 掌握度：熟悉 */
    public static final int MASTERY_FAMILIAR = 3;

    /** 掌握度：掌握 */
    public static final int MASTERY_MASTERED = 4;

    private ReviewSchedulePolicy() {
    }

    /**
     * 连续答对次数 → 下次复习间隔天数。
     *
     * <p>1 → +1 天；2 → +3 天；3 → +7 天；≥4 → +14 天。
     * 注意：连续答对 3 次即移出队列，所以 {@code ≥4 → +14} 这一档在业务上不可达，
     * 仅作为策略层的阶梯定义保留（TC-02-08 覆盖 +1 / +3 / +7 三档业务可达档）。</p>
     *
     * @param correctStreak 连续答对次数（本次作答后）
     * @return 间隔天数
     */
    public static int nextIntervalDays(int correctStreak) {
        if (correctStreak <= 1) {
            return 1;
        }
        if (correctStreak == 2) {
            return 3;
        }
        if (correctStreak == 3) {
            return 7;
        }
        return 14;
    }

    /**
     * 是否达到移出队列的条件。
     *
     * @param correctStreak 连续答对次数（本次作答后）
     * @return 达到则 true
     */
    public static boolean shouldArchive(int correctStreak) {
        return correctStreak >= ARCHIVE_STREAK;
    }

    /**
     * 落库排序分（薄弱度）：连续答对越多，分值越低。
     *
     * @param correctStreak 连续答对次数
     * @return 排序分
     */
    public static BigDecimal baseSortScore(int correctStreak) {
        return new BigDecimal(10 - 2 * correctStreak);
    }

    /**
     * 逾期天数（遗忘风险）：拖得越久越靠前。
     *
     * @param scheduleDate 排期日期
     * @param today        今天
     * @return 逾期天数；未逾期为 0
     */
    public static int overdueDays(LocalDate scheduleDate, LocalDate today) {
        if (scheduleDate == null || today == null) {
            return 0;
        }
        long days = ChronoUnit.DAYS.between(scheduleDate, today);
        return days > 0 ? (int) days : 0;
    }

    /**
     * 变式层级 → 层级名称。
     *
     * @param layer 层级 1换数字 2换情境 3逆向
     * @return 层级名称
     */
    public static String layerName(Integer layer) {
        if (layer == null) {
            return "换数字";
        }
        switch (layer) {
            case 2:
                return "换情境";
            case 3:
                return "逆向";
            default:
                return "换数字";
        }
    }
}
