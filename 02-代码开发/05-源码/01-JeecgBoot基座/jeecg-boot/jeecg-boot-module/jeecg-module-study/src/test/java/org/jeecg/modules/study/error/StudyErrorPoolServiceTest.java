package org.jeecg.modules.study.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.apache.shiro.util.ThreadContext;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.study.common.constant.StudyErrorCode;
import org.jeecg.modules.study.error.constant.ErrorCause;
import org.jeecg.modules.study.error.constant.ReviewSchedulePolicy;
import org.jeecg.modules.study.error.service.StudyErrorManageService;
import org.jeecg.modules.study.error.service.StudyErrorPoolService;
import org.jeecg.modules.study.error.vo.ErrorPoolItemVO;
import org.jeecg.modules.study.error.vo.ErrorVariantListVO;
import org.jeecg.modules.study.error.vo.ErrorVariantResult;
import org.jeecg.modules.study.error.vo.ReviewCompleteCommand;
import org.jeecg.modules.study.error.vo.ReviewCompleteVO;
import org.jeecg.modules.study.error.vo.ReviewQueueItemVO;
import org.jeecg.modules.study.error.vo.StudyErrorReportCommand;
import org.jeecg.modules.study.family.service.StudyChildService;
import org.jeecg.modules.study.family.service.StudyFamilyService;
import org.jeecg.modules.study.family.vo.ChildCreateRequest;
import org.jeecg.modules.study.family.vo.ChildCreateVO;
import org.jeecg.modules.study.support.StudyTestSupport;

/**
 * 统一错题池 + 复习调度引擎自动化测试（T-V0-06 / T-V0-07）。
 *
 * <p>覆盖 TC-02-01…TC-02-16、TC-11-02 / TC-11-03 / TC-11-04、TC-08-07 / TC-08-08。</p>
 *
 * @author jeecg-study
 */
class StudyErrorPoolServiceTest extends StudyTestSupport {

    @Autowired
    private StudyErrorPoolService errorPoolService;

    @Autowired
    private StudyErrorManageService errorManageService;

    @Autowired
    private StudyFamilyService familyService;

    @Autowired
    private StudyChildService childService;

    @Autowired
    private ApplicationContext applicationContext;

    // ------------------------------------------------------------------ TC-02-01…06

