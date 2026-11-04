package org.jeecg.modules.study.mastery.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.study.mastery.mapper.StudyMasteryMapper;
import org.jeecg.modules.study.mastery.service.StudyMasteryService;
import org.jeecg.modules.study.mastery.vo.MasteryCauseCountVO;
import org.jeecg.modules.study.mastery.vo.MasteryPointVO;
import org.jeecg.modules.study.mastery.vo.MasteryWeakPointVO;
import org.jeecg.modules.study.scope.service.ChildScopeService;

/**
 * 考点掌握度服务实现。
 *
 * <p><b>隔离口径</b>：孩子由 {@code childCode} 解析，解析前先过
 * {@link ChildScopeService#resolveChildId(String)}（关系表求交集），
 * 越权一律 4010（不用 404，避免资源探测）。</p>
 *
 * <p><b>口径说明</b>：全部统计都是<b>这个孩子自己的状态</b>，
 * 不与任何其他孩子、任何其他家长做对比（R-04）。</p>
 *
 * @author jeecg-study
 */
@Service
@RequiredArgsConstructor
public class StudyMasteryServiceImpl implements StudyMasteryService {

    /** 薄弱考点默认条数 */
    public static final int DEFAULT_WEAK_POINT_LIMIT = 10;

    /** 薄弱考点条数上限 */
    public static final int MAX_WEAK_POINT_LIMIT = 50;

    /** 掌握状态：未知（没有任何错题记录） */
    private static final int MASTERY_UNKNOWN = 1;

    private final ChildScopeService scopeService;
    private final StudyMasteryMapper masteryMapper;

    @Override
    public List<MasteryWeakPointVO> weakPoints(String childCode, int limit) {
        String childId = scopeService.resolveChildId(childCode);
        int max = limit <= 0 ? DEFAULT_WEAK_POINT_LIMIT : Math.min(limit, MAX_WEAK_POINT_LIMIT);
        List<Map<String, Object>> rows = masteryMapper.weakPoints(childId, max);
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> pointCodes = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String pointCode = strOf(row.get("pointCode"));
            if (!pointCode.isEmpty() && !pointCodes.contains(pointCode)) {
                pointCodes.add(pointCode);
            }
        }
        Map<String, Map<String, Object>> infos = pointInfos(pointCodes);
        List<MasteryWeakPointVO> list = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String pointCode = strOf(row.get("pointCode"));
            Map<String, Object> info = infos.get(pointCode);
            list.add(new MasteryWeakPointVO()
                    .setPointCode(pointCode)
                    .setPointName(info == null ? pointCode : strOf(info.get("pointName")))
                    .setSubjectCode(info == null ? null : strOf(info.get("subjectCode")))
                    .setMasteryState(intOf(row.get("masteryState")))
                    .setErrorCount(intOf(row.get("errorCount")))
                    .setLastErrorAt(dateOf(row.get("lastErrorAt"))));
        }
        return list;
    }

    @Override
    public MasteryPointVO point(String childCode, String pointCode) {
        String code = pointCode == null ? "" : pointCode.trim();
        if (code.isEmpty()) {
            throw new JeecgBootException("考点业务ID不能为空", 400);
        }
        String childId = scopeService.resolveChildId(childCode);
        Map<String, Object> stat = masteryMapper.pointStat(childId, code);
        Map<String, Map<String, Object>> infos = pointInfos(Collections.singletonList(code));
        Map<String, Object> info = infos.get(code);

        MasteryPointVO vo = new MasteryPointVO();
        vo.setPointCode(code);
        vo.setPointName(info == null ? code : strOf(info.get("pointName")));
        vo.setSubjectCode(info == null ? null : strOf(info.get("subjectCode")));
        vo.setMasteryState(stat == null ? MASTERY_UNKNOWN : intOf(stat.get("masteryState")));
        vo.setErrorCount(stat == null ? 0 : intOf(stat.get("errorCount")));
        vo.setLastErrorAt(stat == null ? null : dateOf(stat.get("lastErrorAt")));
        vo.setCauseCounts(buildCauseCounts(childId, code));
        return vo;
    }

    // ------------------------------------------------------------------ 内部

    private List<MasteryCauseCountVO> buildCauseCounts(String childId, String pointCode) {
        List<MasteryCauseCountVO> list = new ArrayList<>();
        List<Map<String, Object>> rows = masteryMapper.causeCounts(childId, pointCode);
        if (rows == null) {
            return list;
        }
        for (Map<String, Object> row : rows) {
            String cause = strOf(row.get("cause"));
            list.add(new MasteryCauseCountVO()
                    .setCause(cause)
                    .setCauseName(causeName(cause))
                    .setCount(intOf(row.get("cnt"))));
        }
        return list;
    }

    private Map<String, Map<String, Object>> pointInfos(List<String> pointCodes) {
        Map<String, Map<String, Object>> map = new LinkedHashMap<>();
        if (pointCodes == null || pointCodes.isEmpty()) {
            return map;
        }
        List<Map<String, Object>> rows = masteryMapper.pointInfos(pointCodes);
        if (rows == null) {
            return map;
        }
        for (Map<String, Object> row : rows) {
            map.put(strOf(row.get("pointCode")), row);
        }
        return map;
    }

    /**
     * 错因展示名。
     *
     * <p>错题池里可能存中文名（当前实现口径），也可能存英文码（设计口径），
     * 这里把两种取值都归一到同一个展示名。</p>
     */
    private String causeName(String cause) {
        if (cause == null || cause.trim().isEmpty()) {
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

    private java.util.Date dateOf(Object value) {
        if (value instanceof java.util.Date) {
            return (java.util.Date) value;
        }
        return null;
    }
}
