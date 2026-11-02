package org.jeecg.modules.study.parent.service.impl;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

import org.jeecg.modules.study.common.exception.StudyForbiddenException;
import org.jeecg.modules.study.family.entity.StudyChild;
import org.jeecg.modules.study.family.mapper.StudyChildMapper;
import org.jeecg.modules.study.parent.mapper.ParentReportMapper;
import org.jeecg.modules.study.parent.service.StudyParentReportService;
import org.jeecg.modules.study.parent.vo.ParentDailyItemVO;
import org.jeecg.modules.study.parent.vo.ParentErrorCauseVO;
import org.jeecg.modules.study.parent.vo.ParentErrorReportVO;
import org.jeecg.modules.study.parent.vo.ParentEyeUsageVO;
import org.jeecg.modules.study.parent.vo.ParentMasteryVO;
import org.jeecg.modules.study.parent.vo.ParentOverviewVO;
import org.jeecg.modules.study.parent.vo.ParentStreakVO;
import org.jeecg.modules.study.parent.vo.ParentWeakPointVO;
import org.jeecg.modules.study.parent.vo.ParentWeeklyVO;
import org.jeecg.modules.study.parent.vo.ParentWritingTrendVO;
import org.jeecg.modules.study.scope.service.ChildScopeService;

/**
 * 家长端只读报表服务实现。
 *
 * <p><b>隔离口径</b>：孩子由 {@code childCode} 解析，解析前先过
 * {@link ChildScopeService#assertChildCodeAccess(String)}，
 * 越权一律 4010（不用 404，避免资源探测）。</p>
 *
 * <p><b>口径说明</b>：全部统计都是<b>这个孩子自己的历史趋势</b>，
 * 不与任何其他孩子、任何其他家长做对比（R-04）。</p>
 *
 * @author jeecg-study
 */
@Service
@RequiredArgsConstructor
public class StudyParentReportServiceImpl implements StudyParentReportService {

    /** 趋势窗口天数（周报口径） */
    private static final int WEEK_DAYS = 7;

    /** 薄弱考点最多返回条数 */
    private static final int WEAK_POINT_LIMIT = 5;

    /** 掌握度：1未知 2学习中 3熟悉 4掌握 */
    private static final int MASTERY_UNKNOWN = 1;
    private static final int MASTERY_LEARNING = 2;
    private static final int MASTERY_FAMILIAR = 3;
    private static final int MASTERY_MASTERED = 4;

    private final ChildScopeService scopeService;
    private final StudyChildMapper childMapper;
    private final ParentReportMapper reportMapper;

    @Override
    public ParentOverviewVO overview(String childCode) {
        StudyChild child = resolveChild(childCode);
        String childId = child.getId();
        LocalDate today = currentDate();
        LocalDate weekStart = today.minusDays(WEEK_DAYS - 1);

        ParentOverviewVO vo = new ParentOverviewVO();
        vo.setChildId(childId);
        vo.setChildCode(child.getChildCode());
        vo.setNickname(child.getNickname());
        vo.setEyeUsage(buildEyeUsage(childId, today, weekStart));
        vo.setStreak(buildStreak(childId, today));
        vo.setMastery(buildMastery(childId));
        vo.setErrorTop(buildErrorTop(childId));
        vo.setWeakPoints(buildWeakPoints(childId));
        vo.setWritingTrend(buildWritingTrend(childId, today, weekStart));
        return vo;
    }