    @Test
    @DisplayName("TC-02-01 拍照录入完整流程：返回 errorCode，错题本可见，下次复习=今天")
    void report_thenVisibleInPool_withTodayReview() {
        TestChild child = givenChild();
        String errorCode = errorPoolService.report(command(child, "UTQST-" + unique(), ErrorCause.CONCEPT_UNCLEAR));

        assertThat(errorCode).startsWith("ERR-");

        List<ErrorPoolItemVO> items = errorPoolService.listByChild(child.childId);
        assertThat(items).hasSize(1);
        ErrorPoolItemVO item = items.get(0);
        assertThat(item.getErrorCode()).isEqualTo(errorCode);
        assertThat(item.getErrorCause()).isEqualTo(ErrorCause.CONCEPT_UNCLEAR);
        assertThat(item.getNextReviewAt()).isNotNull();
        assertThat(toLocalDate(item.getNextReviewAt())).isEqualTo(LocalDate.now());

        // 排期表当天就有一行
        assertThat(count("SELECT COUNT(*) FROM study_review_schedule WHERE error_code = ? AND schedule_date = CURDATE()",
                errorCode)).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-02-03 非自编来源必须填出处，未填返回 4006")
    void report_withoutSourceRef_rejected() {
        TestChild child = givenChild();
        StudyErrorReportCommand cmd = command(child, "UTQST-" + unique(), ErrorCause.CONCEPT_UNCLEAR);
        cmd.setCopyrightType("PUBLIC");
        cmd.setSourceRef("");

        assertThatThrownBy(() -> errorPoolService.report(cmd))
                .isInstanceOf(JeecgBootException.class)
                .satisfies(e -> assertThat(((JeecgBootException) e).getErrCode())
                        .isEqualTo(StudyErrorCode.COPYRIGHT_INCOMPLETE));

        assertThat(count("SELECT COUNT(*) FROM study_error_pool WHERE child_id = ?", child.childId)).isZero();
    }

    @Test
    @DisplayName("TC-02-04 错因未选不可提交，返回 4001")
    void report_withoutErrorCause_rejected() {
        TestChild child = givenChild();
        StudyErrorReportCommand cmd = command(child, "UTQST-" + unique(), null);

        assertThatThrownBy(() -> errorPoolService.report(cmd))
                .isInstanceOf(JeecgBootException.class)
                .satisfies(e -> assertThat(((JeecgBootException) e).getErrCode())
                        .isEqualTo(StudyErrorCode.MISSING_ERROR_CAUSE));

        assertThat(count("SELECT COUNT(*) FROM study_error_pool WHERE child_id = ?", child.childId)).isZero();
    }

    @Test
    @DisplayName("TC-02-05 错因五维齐全：恰好 5 个选项")
    void errorCause_hasExactlyFiveDimensions() {
        assertThat(ErrorCause.ALL).hasSize(5);
        assertThat(ErrorCause.ALL).containsExactly(
                ErrorCause.CONCEPT_UNCLEAR,
                ErrorCause.READING_DEVIATION,
                ErrorCause.CALCULATION_ERROR,
                ErrorCause.METHOD_UNKNOWN,
                ErrorCause.TIME_SHORTAGE);
        assertThat(ErrorCause.isValid(ErrorCause.CONCEPT_UNCLEAR)).isTrue();
        assertThat(ErrorCause.isValid("随便写的原因")).isFalse();
        assertThat(ErrorCause.isValid(null)).isFalse();
    }

    @Test
    @DisplayName("TC-02-06 错因可事后修改，统计随之变化")
    void updateErrorCause_changesStatistics() {
        TestChild child = givenChild();
        String errorCode = errorPoolService.report(command(child, "UTQST-" + unique(), ErrorCause.CONCEPT_UNCLEAR));

        errorManageService.updateErrorCause(errorCode, ErrorCause.CALCULATION_ERROR);

        Map<String, Integer> statistics = errorPoolService.errorCauseStatistics(child.childId);
        assertThat(statistics.get(ErrorCause.CALCULATION_ERROR)).isEqualTo(1);
        assertThat(statistics.get(ErrorCause.CONCEPT_UNCLEAR)).isZero();
        assertThat(errorPoolService.listByChild(child.childId).get(0).getErrorCause())
                .isEqualTo(ErrorCause.CALCULATION_ERROR);
    }

    // ------------------------------------------------------------------ TC-02-07…10 排期

    @Test
    @DisplayName("TC-02-07 排期：当天 → +1 天")
    void correctOnce_scheduleTomorrow() {
        TestChild child = givenChild();
        String errorCode = errorPoolService.report(command(child, "UTQST-" + unique(), ErrorCause.CONCEPT_UNCLEAR));

        errorPoolService.completeReview(errorCode, true, null);

        ErrorPoolItemVO item = errorPoolService.listByChild(child.childId).get(0);
        assertThat(item.getCorrectStreak()).isEqualTo(1);
        assertThat(toLocalDate(item.getNextReviewAt())).isEqualTo(LocalDate.now().plusDays(1));
        assertThat(item.getArchived()).isFalse();
    }

    @Test
    @DisplayName("TC-02-08 排期阶梯 +1 / +3 / +7（三档业务可达，第 3 次做对后归档）")
    void scheduleLadder_oneThreeSeven() {
        TestChild child = givenChild();
        String errorCode = errorPoolService.report(command(child, "UTQST-" + unique(), ErrorCause.CONCEPT_UNCLEAR));

        // 第 1 次答对（在今天）→ +1 天
        errorPoolService.completeReview(errorCode, true, null);
        ErrorPoolItemVO afterFirst = errorPoolService.listByChild(child.childId).get(0);
        assertThat(toLocalDate(afterFirst.getNextReviewAt())).isEqualTo(LocalDate.now().plusDays(1));

        // 第 2 次答对（在 +1 天）→ 阶梯算出 +3 天（复习日 now+1 → 下次 now+4），仍在队列
        ReviewCompleteCommand second = new ReviewCompleteCommand();
        second.setErrorCode(errorCode);
        second.setCorrect(true);
        second.setReviewDate(toDate(LocalDate.now().plusDays(1)));
        errorManageService.completeReview(second);

        ErrorPoolItemVO afterSecond = errorPoolService.listByChild(child.childId).get(0);
        assertThat(afterSecond.getCorrectStreak()).isEqualTo(2);
        assertThat(afterSecond.getArchived()).isFalse();
        assertThat(toLocalDate(afterSecond.getNextReviewAt())).isEqualTo(LocalDate.now().plusDays(4));

        // 第 3 次答对（在 +4 天）→ 阶梯算出 +7 天（复习日 now+4 → 下次 now+11），同时达到 3 次做对 → 归档
        ReviewCompleteCommand third = new ReviewCompleteCommand();
        third.setErrorCode(errorCode);
        third.setCorrect(true);
        third.setReviewDate(toDate(LocalDate.now().plusDays(4)));
        errorManageService.completeReview(third);

        ErrorPoolItemVO afterThird = errorPoolService.listByChild(child.childId).get(0);
        assertThat(afterThird.getCorrectStreak()).isEqualTo(3);
        assertThat(toLocalDate(afterThird.getNextReviewAt())).isEqualTo(LocalDate.now().plusDays(11));
        assertThat(afterThird.getArchived()).isTrue();

        // 阶梯策略：1→+1、2→+3、3→+7、≥4→+14（≥4 业务不可达，仅策略层保留档）
        assertThat(ReviewSchedulePolicy.nextIntervalDays(1)).isEqualTo(1);
        assertThat(ReviewSchedulePolicy.nextIntervalDays(2)).isEqualTo(3);
        assertThat(ReviewSchedulePolicy.nextIntervalDays(3)).isEqualTo(7);
        assertThat(ReviewSchedulePolicy.nextIntervalDays(4)).isEqualTo(14);
    }

    @Test
    @DisplayName("TC-02-09 连续三次做对才移出队列（archived=true）")
    void correctThreeTimes_archivedOutOfQueue() {
        TestChild child = givenChild();
        String errorCode = errorPoolService.report(command(child, "UTQST-" + unique(), ErrorCause.CONCEPT_UNCLEAR));

        // 第 1 次做对 → 仍在队列
        errorPoolService.completeReview(errorCode, true, null);
        assertThat(errorPoolService.listByChild(child.childId).get(0).getArchived()).isFalse();

        // 第 2 次做对 → 仍在队列（移出阈值是 3 次）
        ReviewCompleteCommand second = new ReviewCompleteCommand();
        second.setErrorCode(errorCode);
        second.setCorrect(true);
        second.setReviewDate(toDate(LocalDate.now().plusDays(1)));
        ReviewCompleteVO secondVo = errorManageService.completeReview(second);

        assertThat(secondVo.getArchived()).isFalse();
        assertThat(secondVo.getCorrectStreak()).isEqualTo(2);

        // 第 3 次做对 → 移出队列
        ReviewCompleteCommand third = new ReviewCompleteCommand();
        third.setErrorCode(errorCode);
        third.setCorrect(true);
        third.setReviewDate(toDate(LocalDate.now().plusDays(3)));
        ReviewCompleteVO vo = errorManageService.completeReview(third);

        assertThat(vo.getArchived()).isTrue();
        ErrorPoolItemVO item = errorPoolService.listByChild(child.childId).get(0);
        assertThat(item.getArchived()).isTrue();
        assertThat(item.getReviewStatus()).isEqualTo(ReviewSchedulePolicy.REVIEW_ARCHIVED);
        assertThat(count("SELECT COUNT(*) FROM study_error_pool WHERE error_code = ? AND archived_at IS NOT NULL",
                errorCode)).isEqualTo(1);

        // 已移出 → 不再出现在今日队列
        assertThat(errorPoolService.todayQueue(child.childId)).isEmpty();
    }

    @Test
    @DisplayName("TC-02-10 答错回退重排：下次复习重置为当天，correctStreak 归零")
    void wrongAnswer_resetsScheduleToSameDay() {
        TestChild child = givenChild();
        String errorCode = errorPoolService.report(command(child, "UTQST-" + unique(), ErrorCause.CONCEPT_UNCLEAR));
        errorPoolService.completeReview(errorCode, true, null);
        assertThat(errorPoolService.listByChild(child.childId).get(0).getCorrectStreak()).isEqualTo(1);

        ReviewCompleteCommand wrong = new ReviewCompleteCommand();
        wrong.setErrorCode(errorCode);
        wrong.setCorrect(false);
        wrong.setErrorCause(ErrorCause.TIME_SHORTAGE);
        wrong.setReviewDate(toDate(LocalDate.now().plusDays(1)));
        errorManageService.completeReview(wrong);

        ErrorPoolItemVO item = errorPoolService.listByChild(child.childId).get(0);
        assertThat(item.getCorrectStreak()).isZero();
        assertThat(toLocalDate(item.getNextReviewAt())).isEqualTo(LocalDate.now().plusDays(1));
        assertThat(item.getErrorCause()).isEqualTo(ErrorCause.TIME_SHORTAGE);
    }

    // ------------------------------------------------------------------ TC-02-11 / 12 统一队列

    @Test
    @DisplayName("TC-02-11 复习队列跨模块统一，按排序权重降序")
    void todayQueue_containsAllModules_sortedByScore() {
        TestChild child = givenChild();
        String[] modules = {"WORD", "MATH", "GRAMMAR", "WRITING"};
        for (String module : modules) {
            StudyErrorReportCommand cmd = command(child, "UTQST-" + unique(), ErrorCause.CONCEPT_UNCLEAR);
            cmd.setSourceModule(module);
            errorPoolService.report(cmd);
        }

        List<ReviewQueueItemVO> queue = errorPoolService.todayQueue(child.childId);
        assertThat(queue).hasSize(4);
        assertThat(queue.stream().map(ReviewQueueItemVO::getSourceModule).distinct().count()).isEqualTo(4);

        // 排序分降序
        List<java.math.BigDecimal> scores = new ArrayList<>();
        for (ReviewQueueItemVO item : queue) {
            scores.add(item.getOrderWeight());
        }
        assertThat(scores).isSortedAccordingTo(java.util.Comparator.reverseOrder());

        // 把第一条排期改成 3 天前 → 逾期加权后必须排到最前
        ReviewQueueItemVO last = queue.get(queue.size() - 1);
        jdbc.update("UPDATE study_review_schedule SET schedule_date = DATE_SUB(CURDATE(), INTERVAL 3 DAY)"
                + " WHERE error_code = ?", last.getErrorCode());
        List<ReviewQueueItemVO> reordered = errorPoolService.todayQueue(child.childId);
        assertThat(reordered.get(0).getErrorCode()).isEqualTo(last.getErrorCode());
    }

    @Test
    @DisplayName("TC-02-12 各模块不出现独立错题列表：错题接口只在 error 域")
    void errorPoolEndpoints_onlyInErrorDomain() {
        for (Object bean : applicationContext.getBeansWithAnnotation(RestController.class).values()) {
            Class<?> type = org.springframework.aop.support.AopUtils.getTargetClass(bean);
            String packageName = type.getPackageName();
            if (packageName.startsWith("org.jeecg.modules.study.error")) {
                continue;
            }
            for (Method method : type.getMethods()) {
                String path = mappingPath(method);
                if (path != null && (path.contains("error-pool") || path.contains("/wrong"))) {
                    throw new AssertionError("发现错题域之外的错题接口：" + type.getName() + "#" + method.getName() + " -> " + path);
                }
            }
        }
    }

    // ------------------------------------------------------------------ TC-02-13…15 变式

    @Test
    @DisplayName("TC-02-13 一题多变：变式存在且带层级标识")
    void listVariants_returnsVariantsWithLayer() {
        TestChild child = givenChild();
        String motherCode = "UTMTH-" + unique();
        String questionCode = givenQuestionWithMother(motherCode);
        givenVariant(motherCode, 2);
        String errorCode = errorPoolService.report(command(child, questionCode, ErrorCause.METHOD_UNKNOWN));

        ErrorVariantListVO vo = errorManageService.listVariants(errorCode);

        assertThat(vo.getVariantAvailable()).isTrue();
        assertThat(vo.getDegradeType()).isEqualTo("MOTHER");
        assertThat(vo.getMotherCode()).isEqualTo(motherCode);
        assertThat(vo.getVariants()).isNotEmpty();
        assertThat(vo.getVariants().get(0).getLayer()).isEqualTo(2);
        assertThat(vo.getVariants().get(0).getLayerName()).isEqualTo("换情境");
    }

    @Test
    @DisplayName("TC-02-14 变式未全对不计入「做对」，correctStreak 不增加")
    void variantNotAllCorrect_streakNotIncreased() {
        TestChild child = givenChild();
        String motherCode = "UTMTH-" + unique();
        String questionCode = givenQuestionWithMother(motherCode);
        givenVariant(motherCode, 1);
        String errorCode = errorPoolService.report(command(child, questionCode, ErrorCause.METHOD_UNKNOWN));

        ReviewCompleteCommand cmd = new ReviewCompleteCommand();
        cmd.setErrorCode(errorCode);
        cmd.setCorrect(true);
        cmd.setErrorCause(null);
        ErrorVariantResult variantResult = new ErrorVariantResult();
        variantResult.setVariantCode("UTVAR-" + unique());
        variantResult.setCorrect(false);
        cmd.setVariantResults(java.util.Collections.singletonList(variantResult));

        ReviewCompleteVO vo = errorManageService.completeReview(cmd);

        assertThat(vo.getCounted()).isFalse();
        assertThat(errorPoolService.listByChild(child.childId).get(0).getCorrectStreak()).isZero();
    }

    @Test
    @DisplayName("TC-02-15 无母题错题降级为同知识点同题型变式；确实没有则标记暂无可用变式但仍进队列")
    void listVariants_degradeToSamePointOrNone() {
        TestChild child = givenChild();
        String pointCode = "UTKPT-" + unique();
        String questionCode = givenQuestionWithPoint(pointCode);
        givenQuestionWithPoint(pointCode);

        StudyErrorReportCommand cmd = command(child, questionCode, ErrorCause.CONCEPT_UNCLEAR);
        cmd.setKnowledgePointCode(pointCode);
        String errorCode = errorPoolService.report(cmd);
        ErrorVariantListVO degraded = errorManageService.listVariants(errorCode);
        assertThat(degraded.getVariantAvailable()).isTrue();
        assertThat(degraded.getDegradeType()).isEqualTo("SAME_POINT");
        assertThat(degraded.getVariants().get(0).getLayer()).isEqualTo(1);

        // 没有知识点也没有母题 → 暂无可用变式，但错题仍然在队列里
        StudyErrorReportCommand plain = command(child, "UTQST-" + unique(), ErrorCause.CONCEPT_UNCLEAR);
        plain.setKnowledgePointCode(null);
        String plainCode = errorPoolService.report(plain);
        ErrorVariantListVO none = errorManageService.listVariants(plainCode);
        assertThat(none.getVariantAvailable()).isFalse();
        assertThat(none.getDegradeType()).isEqualTo("NONE");
        assertThat(errorPoolService.todayQueue(child.childId)).hasSize(2);
    }

    // ------------------------------------------------------------------ TC-02-16 统计

    @Test
    @DisplayName("TC-02-16 错因分布统计：按五维分组计数，与错题池条数一致")
    void statistics_groupByFiveDimensions() {
        TestChild child = givenChild();
        errorPoolService.report(command(child, "UTQST-" + unique(), ErrorCause.CONCEPT_UNCLEAR));
        errorPoolService.report(command(child, "UTQST-" + unique(), ErrorCause.CONCEPT_UNCLEAR));
        errorPoolService.report(command(child, "UTQST-" + unique(), ErrorCause.CALCULATION_ERROR));
        errorPoolService.report(command(child, "UTQST-" + unique(), ErrorCause.TIME_SHORTAGE));

        Map<String, Integer> statistics = errorPoolService.errorCauseStatistics(child.childId);

        assertThat(statistics).hasSize(5);
        assertThat(statistics.get(ErrorCause.CONCEPT_UNCLEAR)).isEqualTo(2);
        assertThat(statistics.get(ErrorCause.CALCULATION_ERROR)).isEqualTo(1);
        assertThat(statistics.get(ErrorCause.TIME_SHORTAGE)).isEqualTo(1);
        assertThat(statistics.get(ErrorCause.METHOD_UNKNOWN)).isZero();
        int sum = statistics.values().stream().mapToInt(Integer::intValue).sum();
        assertThat(sum).isEqualTo(errorPoolService.listByChild(child.childId).size());
    }

    // ------------------------------------------------------------------ TC-11 端到端

    @Test
    @DisplayName("TC-11-02 六模块错题汇聚：四模块各造错，池内条数一致")
    void errorsFromModules_gatherIntoOnePool() {
        TestChild child = givenChild();
        for (String module : new String[]{"WORD", "MATH", "GRAMMAR", "WRITING"}) {
            StudyErrorReportCommand cmd = command(child, "UTQST-" + unique(), ErrorCause.CONCEPT_UNCLEAR);
            cmd.setSourceModule(module);
            errorPoolService.report(cmd);
        }

        List<ErrorPoolItemVO> items = errorPoolService.listByChild(child.childId);
        assertThat(items).hasSize(4);
        assertThat(items.stream().map(ErrorPoolItemVO::getSourceModule).distinct().count()).isEqualTo(4);
    }

    @Test
    @DisplayName("TC-11-03 错因强制：未选错因提交被拒（4001）")
    void missingErrorCause_rejectedWith4001() {
        TestChild child = givenChild();
        StudyErrorReportCommand cmd = command(child, "UTQST-" + unique(), "  ");

        assertThatThrownBy(() -> errorPoolService.report(cmd))
                .isInstanceOf(JeecgBootException.class)
                .satisfies(e -> assertThat(((JeecgBootException) e).getErrCode())
                        .isEqualTo(StudyErrorCode.MISSING_ERROR_CAUSE));
    }

    @Test
    @DisplayName("TC-11-04 排期正确：答错→答对→答对 = 当天 → +1 → 移出")
    void scheduleFlow_wrongThenTwoCorrect() {
        TestChild child = givenChild();
        String errorCode = errorPoolService.report(command(child, "UTQST-" + unique(), ErrorCause.CONCEPT_UNCLEAR));

        // 答错 → 当天
        assertThat(toLocalDate(errorPoolService.listByChild(child.childId).get(0).getNextReviewAt()))
                .isEqualTo(LocalDate.now());

        // 答对 → +1
        errorPoolService.completeReview(errorCode, true, null);
        assertThat(toLocalDate(errorPoolService.listByChild(child.childId).get(0).getNextReviewAt()))
                .isEqualTo(LocalDate.now().plusDays(1));

        // 再答对 → 间隔 +3 天（复习日 now+1 → 下次复习 now+4），仍在队列
        ReviewCompleteCommand second = new ReviewCompleteCommand();
        second.setErrorCode(errorCode);
        second.setCorrect(true);
        second.setReviewDate(toDate(LocalDate.now().plusDays(1)));
        errorManageService.completeReview(second);
        assertThat(toLocalDate(errorPoolService.listByChild(child.childId).get(0).getNextReviewAt()))
                .isEqualTo(LocalDate.now().plusDays(4));

        // 第 3 次答对 → 移出队列
        ReviewCompleteCommand third = new ReviewCompleteCommand();
        third.setErrorCode(errorCode);
        third.setCorrect(true);
        third.setReviewDate(toDate(LocalDate.now().plusDays(4)));
        errorManageService.completeReview(third);

        ErrorPoolItemVO item = errorPoolService.listByChild(child.childId).get(0);
        assertThat(item.getCorrectStreak()).isEqualTo(3);
        assertThat(item.getArchived()).isTrue();
        assertThat(errorPoolService.todayQueue(child.childId)).isEmpty();
    }

    // ------------------------------------------------------------------ TC-08-07 / 08 幂等与并发

    @Test
    @DisplayName("TC-08-07 复习完成幂等：同一错题同一天重复提交，排期只推进一次")
    void completeReview_sameDay_onlyAdvancesOnce() {
        TestChild child = givenChild();
        String errorCode = errorPoolService.report(command(child, "UTQST-" + unique(), ErrorCause.CONCEPT_UNCLEAR));

        errorPoolService.completeReview(errorCode, true, null);
        errorPoolService.completeReview(errorCode, true, null);
        errorPoolService.completeReview(errorCode, true, null);

        ErrorPoolItemVO item = errorPoolService.listByChild(child.childId).get(0);
        assertThat(item.getCorrectStreak()).isEqualTo(1);
        assertThat(item.getArchived()).isFalse();
        assertThat(count("SELECT COUNT(*) FROM study_review_schedule WHERE error_code = ? AND status = 2",
                errorCode)).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-08-08 并发复习不重复累加：correct_streak 只 +1")
    void completeReview_concurrentOnlyIncrementsOnce() throws Exception {
        TestChild child = givenChild();
        String errorCode = errorPoolService.report(command(child, "UTQST-" + unique(), ErrorCause.CONCEPT_UNCLEAR));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            futures.add(pool.submit(() -> {
                bindPrincipal(child.parentId, child.parentName, "study_parent");
                try {
                    start.await(5, TimeUnit.SECONDS);
                    errorPoolService.completeReview(errorCode, true, null);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    ThreadContext.remove();
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get(20, TimeUnit.SECONDS);
        }
        pool.shutdown();

        ErrorPoolItemVO item = errorPoolService.listByChild(child.childId).get(0);
        assertThat(item.getCorrectStreak()).isEqualTo(1);
        assertThat(item.getArchived()).isFalse();
    }

    // ------------------------------------------------------------------ 数据范围

    @Test
    @DisplayName("TC-08-05 判定来源写入受限：judge_source=model 被拒（R02-4）")
    void report_withIllegalJudgeSource_rejected() {
        TestChild child = givenChild();
        StudyErrorReportCommand cmd = command(child, "UTQST-" + unique(), ErrorCause.CONCEPT_UNCLEAR);
        cmd.setJudgeSource("model");

        assertThatThrownBy(() -> errorPoolService.report(cmd))
                .isInstanceOf(JeecgBootException.class);

        assertThat(count("SELECT COUNT(*) FROM study_error_pool WHERE child_id = ?", child.childId)).isZero();
    }

    @Test
    @DisplayName("数据范围：家长访问非关联孩子的错题返回 4010")
    void crossParentAccess_forbidden() {
        TestChild owner = givenChild();
        String otherParentName = "ut_x_" + unique();
        String otherParentId = givenParentAccount(otherParentName);
        loginAsParent(otherParentId, otherParentName);

        assertThatThrownBy(() -> errorPoolService.listByChild(owner.childId))
                .isInstanceOf(JeecgBootException.class)
                .satisfies(e -> assertThat(((JeecgBootException) e).getErrCode())
                        .isEqualTo(StudyErrorCode.FORBIDDEN_SCOPE));
    }

    // ------------------------------------------------------------------ 工具

    @Override
    protected void cleanTestData() {
        try {
            jdbc.update("DELETE FROM study_review_schedule WHERE child_id IN"
                    + " (SELECT id FROM study_child WHERE nickname LIKE 'UT%')");
            jdbc.update("DELETE FROM study_error_pool WHERE child_id IN"
                    + " (SELECT id FROM study_child WHERE nickname LIKE 'UT%')");
            jdbc.update("DELETE FROM study_question_variant WHERE variant_code LIKE 'UTVAR%'");
            jdbc.update("DELETE FROM study_question WHERE question_code LIKE 'UTQST%'");
        } catch (Exception e) {
            System.out.println("[StudyErrorPoolServiceTest] 清理测试数据失败：" + e.getMessage());
        }
        super.cleanTestData();
    }

    /**
     * 造一个「家庭 → 孩子」最小闭环，返回家长与孩子的标识。
     */
    private TestChild givenChild() {
        String parentName = "ut_p_" + unique();
        String parentId = givenParentAccount(parentName);
        loginAsParent(parentId, parentName);
        familyService.create("UT家庭" + unique());
        ChildCreateVO child = childService.create(new ChildCreateRequest().setNickname("UT娃" + unique()));
        Map<String, Object> row = queryOne("SELECT user_id FROM study_child WHERE id = ?", child.getChildId());
        return new TestChild(parentId, parentName, child.getChildId(), child.getChildCode(),
                row == null ? null : String.valueOf(row.get("user_id")));
    }

    private StudyErrorReportCommand command(TestChild child, String contentId, String errorCause) {
        StudyErrorReportCommand cmd = new StudyErrorReportCommand();
        cmd.setChildId(child.childId);
        cmd.setSourceModule("MATH");
        cmd.setContentType("QUESTION");
        cmd.setContentId(contentId);
        cmd.setKnowledgePointCode("UTKPT-" + unique());
        cmd.setSubjectCode("MA");
        cmd.setErrorCause(errorCause);
        cmd.setRawAnswer("11");
        cmd.setCorrectAnswer("12.5");
        cmd.setJudgeSource("rule");
        cmd.setSourceRef("自编");
        cmd.setCopyrightType("SELF");
        return cmd;
    }

    /**
     * 造一道挂在母题下的题目。
     */
    private String givenQuestionWithMother(String motherCode) {
        String questionCode = "UTQST-" + unique();
        jdbc.update("INSERT INTO study_question (id, question_code, question_type, subject_code, stem, answer,"
                + " mother_question_code, difficulty, source_type, copyright_type, status, del_flag, create_time)"
                + " VALUES (?, ?, 'NUMERIC', 'MA', '母题变式题干', '12.5', ?, 3, 'SELF', 'SELF', 1, 0, NOW())",
                com.baomidou.mybatisplus.core.toolkit.IdWorker.get32UUID(), questionCode, motherCode);
        return questionCode;
    }

    /**
     * 造一道带知识点的题目（降级变式用）。
     */
    private String givenQuestionWithPoint(String pointCode) {
        String questionCode = "UTQST-" + unique();
        jdbc.update("INSERT INTO study_question (id, question_code, question_type, subject_code, knowledge_point_code,"
                + " stem, answer, variant_layer, difficulty, source_type, copyright_type, status, del_flag, create_time)"
                + " VALUES (?, ?, 'NUMERIC', 'MA', ?, '降级变式题干', '12.5', 1, 3, 'SELF', 'SELF', 1, 0, NOW())",
                com.baomidou.mybatisplus.core.toolkit.IdWorker.get32UUID(), questionCode, pointCode);
        return questionCode;
    }

    /**
     * 造一条母题变式。
     */
    private void givenVariant(String motherCode, int layer) {
        jdbc.update("INSERT INTO study_question_variant (id, variant_code, mother_code, layer, question_code, stem,"
                + " answer, generated_by, status, del_flag, create_time)"
                + " VALUES (?, ?, ?, ?, NULL, '变式题干', '12.5', 'RULE', 1, 0, NOW())",
                com.baomidou.mybatisplus.core.toolkit.IdWorker.get32UUID(), "UTVAR-" + unique(), motherCode, layer);
    }

    private static String mappingPath(Method method) {
        GetMapping get = AnnotatedElementUtils.findMergedAnnotation(method, GetMapping.class);
        if (get != null && get.value().length > 0) {
            return get.value()[0];
        }
        PostMapping post = AnnotatedElementUtils.findMergedAnnotation(method, PostMapping.class);
        if (post != null && post.value().length > 0) {
            return post.value()[0];
        }
        PutMapping put = AnnotatedElementUtils.findMergedAnnotation(method, PutMapping.class);
        if (put != null && put.value().length > 0) {
            return put.value()[0];
        }
        DeleteMapping delete = AnnotatedElementUtils.findMergedAnnotation(method, DeleteMapping.class);
        if (delete != null && delete.value().length > 0) {
            return delete.value()[0];
        }
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
        if (mapping != null && mapping.value().length > 0) {
            return mapping.value()[0];
        }
        return null;
    }

    private static LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private static Date toDate(LocalDate date) {
        return date == null ? null : Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    /**
     * 测试用的孩子上下文。
     */
    private static final class TestChild {
        private final String parentId;
        private final String parentName;
        private final String childId;
        private final String childCode;
        private final String kidUserId;

        private TestChild(String parentId, String parentName, String childId, String childCode, String kidUserId) {
            this.parentId = parentId;
            this.parentName = parentName;
            this.childId = childId;
            this.childCode = childCode;
            this.kidUserId = kidUserId;
        }
    }
}
