package org.jeecg.modules.study.practice.service.impl;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.study.common.record.StudyRecordTableGuard;
import org.jeecg.modules.study.practice.entity.StudyPracticeRecord;
import org.jeecg.modules.study.practice.mapper.StudyPracticeRecordMapper;
import org.jeecg.modules.study.practice.service.StudyPracticeStatsService;
import org.jeecg.modules.study.practice.vo.PracticeRecordPageVO;
import org.jeecg.modules.study.practice.vo.PracticeRecordVO;
import org.jeecg.modules.study.practice.vo.PracticeStreakVO;
import org.jeecg.modules.study.practice.vo.PracticeTodayStatsVO;
import org.jeecg.modules.study.scope.service.ChildScopeService;

/**
 * 作答记录只读统计服务实现。
 *
 * <p><b>分表口径</b>：作答记录按月分表（{@code study_practice_record_yyyyMM}），
 * 表名一律经 {@link StudyRecordTableGuard} 拿：当月表走 {@code currentTableName()}
 * （顺带兜底建表），历史月表走 {@code tableNameOf(YearMonth)}（只拼名字）。
 * 历史月表可能还没建（月度任务没跑），此时<b>不抛异常</b>，只当这张表没有数据 ——
 * 缺表是运维的事，不该让家长端报表整个挂掉。</p>
 *
 * <p><b>隔离口径</b>：孩子由 {@code childCode} 解析，解析前先过
 * {@link ChildScopeService#resolveChildId(String)}（关系表求交集），
 * 越权一律 4010。</p>
 *
 * @author jeecg-study
 */
@Service
@RequiredArgsConstructor
public class StudyPracticeStatsServiceImpl implements StudyPracticeStatsService {

    private static final Logger log = LoggerFactory.getLogger(StudyPracticeStatsServiceImpl.class);

    /** 默认页码 */
    private static final int DEFAULT_PAGE_NO = 1;

    /** 默认每页条数（《接口设计说明书》§1.3） */
    private static final int DEFAULT_PAGE_SIZE = 10;

    /** 每页条数上限 */
    private static final int MAX_PAGE_SIZE = 100;

    /** 算连续天数时回看几个月（当月 + 前两个月） */
    private static final int STREAK_LOOKBACK_MONTHS = 3;

    private final ChildScopeService scopeService;
    private final StudyPracticeRecordMapper recordMapper;
    private final StudyRecordTableGuard recordTableGuard;

    @Override
    public PracticeRecordPageVO records(String childCode, Integer pageNo, Integer pageSize, String month) {
        String childId = scopeService.resolveChildId(childCode);
        int page = pageNo == null || pageNo < 1 ? DEFAULT_PAGE_NO : pageNo;
        int size = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        String table = resolveTable(month);

        PracticeRecordPageVO vo = new PracticeRecordPageVO();
        vo.setPageNo(page);
        vo.setPageSize(size);
        vo.setMonth(monthKey(table));

        Long total = safeCount(table, childId);
        vo.setTotal(total == null ? 0L : total);
        vo.setPages((int) ((total == null ? 0L : total) + size - 1) / size);

        int offset = (page - 1) * size;
        List<StudyPracticeRecord> rows = safePage(table, childId, offset, size);
        List<PracticeRecordVO> records = new ArrayList<>();
        if (rows != null) {
            for (StudyPracticeRecord row : rows) {
                records.add(toVO(row));
            }
        }
        vo.setRecords(records);
        return vo;
    }

    @Override
    public PracticeTodayStatsVO todayStats(String childCode) {
        String childId = scopeService.resolveChildId(childCode);
        String table = recordTableGuard.currentTableName();
        String today = LocalDate.now().toString();

        Map<String, Object> row = safeSummary(table, childId, today);
        int total = intOf(row == null ? null : row.get("totalCount"));
        int correct = intOf(row == null ? null : row.get("correctCount"));

        PracticeTodayStatsVO vo = new PracticeTodayStatsVO();
        vo.setDate(today);
        vo.setTotalCount(total);
        vo.setCorrectCount(correct);
        vo.setWrongCount(Math.max(total - correct, 0));
        vo.setDurationMinutes((int) Math.round(intOf(row == null ? null : row.get("durationMs")) / 60000.0));
        vo.setModuleCount(intOf(row == null ? null : row.get("moduleCount")));
        return vo;
    }

