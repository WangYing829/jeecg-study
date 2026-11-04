package org.jeecg.modules.study.error.service.impl;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;

import lombok.RequiredArgsConstructor;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.study.common.constant.StudyErrorCode;
import org.jeecg.modules.study.common.enums.JudgeSource;
import org.jeecg.modules.study.common.exception.StudyForbiddenException;
import org.jeecg.modules.study.error.constant.ErrorCause;
import org.jeecg.modules.study.error.constant.ReviewSchedulePolicy;
import org.jeecg.modules.study.error.entity.StudyErrorPool;
import org.jeecg.modules.study.error.entity.StudyReviewSchedule;
import org.jeecg.modules.study.error.mapper.StudyErrorContextMapper;
import org.jeecg.modules.study.error.mapper.StudyErrorPoolMapper;
import org.jeecg.modules.study.error.mapper.StudyErrorQuestionMapper;
import org.jeecg.modules.study.error.mapper.StudyErrorQuestionVariantMapper;
import org.jeecg.modules.study.error.mapper.StudyReviewScheduleMapper;
import org.jeecg.modules.study.error.service.StudyErrorManageService;
import org.jeecg.modules.study.error.service.StudyErrorPoolService;
import org.jeecg.modules.study.error.vo.ErrorDetailVO;
import org.jeecg.modules.study.error.vo.ErrorHotStatisticVO;
import org.jeecg.modules.study.error.vo.ErrorPoolItemVO;
import org.jeecg.modules.study.error.vo.ErrorPoolReportVO;
import org.jeecg.modules.study.error.vo.ErrorVariantListVO;
import org.jeecg.modules.study.error.vo.ErrorVariantResult;
import org.jeecg.modules.study.error.vo.ErrorVariantVO;
import org.jeecg.modules.study.error.vo.ReviewCompleteCommand;
import org.jeecg.modules.study.error.vo.ReviewCompleteVO;
import org.jeecg.modules.study.error.vo.ReviewQueueItemVO;
import org.jeecg.modules.study.error.vo.StudyErrorReportCommand;
import org.jeecg.modules.study.idseq.service.StudyIdSequenceService;
import org.jeecg.modules.study.scope.service.ChildScopeService;

/**
 * 统一错题池 + 复习调度引擎实现（T-V0-06 / T-V0-07）。
 *
 * <p><b>全局只有一个错题池</b>：六个模块的错误都汇聚到这里，
 * 用 {@code source_module} 区分来源，用 {@code child_id} 隔离孩子。</p>
 *
 * <p><b>排期阶梯</b>（服务端计算，前端不参与）：答错 → 当天；答对 → +1 / +3 / +7 / +14 天；
 * 连续答对 2 次移出队列。变式未全对<b>不计入做对</b>。</p>
 *
 * <p><b>并发与幂等</b>：</p>
 * <ul>
 *   <li>入池幂等键 = 孩子 + 内容类型 + 内容；</li>
 *   <li>复习完成幂等键 = 错题 + 日期（当天排期行 CAS 占用，只会被推进一次）；</li>
 *   <li>连续答对次数用 CAS 更新，避免并发脏写（TC-08-08）。</li>
 * </ul>
 *
 * @author jeecg-study
 */
// update-begin---author:mavis ---date:2026-11-04  for：【第二档-6 上线学习端错题线】错题入池的 childId 允许缺省 —— 缺省时由服务端会话推导（孩子取自己），前端不再必须先查 childCode 再回传-----------
@Service
@RequiredArgsConstructor
public class StudyErrorPoolServiceImpl implements StudyErrorPoolService, StudyErrorManageService {

    /** 版权类型：自编 */
    private static final String COPYRIGHT_SELF = "SELF";

    /** 降级类型：母题变式 */
    private static final String DEGRADE_MOTHER = "MOTHER";

    /** 降级类型：同知识点同题型 */
    private static final String DEGRADE_SAME_POINT = "SAME_POINT";

    /** 降级类型：暂无可用变式 */
    private static final String DEGRADE_NONE = "NONE";

    private final StudyErrorPoolMapper errorPoolMapper;
    private final StudyReviewScheduleMapper scheduleMapper;
    private final StudyErrorQuestionVariantMapper variantMapper;
    private final StudyErrorQuestionMapper questionMapper;
    private final StudyErrorContextMapper contextMapper;
    private final ChildScopeService scopeService;
    private final StudyIdSequenceService idSequence;

