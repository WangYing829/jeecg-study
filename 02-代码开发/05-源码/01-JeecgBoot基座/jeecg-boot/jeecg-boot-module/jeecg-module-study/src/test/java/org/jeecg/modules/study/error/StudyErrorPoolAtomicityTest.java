package org.jeecg.modules.study.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.study.error.constant.ErrorCause;
import org.jeecg.modules.study.error.entity.StudyReviewSchedule;
import org.jeecg.modules.study.error.mapper.StudyReviewScheduleMapper;
import org.jeecg.modules.study.error.service.StudyErrorPoolService;
import org.jeecg.modules.study.error.vo.StudyErrorReportCommand;
import org.jeecg.modules.study.family.service.StudyChildService;
import org.jeecg.modules.study.family.service.StudyFamilyService;
import org.jeecg.modules.study.family.vo.ChildCreateRequest;
import org.jeecg.modules.study.family.vo.ChildCreateVO;
import org.jeecg.modules.study.support.StudyTestSupport;

/**
 * 错题入池的事务原子性测试（T-V0-06）。
 *
 * <p><b>单独一个测试类</b>：这里用 {@code @MockBean} 把排期 Mapper 换成假实现，
 * 用来注入「排期写入失败」；不能影响其它测试类的真实数据库行为。</p>
 *
 * @author jeecg-study
 */
class StudyErrorPoolAtomicityTest extends StudyTestSupport {

    @Autowired
    private StudyErrorPoolService errorPoolService;

    @Autowired
    private StudyFamilyService familyService;

    @Autowired
    private StudyChildService childService;

    /** 排期 Mapper 被替换：写不进去 */
    @MockBean
    private StudyReviewScheduleMapper scheduleMapper;

    @Test
    @DisplayName("TC-08-09 / TC-11-16 事务原子性：排期写入失败时错题记录不落库、无半写状态")
    void scheduleWriteFailure_errorRecordNotPersisted() {
        String parentName = "ut_p_" + unique();
        String parentId = givenParentAccount(parentName);
        loginAsParent(parentId, parentName);
        familyService.create("UT家庭" + unique());
        ChildCreateVO child = childService.create(new ChildCreateRequest().setNickname("UT娃" + unique()));

        // 注入「排期写入失败」
        when(scheduleMapper.insert(any(StudyReviewSchedule.class))).thenReturn(0);

        StudyErrorReportCommand cmd = new StudyErrorReportCommand();
        cmd.setChildId(child.getChildId());
        cmd.setSourceModule("MATH");
        cmd.setContentType("QUESTION");
        cmd.setContentId("UTQST-" + unique());
        cmd.setKnowledgePointCode("UTKPT-" + unique());
        cmd.setSubjectCode("MA");
        cmd.setErrorCause(ErrorCause.CONCEPT_UNCLEAR);
        cmd.setRawAnswer("11");
        cmd.setCorrectAnswer("12.5");
        cmd.setJudgeSource("rule");
        cmd.setCopyrightType("SELF");

        assertThatThrownBy(() -> errorPoolService.report(cmd))
                .isInstanceOf(JeecgBootException.class);

        // 错题记录不允许落库（不能出现半写状态）
        assertThat(count("SELECT COUNT(*) FROM study_error_pool WHERE child_id = ?", child.getChildId())).isZero();
    }

    @Override
    protected void cleanTestData() {
        try {
            jdbc.update("DELETE FROM study_error_pool WHERE child_id IN"
                    + " (SELECT id FROM study_child WHERE nickname LIKE 'UT%')");
        } catch (Exception e) {
            System.out.println("[StudyErrorPoolAtomicityTest] 清理测试数据失败：" + e.getMessage());
        }
        super.cleanTestData();
    }
}
