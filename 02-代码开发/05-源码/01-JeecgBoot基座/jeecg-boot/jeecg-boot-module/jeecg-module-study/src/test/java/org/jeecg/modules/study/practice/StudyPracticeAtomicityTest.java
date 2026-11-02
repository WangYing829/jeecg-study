package org.jeecg.modules.study.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.study.error.constant.ErrorCause;
import org.jeecg.modules.study.error.service.impl.StudyErrorPoolServiceImpl;
import org.jeecg.modules.study.error.vo.StudyErrorReportCommand;
import org.jeecg.modules.study.family.service.StudyChildService;
import org.jeecg.modules.study.family.service.StudyFamilyService;
import org.jeecg.modules.study.family.vo.ChildCreateRequest;
import org.jeecg.modules.study.family.vo.ChildCreateVO;
import org.jeecg.modules.study.practice.service.StudyPracticeService;
import org.jeecg.modules.study.practice.vo.AnswerCommand;
import org.jeecg.modules.study.support.StudyTestSupport;

/**
 * 作答流程的事务原子性测试（T-V0-10）。
 *
 * <p><b>单独一个测试类</b>：用 {@code @MockBean} 把错题池服务换成假实现，
 * 模拟「排期写入失败」，验证作答记录不会半写落库。</p>
 *
 * @author jeecg-study
 */
class StudyPracticeAtomicityTest extends StudyTestSupport {

    @Autowired
    private StudyPracticeService practiceService;

    @Autowired
    private StudyFamilyService familyService;

    @Autowired
    private StudyChildService childService;

    /**
     * 错题池服务被替换：写入直接失败。
     *
     * <p><b>为什么 mock 具体实现类而不是接口</b>：{@code StudyErrorPoolServiceImpl}
     * 同时实现 {@code StudyErrorPoolService} 与 {@code StudyErrorManageService}，
     * 是这两个契约唯一的 Bean。若 mock 接口，Spring 会把这个唯一 Bean 整体替换掉，
     * {@code StudyErrorPoolController} 注入 {@code StudyErrorManageService} 就会断链，
     * 整个 Spring 上下文起不来（连带其它测试类一起挂）。
     * mock 具体类时，替身仍然同时实现两个接口，契约不受影响。</p>
     */
    @MockBean
    private StudyErrorPoolServiceImpl errorPoolService;

    @Test
    @DisplayName("TC-08-09 / TC-11-16 事务原子性：错题池写入失败时作答记录不落库、无半写状态")
    void errorPoolFailure_practiceRecordNotPersisted() {
        String parentName = "ut_p_" + unique();
        String parentId = givenParentAccount(parentName);
        loginAsParent(parentId, parentName);
        familyService.create("UT家庭" + unique());
        ChildCreateVO child = childService.create(new ChildCreateRequest().setNickname("UT娃" + unique()));
        Map<String, Object> row = queryOne("SELECT user_id FROM study_child WHERE id = ?", child.getChildId());
        String kidUserId = row == null ? null : String.valueOf(row.get("user_id"));

        String questionCode = "UTQST-" + unique();
        jdbc.update("INSERT INTO study_question (id, question_code, question_type, subject_code, stem, answer,"
                + " answer_tolerance, analysis, difficulty, source_type, copyright_type, status, del_flag, create_time)"
                + " VALUES (?, ?, 'NUMERIC', 'MA', '两车相遇求时间', '12.5', 0.05, '先求速度和', 3, 'SELF', 'SELF', 1, 0, NOW())",
                com.baomidou.mybatisplus.core.toolkit.IdWorker.get32UUID(), questionCode);

        loginAsKid(kidUserId, "UTkid");

        // 注入「错题池写入失败」（对应排期写入失败的场景）
        when(errorPoolService.report(any(StudyErrorReportCommand.class)))
                .thenThrow(new JeecgBootException("复习排期写入失败", 500));

        AnswerCommand cmd = new AnswerCommand();
        cmd.setSessionId("PS-" + unique());
        cmd.setContentId(questionCode);
        cmd.setAnswer("11");
        cmd.setErrorCause(ErrorCause.CALCULATION_ERROR);

        assertThatThrownBy(() -> practiceService.submitAnswer(cmd))
                .isInstanceOf(JeecgBootException.class);

        // 作答记录不允许落库（不能出现半写状态）
        String table = "study_practice_record_" + YearMonth.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        assertThat(count("SELECT COUNT(*) FROM " + table + " WHERE child_id = ?", child.getChildId())).isZero();
    }

    @Override
    protected void cleanTestData() {
        try {
            String table = "study_practice_record_" + YearMonth.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
            jdbc.update("DELETE FROM " + table
                    + " WHERE child_id IN (SELECT id FROM study_child WHERE nickname LIKE 'UT%')");
            jdbc.update("DELETE FROM study_question WHERE question_code LIKE 'UTQST%'");
        } catch (Exception e) {
            System.out.println("[StudyPracticeAtomicityTest] 清理测试数据失败：" + e.getMessage());
        }
        super.cleanTestData();
    }
}