    // ------------------------------------------------------------------ 错题池

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String report(StudyErrorReportCommand command) {
        return doReport(command).getErrorCode();
    }

    /**
     * 错题入池并返回完整结果（Controller 用）。
     *
     * @param command 入池入参
     * @return 入池结果
     */
    @Transactional(rollbackFor = Exception.class)
    public ErrorPoolReportVO reportToPool(StudyErrorReportCommand command) {
        return doReport(command);
    }

    @Override
    public List<ErrorPoolItemVO> listByChild(String childId) {
        String id = trim(childId);
        if (id.isEmpty()) {
            throw new StudyForbiddenException();
        }
        scopeService.assertChildAccess(id);
        List<StudyErrorPool> rows = errorPoolMapper.selectByChildId(id);
        List<ErrorPoolItemVO> list = new ArrayList<>();
        if (rows == null) {
            return list;
        }
        for (StudyErrorPool error : rows) {
            list.add(toItemVO(error));
        }
        return list;
    }

    @Override
    public List<ReviewQueueItemVO> todayQueue(String childId) {
        String id = trim(childId);
        if (id.isEmpty()) {
            throw new StudyForbiddenException();
        }
        scopeService.assertChildAccess(id);
        List<ReviewQueueItemVO> queue = scheduleMapper.selectTodayQueue(id, toDate(LocalDate.now()));
        return queue == null ? Collections.emptyList() : queue;
    }

    @Override
    public Map<String, Integer> errorCauseStatistics(String childId) {
        String id = trim(childId);
        if (id.isEmpty()) {
            throw new StudyForbiddenException();
        }
        scopeService.assertChildAccess(id);
        Map<String, Integer> statistics = new LinkedHashMap<>();
        for (String cause : ErrorCause.ALL) {
            statistics.put(cause, 0);
        }
        List<Map<String, Object>> rows = errorPoolMapper.countGroupByCause(id);
        if (rows == null) {
            return statistics;
        }
        for (Map<String, Object> row : rows) {
            String cause = stringValue(row, "errorCause");
            Object cnt = row.get("cnt");
            if (cause == null || cnt == null) {
                continue;
            }
            statistics.put(cause, ((Number) cnt).intValue());
        }
        return statistics;
    }