    @Override
    public PracticeStreakVO streak(String childCode) {
        String childId = scopeService.resolveChildId(childCode);
        LocalDate today = LocalDate.now();

        Set<LocalDate> activeDays = new LinkedHashSet<>();
        YearMonth current = YearMonth.of(today.getYear(), today.getMonthValue());
        for (int i = 0; i < STREAK_LOOKBACK_MONTHS; i++) {
            String table = recordTableGuard.tableNameOf(current.minusMonths(i));
            for (java.sql.Date date : safePracticeDates(table, childId, today.toString())) {
                LocalDate day = toLocalDate(date);
                if (day != null) {
                    activeDays.add(day);
                }
            }
        }

        boolean todayCompleted = activeDays.contains(today);
        LocalDate cursor = todayCompleted ? today : today.minusDays(1);
        int days = 0;
        while (activeDays.contains(cursor)) {
            days = days + 1;
            cursor = cursor.minusDays(1);
        }

        PracticeStreakVO vo = new PracticeStreakVO();
        vo.setDays(days);
        vo.setTodayCompleted(todayCompleted);
        return vo;
    }

    // ------------------------------------------------------------------ 内部

    /**
     * 解析作答记录月表名。
     *
     * <p>不传月份 → 当月表（顺带兜底建表）；传了月份 → 只拼名字，
     * 表不存在时后续查询会返回空（见 {@code safeXxx}）。</p>
     */
    private String resolveTable(String month) {
        String key = month == null ? "" : month.trim();
        if (key.isEmpty()) {
            return recordTableGuard.currentTableName();
        }
        if (!key.matches("\\d{6}")) {
            throw new JeecgBootException("月份格式应为 yyyyMM", 400);
        }
        int year = Integer.parseInt(key.substring(0, 4));
        int monthValue = Integer.parseInt(key.substring(4, 6));
        if (monthValue < 1 || monthValue > 12) {
            throw new JeecgBootException("月份格式应为 yyyyMM", 400);
        }
        return recordTableGuard.tableNameOf(YearMonth.of(year, monthValue));
    }

    private String monthKey(String table) {
        if (table == null || table.length() < 6) {
            return null;
        }
        return table.substring(table.length() - 6);
    }

    private Long safeCount(String table, String childId) {
        try {
            return recordMapper.countByChild(table, childId);
        } catch (Exception e) {
            log.warn("[StudyPracticeStatsService] 月表 {} 不可用（可能尚未建表）：{}", table, e.getMessage());
            return 0L;
        }
    }

    private List<StudyPracticeRecord> safePage(String table, String childId, int offset, int limit) {
        try {
            return recordMapper.pageByChild(table, childId, offset, limit);
        } catch (Exception e) {
            log.warn("[StudyPracticeStatsService] 月表 {} 不可用（可能尚未建表）：{}", table, e.getMessage());
            return new ArrayList<>();
        }
    }

    private Map<String, Object> safeSummary(String table, String childId, String date) {
        try {
            return recordMapper.summarizeByDate(table, childId, date);
        } catch (Exception e) {
            log.warn("[StudyPracticeStatsService] 月表 {} 不可用（可能尚未建表）：{}", table, e.getMessage());
            return null;
        }
    }

    private List<java.sql.Date> safePracticeDates(String table, String childId, String to) {
        try {
            return recordMapper.listPracticeDates(table, childId, to);
        } catch (Exception e) {
            log.warn("[StudyPracticeStatsService] 月表 {} 不可用（可能尚未建表）：{}", table, e.getMessage());
            return new ArrayList<>();
        }
    }

    private PracticeRecordVO toVO(StudyPracticeRecord row) {
        return new PracticeRecordVO()
                .setContentId(row.getContentId())
                .setContentType(row.getContentType())
                .setSourceModule(row.getSourceModule())
                .setKnowledgePointCode(row.getKnowledgePointCode())
                .setCorrect(row.getCorrect())
                .setErrorCause(row.getErrorCause())
                .setErrorCode(row.getErrorCode())
                .setDurationMs(row.getDurationMs())
                .setPracticeDate(row.getPracticeDate())
                .setCreateTime(row.getCreateTime());
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof java.sql.Date) {
            return ((java.sql.Date) value).toLocalDate();
        }
        if (value instanceof java.util.Date) {
            return ((java.util.Date) value).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        if (value instanceof LocalDate) {
            return (LocalDate) value;
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
}