    @Override
    public ParentWeeklyVO weekly(String childCode) {
        StudyChild child = resolveChild(childCode);
        String childId = child.getId();
        LocalDate today = currentDate();
        LocalDate from = today.minusDays(WEEK_DAYS - 1);

        Map<LocalDate, ParentDailyItemVO> items = new LinkedHashMap<>();
        for (int i = 0; i < WEEK_DAYS; i++) {
            LocalDate day = from.plusDays(i);
            items.put(day, new ParentDailyItemVO()
                    .setDate(day.toString())
                    .setEyeMinutes(0)
                    .setRestCount(0)
                    .setErrorCount(0)
                    .setWritingCount(0));
        }

        for (Map<String, Object> row : reportMapper.listDailyEyeUsage(childId, from.toString(), today.toString())) {
            ParentDailyItemVO item = items.get(toLocalDate(row.get("usageDate")));
            if (item != null) {
                item.setEyeMinutes(intOf(row.get("eyeMinutes")));
                item.setRestCount(intOf(row.get("restCount")));
            }
        }

        for (Map<String, Object> row : reportMapper.listDailyErrorCount(childId, from.toString(), today.toString())) {
            ParentDailyItemVO item = items.get(toLocalDate(row.get("day")));
            if (item != null) {
                item.setErrorCount(intOf(row.get("cnt")));
            }
        }
        for (Map<String, Object> row : reportMapper.listDailyWritingCount(childId, from.toString(), today.toString())) {
            ParentDailyItemVO item = items.get(toLocalDate(row.get("day")));
            if (item != null) {
                item.setWritingCount(intOf(row.get("cnt")));
            }
        }

        int totalEyeMinutes = 0;
        int totalRestCount = 0;
        int totalErrorCount = 0;
        int totalWritingCount = 0;
        int activeDays = 0;
        List<ParentDailyItemVO> days = new ArrayList<>();
        for (ParentDailyItemVO item : items.values()) {
            totalEyeMinutes += item.getEyeMinutes();
            totalRestCount += item.getRestCount();
            totalErrorCount += item.getErrorCount();
            totalWritingCount += item.getWritingCount();
            if (item.getEyeMinutes() > 0 || item.getErrorCount() > 0 || item.getWritingCount() > 0) {
                activeDays = activeDays + 1;
            }
            days.add(item);
        }

        ParentWeeklyVO vo = new ParentWeeklyVO();
        vo.setChildCode(child.getChildCode());
        vo.setFromDate(from.toString());
        vo.setToDate(today.toString());
        vo.setActiveDays(activeDays);
        vo.setTotalEyeMinutes(totalEyeMinutes);
        vo.setTotalRestCount(totalRestCount);
        vo.setTotalErrorCount(totalErrorCount);
        vo.setTotalWritingCount(totalWritingCount);
        vo.setDays(days);
        return vo;
    }

    @Override
    public ParentErrorReportVO errors(String childCode) {
        StudyChild child = resolveChild(childCode);
        String childId = child.getId();

        ParentErrorReportVO vo = new ParentErrorReportVO();
        vo.setChildCode(child.getChildCode());
        vo.setTotal(intOf(reportMapper.countErrors(childId)));
        vo.setCauses(buildCauseList(reportMapper.errorCauseDistribution(childId)));
        vo.setWeakPoints(buildWeakPoints(childId));
        return vo;
    }

    // ------------------------------------------------------------------ 内部

    /**
     * 解析目标孩子：显式 childCode 优先，否则取当前身份唯一可见的孩子。
     */
    private StudyChild resolveChild(String childCode) {
        String target = childCode == null ? null : childCode.trim();
        if (target != null && !target.isEmpty()) {
            // 越权一律 4010
            scopeService.assertChildCodeAccess(target);
            StudyChild child = childMapper.selectByChildCode(target);
            if (child == null) {
                throw new StudyForbiddenException();
            }
            return child;
        }
        List<String> visible = scopeService.visibleChildIds();
        if (visible == null || visible.isEmpty()) {
            throw new StudyForbiddenException("没有关联的孩子，无法查看学习报告");
        }
        List<String> sorted = new ArrayList<>(visible);
        Collections.sort(sorted);
        StudyChild child = childMapper.selectById(sorted.get(0));
        if (child == null) {
            throw new StudyForbiddenException();
        }
        return child;
    }

    private ParentEyeUsageVO buildEyeUsage(String childId, LocalDate today, LocalDate weekStart) {
        ParentEyeUsageVO vo = new ParentEyeUsageVO();
        vo.setTodayMinutes(intOf(reportMapper.sumEyeMinutes(childId, today.toString())));
        vo.setWeekMinutes(intOf(reportMapper.sumEyeMinutesBetween(childId, weekStart.toString(), today.toString())));
        vo.setAvgSessionMinutes(intOf(reportMapper.avgSessionMinutesBetween(childId,
                weekStart.toString(), today.toString())));
        vo.setRestCount(intOf(reportMapper.sumRestCountBetween(childId, weekStart.toString(), today.toString())));
        vo.setRestConfirmedCount(intOf(reportMapper.sumRestConfirmedBetween(childId,
                weekStart.toString(), today.toString())));
        return vo;
    }

    private ParentStreakVO buildStreak(String childId, LocalDate today) {
        List<Date> rows = reportMapper.listActiveUsageDates(childId, today.toString());
        Set<LocalDate> activeDays = new HashSet<>();
        if (rows != null) {
            for (Date row : rows) {
                activeDays.add(toLocalDate(row));
            }
        }
        boolean todayCompleted = activeDays.contains(today);
        LocalDate cursor = todayCompleted ? today : today.minusDays(1);
        int days = 0;
        while (activeDays.contains(cursor)) {
            days = days + 1;
            cursor = cursor.minusDays(1);
        }
        ParentStreakVO vo = new ParentStreakVO();
        vo.setDays(days);
        vo.setTodayCompleted(todayCompleted);
        return vo;
    }