    // ------------------------------------------------------------------ 复习

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeReview(String errorCode, boolean correct, String errorCause) {
        ReviewCompleteCommand command = new ReviewCompleteCommand();
        command.setErrorCode(errorCode);
        command.setCorrect(correct);
        command.setErrorCause(errorCause);
        advanceReview(command);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewCompleteVO completeReview(ReviewCompleteCommand command) {
        return advanceReview(command);
    }

    @Override
    public List<ReviewQueueItemVO> upcoming(String childId, int days) {
        String id = trim(childId);
        if (id.isEmpty()) {
            throw new StudyForbiddenException();
        }
        scopeService.assertChildAccess(id);
        int span = days <= 0 ? 7 : days;
        LocalDate today = LocalDate.now();
        List<ReviewQueueItemVO> list = scheduleMapper.selectUpcoming(id, toDate(today), toDate(today.plusDays(span - 1)));
        return list == null ? Collections.emptyList() : list;
    }

    // ------------------------------------------------------------------ 变式 / 错因

    @Override
    public ErrorDetailVO detail(String errorCode) {
        StudyErrorPool error = requireError(errorCode);
        scopeService.assertChildAccess(error.getChildId());

        ErrorDetailVO vo = new ErrorDetailVO();
        vo.setErrorCode(error.getErrorCode());
        vo.setSourceModule(error.getSourceModule());
        vo.setContentType(error.getContentType());
        vo.setContentId(error.getContentId());
        vo.setKnowledgePointCode(error.getKnowledgePointCode());
        vo.setKnowledgePointName(pointName(error.getKnowledgePointCode()));
        vo.setSubjectCode(error.getSubjectCode());
        vo.setErrorCause(error.getErrorCause());
        vo.setErrorCauseName(causeName(error.getErrorCause()));
        vo.setJudgeSource(error.getJudgeSource());
        vo.setMasteryState(error.getMasteryState());
        vo.setCorrectStreak(error.getCorrectStreak());
        vo.setReviewStatus(error.getReviewStatus());
        vo.setNextReviewAt(error.getNextReviewAt());
        vo.setLastReviewAt(error.getLastReviewAt());
        vo.setArchived(error.getArchivedAt() != null);
        vo.setCreateTime(error.getCreateTime());
        vo.setCopyrightType(error.getCopyrightType());
        vo.setSourceRef(error.getSourceRef());
        return vo;
    }

    @Override
    public List<ErrorHotStatisticVO> hotStatistics(String childId, int limit) {
        String id = trim(childId);
        if (id.isEmpty()) {
            throw new StudyForbiddenException();
        }
        scopeService.assertChildAccess(id);
        int max = limit <= 0 ? 10 : Math.min(limit, 50);

        List<Map<String, Object>> rows = errorPoolMapper.countGroupByContent(id, max);
        List<ErrorHotStatisticVO> list = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return list;
        }
        List<String> pointCodes = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String pointCode = stringValue(row, "knowledgePointCode");
            if (pointCode != null && !pointCode.isEmpty() && !pointCodes.contains(pointCode)) {
                pointCodes.add(pointCode);
            }
        }
        Map<String, String> names = pointNames(pointCodes);
        for (Map<String, Object> row : rows) {
            String pointCode = stringValue(row, "knowledgePointCode");
            list.add(new ErrorHotStatisticVO()
                    .setErrorCode(stringValue(row, "errorCode"))
                    .setSourceModule(stringValue(row, "sourceModule"))
                    .setContentType(stringValue(row, "contentType"))
                    .setContentId(stringValue(row, "contentId"))
                    .setKnowledgePointCode(pointCode)
                    .setKnowledgePointName(pointCode == null ? null : names.getOrDefault(pointCode, pointCode))
                    .setErrorCount(intOf(row.get("errorCount")))
                    .setMasteryState(intOf(row.get("masteryState")))
                    .setLastErrorAt(dateOf(row.get("lastErrorAt"))));
        }
        return list;
    }

    @Override
    public ErrorVariantListVO listVariants(String errorCode) {
        StudyErrorPool error = requireError(errorCode);
        scopeService.assertChildAccess(error.getChildId());

        VariantResolution resolution = resolveVariants(error);
        ErrorVariantListVO vo = new ErrorVariantListVO();
        vo.setErrorCode(error.getErrorCode());
        vo.setMotherCode(resolution.motherCode);
        vo.setVariants(resolution.variants);
        vo.setVariantAvailable(!resolution.variants.isEmpty());
        vo.setDegradeType(resolution.degradeType);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateErrorCause(String errorCode, String errorCause) {
        StudyErrorPool error = requireError(errorCode);
        scopeService.assertChildAccess(error.getChildId());
        String cause = trim(errorCause);
        if (!ErrorCause.isValid(cause)) {
            throw new JeecgBootException("错因必须是五维之一", StudyErrorCode.MISSING_ERROR_CAUSE);
        }
        error.setErrorCause(cause);
        errorPoolMapper.updateById(error);
    }

    // ------------------------------------------------------------------ 内部：入池

    private ErrorPoolReportVO doReport(StudyErrorReportCommand command) {
        if (command == null) {
            throw new JeecgBootException("错题入池参数不能为空", 400);
        }
        String childId = trim(command.getChildId());
        if (childId.isEmpty()) {
            // 孩子身份：childId 由服务端会话推导（契约 §3.2，不接受前端传入）；
            // 家长身份必须显式指定孩子，推不出则一律 4010。
            childId = trim(scopeService.ownChildId());
        }
        if (childId.isEmpty()) {
            throw new StudyForbiddenException();
        }
        // 数据范围：家长只能给自己关联的孩子入池，孩子只能给自己入池
        scopeService.assertChildAccess(childId);

        // 错因必须先选（TC-02-04 / TC-11-03 → 4001）
        String errorCause = trim(command.getErrorCause());
        if (!ErrorCause.isValid(errorCause)) {
            throw new JeecgBootException("请先选择错因", StudyErrorCode.MISSING_ERROR_CAUSE);
        }

        // 判定来源只能是 rule / rule+model（R02-2 / R02-4）
        String judgeSource = trim(command.getJudgeSource());
        if (judgeSource.isEmpty()) {
            judgeSource = JudgeSource.RULE.getCode();
        }
        if (!JudgeSource.isValid(judgeSource)) {
            throw new JeecgBootException("判定来源只能是 rule 或 rule+model", 400);
        }

        // 非自编来源必须填出处（TC-02-03 → 4006）
        String copyrightType = trim(command.getCopyrightType());
        if (copyrightType.isEmpty()) {
            copyrightType = COPYRIGHT_SELF;
        }
        String sourceRef = trim(command.getSourceRef());
        if (!COPYRIGHT_SELF.equalsIgnoreCase(copyrightType) && sourceRef.isEmpty()) {
            throw new JeecgBootException("非自编内容必须填写出处", StudyErrorCode.COPYRIGHT_INCOMPLETE);
        }

        String contentType = trim(command.getContentType());
        String contentId = trim(command.getContentId());
        if (contentType.isEmpty() || contentId.isEmpty()) {
            throw new JeecgBootException("内容类型与内容ID不能为空", 400);
        }

        // 幂等：同一孩子 + 内容类型 + 内容只入一次
        StudyErrorPool existed = errorPoolMapper.selectByChildAndContent(childId, contentType, contentId);
        if (existed != null) {
            ErrorPoolReportVO vo = new ErrorPoolReportVO();
            vo.setErrorCode(existed.getErrorCode());
            vo.setNextReviewAt(existed.getNextReviewAt());
            vo.setDuplicated(Boolean.TRUE);
            return vo;
        }

        String errorCode = idSequence.next("ERR");
        LocalDate today = LocalDate.now();

        StudyErrorPool error = new StudyErrorPool();
        error.setId(IdWorker.get32UUID());
        error.setChildId(childId);
        error.setErrorCode(errorCode);
        error.setContextId(resolveContextId(childId));
        error.setSourceModule(trim(command.getSourceModule()));
        error.setContentType(contentType);
        error.setContentId(contentId);
        error.setKnowledgePointCode(trim(command.getKnowledgePointCode()));
        error.setSubjectCode(trim(command.getSubjectCode()));
        error.setErrorCause(errorCause);
        error.setRawAnswer(command.getRawAnswer());
        error.setCorrectAnswer(command.getCorrectAnswer());
        error.setJudgeSource(judgeSource);
        error.setMasteryState(ReviewSchedulePolicy.MASTERY_LEARNING);
        error.setCorrectStreak(0);
        error.setReviewStatus(ReviewSchedulePolicy.REVIEW_PENDING);
        error.setNextReviewAt(toDate(today));
        error.setUpgradeFlag(0);
        error.setSourceRef(sourceRef.isEmpty() ? null : sourceRef);
        error.setCopyrightType(copyrightType.toUpperCase());
        error.setDelFlag(0);
        if (errorPoolMapper.insert(error) != 1) {
            throw new JeecgBootException("错题入池失败", 500);
        }

        // 入池当天进队列（TC-02-01：下次复习 = 今天）
        insertSchedule(error, today, ReviewSchedulePolicy.baseSortScore(0));

        ErrorPoolReportVO vo = new ErrorPoolReportVO();
        vo.setErrorCode(errorCode);
        vo.setNextReviewAt(toDate(today));
        vo.setDuplicated(Boolean.FALSE);
        return vo;
    }

    // ------------------------------------------------------------------ 内部：复习推进

    private ReviewCompleteVO advanceReview(ReviewCompleteCommand command) {
        if (command == null || trim(command.getErrorCode()).isEmpty()) {
            throw new JeecgBootException("错题业务ID不能为空", 400);
        }
        String errorCode = trim(command.getErrorCode());
        StudyErrorPool error = errorPoolMapper.selectByErrorCode(errorCode);
        if (error == null) {
            throw new JeecgBootException("错题不存在：" + errorCode, 400);
        }
        scopeService.assertChildAccess(error.getChildId());

        boolean correct = Boolean.TRUE.equals(command.getCorrect());
        LocalDate reviewDate = command.getReviewDate() == null ? LocalDate.now() : toLocalDate(command.getReviewDate());

        // 变式未全对 → 本次不计入「做对」（TC-02-14）
        VariantResolution resolution = resolveVariants(error);
        if (correct && !variantsAllCorrect(command, resolution.variants)) {
            return buildCompleteVO(error, Boolean.FALSE, "变式没有全对，这次还不算做对");
        }

        // 答错必须带错因（先校验，避免白占排期行）
        String cause = correct ? error.getErrorCause() : trim(command.getErrorCause());
        if (!correct && !ErrorCause.isValid(cause)) {
            throw new JeecgBootException("答错必须选择错因", StudyErrorCode.MISSING_ERROR_CAUSE);
        }

        // 同日幂等：当天已经复习过就不再推进（TC-08-07）
        StudyReviewSchedule todaySchedule = scheduleMapper.selectByErrorCodeAndDate(errorCode, toDate(reviewDate));
        if (todaySchedule != null && todaySchedule.getStatus() != null
                && todaySchedule.getStatus() == ReviewSchedulePolicy.SCHEDULE_COMPLETED) {
            return buildCompleteVO(error, Boolean.FALSE, "今天已经复习过这道题了");
        }

        // 并发保护：答对时先 CAS 占用当天的排期行，只有一个会话能推进（TC-08-08）
        // 答错不占用排期行：错题要重排回当天，必须还能留在队列里（TC-02-10）
        if (todaySchedule != null && correct) {
            int claimed = scheduleMapper.markCompleted(todaySchedule.getId(), new Date());
            if (claimed == 0) {
                return buildCompleteVO(error, Boolean.FALSE, "这道题刚刚已经复习过了");
            }
        }

        int oldStreak = error.getCorrectStreak() == null ? 0 : error.getCorrectStreak();
        int newStreak;
        LocalDate nextReview;
        int masteryState;
        boolean archived = false;
        Date archivedAt = null;

        if (!correct) {
            newStreak = 0;
            nextReview = reviewDate;
            masteryState = ReviewSchedulePolicy.MASTERY_LEARNING;
        } else {
            newStreak = oldStreak + 1;
            nextReview = reviewDate.plusDays(ReviewSchedulePolicy.nextIntervalDays(newStreak));
            masteryState = newStreak >= ReviewSchedulePolicy.ARCHIVE_STREAK
                    ? ReviewSchedulePolicy.MASTERY_MASTERED : ReviewSchedulePolicy.MASTERY_FAMILIAR;
            archived = ReviewSchedulePolicy.shouldArchive(newStreak);
            if (archived) {
                archivedAt = new Date();
            }
        }

        int reviewStatus = archived ? ReviewSchedulePolicy.REVIEW_ARCHIVED : ReviewSchedulePolicy.REVIEW_PENDING;
        int updated = errorPoolMapper.updateReviewProgress(error.getId(), oldStreak, newStreak,
                toDate(nextReview), masteryState, reviewStatus, archivedAt, new Date(), cause);
        if (updated == 0) {
            // 并发：别人已经推进过，本次不重复累加（TC-08-08）
            StudyErrorPool latest = errorPoolMapper.selectByErrorCode(errorCode);
            return buildCompleteVO(latest == null ? error : latest, Boolean.FALSE, "这道题刚刚已经复习过了");
        }

        // 生成下一次排期（归档后不再进队列）
        if (!archived) {
            insertScheduleIfAbsent(error, nextReview, ReviewSchedulePolicy.baseSortScore(newStreak));
        }

        error.setCorrectStreak(newStreak);
        error.setNextReviewAt(toDate(nextReview));
        error.setMasteryState(masteryState);
        error.setReviewStatus(reviewStatus);
        error.setArchivedAt(archivedAt);
        return buildCompleteVO(error, Boolean.TRUE, archived
                ? "连续两次做对，这道题已移出复习队列"
                : (correct ? "做对了，下次复习日期已更新" : "下次复习重排到今天"));
    }

    // ------------------------------------------------------------------ 内部：变式

    /**
     * 变式解析：有母题走母题变式；无母题降级为「同知识点同题型的换数字变式」；
     * 都没有则标记「暂无可用变式」（错题仍留在队列里）。
     */
    private VariantResolution resolveVariants(StudyErrorPool error) {
        String motherCode = null;
        if (error.getContentId() != null && !error.getContentId().trim().isEmpty()) {
            motherCode = questionMapper.selectMotherCodeByQuestionCode(error.getContentId());
        }
        if (motherCode == null && error.getKnowledgePointCode() != null
                && !error.getKnowledgePointCode().trim().isEmpty()) {
            motherCode = questionMapper.selectMotherCodeByPointCode(error.getKnowledgePointCode());
        }
        if (motherCode != null && !motherCode.trim().isEmpty()) {
            List<ErrorVariantVO> variants = variantMapper.selectByMotherCode(motherCode, error.getChildId());
            if (variants != null && !variants.isEmpty()) {
                return new VariantResolution(motherCode, DEGRADE_MOTHER, withLayerNames(variants));
            }
        }

        String pointCode = error.getKnowledgePointCode();
        if (pointCode != null && !pointCode.trim().isEmpty() && error.getContentId() != null) {
            String questionType = questionMapper.selectQuestionType(error.getContentId());
            if (questionType != null && !questionType.trim().isEmpty()) {
                List<ErrorVariantVO> degraded = questionMapper.selectDegradedVariants(
                        pointCode, questionType, error.getContentId());
                if (degraded == null || degraded.isEmpty()) {
                    degraded = questionMapper.selectSamePointVariants(pointCode, questionType, error.getContentId());
                }
                if (degraded != null && !degraded.isEmpty()) {
                    return new VariantResolution(motherCode, DEGRADE_SAME_POINT, withLayerNames(degraded));
                }
            }
        }
        return new VariantResolution(motherCode, DEGRADE_NONE, Collections.emptyList());
    }

    /**
     * 补齐变式的层级名称。
     *
     * <p>变式表的 {@code variant_layer} 只存层级序号（1换数字 / 2换情境 / 3逆向），
     * 层级名称由 {@link ReviewSchedulePolicy#layerName(Integer)} 统一给出，
     * 这里在 Service 层补齐，避免把中文文案写死进 SQL。</p>
     *
     * @param variants 变式清单
     * @return 原清单（就地补齐后返回）
     */
    private List<ErrorVariantVO> withLayerNames(List<ErrorVariantVO> variants) {
        if (variants == null || variants.isEmpty()) {
            return variants;
        }
        for (ErrorVariantVO variant : variants) {
            if (variant != null && (variant.getLayerName() == null || variant.getLayerName().trim().isEmpty())) {
                variant.setLayerName(ReviewSchedulePolicy.layerName(variant.getLayer()));
            }
        }
        return variants;
    }

    private boolean variantsAllCorrect(ReviewCompleteCommand command, List<ErrorVariantVO> variants) {
        if (variants == null || variants.isEmpty()) {
            return true;
        }
        List<ErrorVariantResult> results = command.getVariantResults();
        if (results == null || results.isEmpty()) {
            return false;
        }
        for (ErrorVariantResult result : results) {
            if (result == null || !Boolean.TRUE.equals(result.getCorrect())) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ 内部：排期

    private void insertSchedule(StudyErrorPool error, LocalDate date, java.math.BigDecimal sortScore) {
        StudyReviewSchedule schedule = new StudyReviewSchedule();
        schedule.setId(IdWorker.get32UUID());
        schedule.setChildId(error.getChildId());
        schedule.setErrorCode(error.getErrorCode());
        schedule.setContextId(error.getContextId());
        schedule.setSourceModule(error.getSourceModule());
        schedule.setScheduleDate(toDate(date));
        schedule.setSortScore(sortScore);
        schedule.setQueueDate(toDate(date));
        schedule.setSeqNo(nextSeqNo(error.getChildId()));
        schedule.setStatus(ReviewSchedulePolicy.SCHEDULE_PENDING);
        schedule.setDelFlag(0);
        int rows = scheduleMapper.insert(schedule);
        if (rows != 1) {
            // 排期写入失败必须让整个事务回滚——错题记录不允许出现半写状态（TC-08-09 / TC-11-16）
            throw new JeecgBootException("复习排期写入失败", 500);
        }
    }

    private void insertScheduleIfAbsent(StudyErrorPool error, LocalDate date, java.math.BigDecimal sortScore) {
        StudyReviewSchedule existed = scheduleMapper.selectByErrorCodeAndDate(error.getErrorCode(), toDate(date));
        if (existed != null) {
            return;
        }
        insertSchedule(error, date, sortScore);
    }

    private int nextSeqNo(String childId) {
        Long count = scheduleMapper.selectCount(
                new QueryWrapper<StudyReviewSchedule>().eq("child_id", childId).eq("del_flag", 0));
        return count == null ? 0 : count.intValue();
    }

    // ------------------------------------------------------------------ 内部：工具

    private StudyErrorPool requireError(String errorCode) {
        String code = trim(errorCode);
        if (code.isEmpty()) {
            throw new JeecgBootException("错题业务ID不能为空", 400);
        }
        StudyErrorPool error = errorPoolMapper.selectByErrorCode(code);
        if (error == null) {
            throw new JeecgBootException("错题不存在：" + code, 400);
        }
        return error;
    }

    /**
     * 解析孩子当前上下文。
     *
     * <p>V0 阶段上下文域尚未落地：查到上下文就用上下文 ID，
     * 查不到则退化为孩子 ID，保证 {@code context_id} 始终可追溯到「是谁的错题」。</p>
     */
    private String resolveContextId(String childId) {
        String contextId = contextMapper.selectCurrentContextId(childId);
        return contextId == null || contextId.trim().isEmpty() ? childId : contextId;
    }

    private ErrorPoolItemVO toItemVO(StudyErrorPool error) {
        ErrorPoolItemVO vo = new ErrorPoolItemVO();
        vo.setErrorCode(error.getErrorCode());
        vo.setSourceModule(error.getSourceModule());
        vo.setContentType(error.getContentType());
        vo.setContentId(error.getContentId());
        vo.setKnowledgePointCode(error.getKnowledgePointCode());
        vo.setSubjectCode(error.getSubjectCode());
        vo.setErrorCause(error.getErrorCause());
        vo.setJudgeSource(error.getJudgeSource());
        vo.setMasteryState(error.getMasteryState());
        vo.setCorrectStreak(error.getCorrectStreak());
        vo.setReviewStatus(error.getReviewStatus());
        vo.setNextReviewAt(error.getNextReviewAt());
        vo.setArchived(error.getArchivedAt() != null);
        return vo;
    }

    private ReviewCompleteVO buildCompleteVO(StudyErrorPool error, Boolean counted, String message) {
        ReviewCompleteVO vo = new ReviewCompleteVO();
        vo.setErrorCode(error.getErrorCode());
        vo.setCorrectStreak(error.getCorrectStreak());
        vo.setMasteryState(error.getMasteryState());
        vo.setArchived(error.getArchivedAt() != null);
        vo.setNextReviewAt(error.getNextReviewAt());
        vo.setCounted(counted);
        vo.setMessage(message);
        return vo;
    }

    private static String stringValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (key.equalsIgnoreCase(entry.getKey())) {
                    value = entry.getValue();
                    break;
                }
            }
        }
        return value == null ? null : String.valueOf(value);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    static Date toDate(LocalDate date) {
        return date == null ? null : Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    static LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    // ------------------------------------------------------------------ 内部：工具

    /**
     * 考点名称（内容表，全局共享）。
     */
    private String pointName(String pointCode) {
        if (pointCode == null || pointCode.trim().isEmpty()) {
            return null;
        }
        Map<String, String> names = pointNames(Collections.singletonList(pointCode.trim()));
        return names.get(pointCode.trim());
    }

    /**
     * 一批考点业务ID → 名称。
     */
    private Map<String, String> pointNames(List<String> pointCodes) {
        Map<String, String> names = new LinkedHashMap<>();
        if (pointCodes == null || pointCodes.isEmpty()) {
            return names;
        }
        List<Map<String, Object>> rows = questionMapper.selectPointNames(pointCodes);
        if (rows == null) {
            return names;
        }
        for (Map<String, Object> row : rows) {
            names.put(stringValue(row, "pointCode"), stringValue(row, "pointName"));
        }
        return names;
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

    private Date dateOf(Object value) {
        return value instanceof Date ? (Date) value : null;
    }

    /**
     * 变式解析结果。
     */
    private static final class VariantResolution {

        private final String motherCode;
        private final String degradeType;
        private final List<ErrorVariantVO> variants;

        private VariantResolution(String motherCode, String degradeType, List<ErrorVariantVO> variants) {
            this.motherCode = motherCode;
            this.degradeType = degradeType;
            this.variants = variants == null ? Collections.emptyList() : variants;
        }
    }
}
// update-end---author:mavis ---date:2026-11-04  for：【第二档-6 上线学习端错题线】错题入池的 childId 允许缺省 —— 缺省时由服务端会话推导（孩子取自己），前端不再必须先查 childCode 再回传-----------