    private ParentMasteryVO buildMastery(String childId) {
        Map<Integer, Integer> counter = new LinkedHashMap<>();
        for (Map<String, Object> row : reportMapper.masteryDistribution(childId)) {
            counter.put(intOf(row.get("masteryState")), intOf(row.get("cnt")));
        }
        ParentMasteryVO vo = new ParentMasteryVO();
        vo.setMastered(counter.getOrDefault(MASTERY_MASTERED, 0));
        vo.setFamiliar(counter.getOrDefault(MASTERY_FAMILIAR, 0));
        vo.setLearning(counter.getOrDefault(MASTERY_LEARNING, 0));
        vo.setUnknown(counter.getOrDefault(MASTERY_UNKNOWN, 0));
        return vo;
    }

    private List<ParentErrorCauseVO> buildErrorTop(String childId) {
        return buildCauseList(reportMapper.topErrorCauses(childId));
    }

    private List<ParentErrorCauseVO> buildCauseList(List<Map<String, Object>> rows) {
        List<ParentErrorCauseVO> list = new ArrayList<>();
        if (rows == null) {
            return list;
        }
        for (Map<String, Object> row : rows) {
            String cause = strOf(row.get("cause"));
            list.add(new ParentErrorCauseVO()
                    .setCause(cause)
                    .setCauseName(causeName(cause))
                    .setCount(intOf(row.get("cnt"))));
        }
        return list;
    }

    /**
     * 错因展示名。
     *
     * <p>错题池里存的是错因五维的取值（当前实现写中文名，表注释写的是编码），
     * 这里把两种取值都归一到同一个展示名，家长端只看到「维度名 + 条数」。</p>
     *
     * @param cause 错题池里的原始取值
     * @return 展示名
     */
    private String causeName(String cause) {
        if (cause == null || cause.isEmpty()) {
            return "";
        }
        switch (cause.trim().toUpperCase()) {
            case "CONCEPT":
                return "概念不清";
            case "READING":
                return "审题偏差";
            case "CALC":
                return "计算失误";
            case "METHOD":
                return "方法不会";
            case "TIME":
                return "时间不够";
            default:
                return cause;
        }
    }

    private List<ParentWeakPointVO> buildWeakPoints(String childId) {
        List<Map<String, Object>> rows = reportMapper.weakPoints(childId, WEAK_POINT_LIMIT);
        List<ParentWeakPointVO> list = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return list;
        }
        List<String> pointCodes = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String pointCode = strOf(row.get("pointCode"));
            if (!pointCode.isEmpty()) {
                pointCodes.add(pointCode);
            }
        }
        Map<String, String> names = new LinkedHashMap<>();
        if (!pointCodes.isEmpty()) {
            for (Map<String, Object> row : reportMapper.knowledgePointNames(pointCodes)) {
                names.put(strOf(row.get("pointCode")), strOf(row.get("pointName")));
            }
        }
        for (Map<String, Object> row : rows) {
            String pointCode = strOf(row.get("pointCode"));
            list.add(new ParentWeakPointVO()
                    .setPointCode(pointCode)
                    .setPointName(names.getOrDefault(pointCode, pointCode))
                    .setMasteryState(intOf(row.get("masteryState")))
                    .setErrorCount(intOf(row.get("cnt"))));
        }
        return list;
    }

    private List<ParentWritingTrendVO> buildWritingTrend(String childId, LocalDate today, LocalDate weekStart) {
        List<ParentWritingTrendVO> list = new ArrayList<>();
        for (Map<String, Object> row : reportMapper.listDailyWritingCount(childId,
                weekStart.toString(), today.toString())) {
            LocalDate day = toLocalDate(row.get("day"));
            if (day == null) {
                continue;
            }
            list.add(new ParentWritingTrendVO()
                    .setDate(day.toString())
                    .setWritingCount(intOf(row.get("cnt"))));
        }
        return list;
    }

    // ------------------------------------------------------------------ 工具

    /**
     * 当前日期：统一取数据库的 {@code CURDATE()}，保证与应用侧写入的归属日同一口径。
     */
    private LocalDate currentDate() {
        LocalDate date = toLocalDate(reportMapper.currentDate());
        return date == null ? LocalDate.now() : date;
    }

    private LocalDate toLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate) {
            return (LocalDate) value;
        }
        if (value instanceof java.sql.Date) {
            // java.sql.Date.toInstant() 不支持，必须用 toLocalDate()
            return ((java.sql.Date) value).toLocalDate();
        }
        if (value instanceof java.util.Date) {
            return ((java.util.Date) value).toInstant()
                    .atZone(ZoneId.systemDefault()).toLocalDate();
        }
        return null;
    }

    private int intOf(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String strOf(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
