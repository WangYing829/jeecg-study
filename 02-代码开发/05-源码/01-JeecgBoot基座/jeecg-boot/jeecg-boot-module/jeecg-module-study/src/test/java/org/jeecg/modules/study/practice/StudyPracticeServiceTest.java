package org.jeecg.modules.study.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.study.common.constant.StudyErrorCode;
import org.jeecg.modules.study.common.enums.JudgeSource;
import org.jeecg.modules.study.error.constant.ErrorCause;
import org.jeecg.modules.study.family.service.StudyChildService;
import org.jeecg.modules.study.family.service.StudyFamilyService;
import org.jeecg.modules.study.family.vo.ChildCreateRequest;
import org.jeecg.modules.study.family.vo.ChildCreateVO;
import org.jeecg.modules.study.practice.service.StudyPracticeService;
import org.jeecg.modules.study.practice.vo.AnswerCommand;
import org.jeecg.modules.study.practice.vo.AnswerResultVO;
import org.jeecg.modules.study.practice.vo.ExplanationVO;
import org.jeecg.modules.study.practice.vo.PracticeQuestionVO;
import org.jeecg.modules.study.support.StudyTestSupport;

/**
 * 练习 / 判定 / 解析自动化测试（T-V0-09 / T-V0-10）。
 *
 * <p>覆盖 TC-08-03…TC-08-06、TC-08-10、TC-08-11、TC-11-03、TC-11-14。</p>
 *
 * @author jeecg-study
 */
class StudyPracticeServiceTest extends StudyTestSupport {

    /** 标准答案 */
    private static final String ANSWER = "12.5";

    /** 数值容差 */
    private static final String TOLERANCE = "0.05";

    @Autowired
    private StudyPracticeService practiceService;

    @Autowired
    private StudyFamilyService familyService;

    @Autowired
    private StudyChildService childService;

    // ------------------------------------------------------------------ TC-08-03

    @Test
    @DisplayName("TC-08-03 取题接口响应不含 answer / analysis / correctAnswer / solution")
    void nextQuestion_responseHasNoAnswerFields() throws Exception {
        TestKid kid = givenKid();
        givenQuestion();
        loginAsKid(kid.kidUserId, "UTkid");

        PracticeQuestionVO vo = practiceService.nextQuestion("math");

        assertThat(vo).isNotNull();
        assertThat(vo.getContentId()).isNotBlank();
        assertThat(vo.getStem()).isNotBlank();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(mapper.writeValueAsString(vo));
        List<String> keys = new ArrayList<>();
        collectKeys(node, keys);
        assertThat(keys).doesNotContain("answer", "analysis", "correctAnswer", "solution");
    }

    // ------------------------------------------------------------------ TC-08-04

    @Test
    @DisplayName("TC-08-04 未作答直接取解析被拒，返回 4003")
    void explanation_beforeAnswer_rejected() {
        TestKid kid = givenKid();
        String questionCode = givenQuestion();
        loginAsKid(kid.kidUserId, "UTkid");

        assertThatThrownBy(() -> practiceService.explanation(questionCode, null))
                .isInstanceOf(JeecgBootException.class)
                .satisfies(e -> assertThat(((JeecgBootException) e).getErrCode())
                        .isEqualTo(StudyErrorCode.PRACTICE_ORDER_VIOLATION));
    }

    @Test
    @DisplayName("TC-08-04 作答之后可以取到解析")
    void explanation_afterAnswer_ok() {
        TestKid kid = givenKid();
        String questionCode = givenQuestion();
        loginAsKid(kid.kidUserId, "UTkid");

        practiceService.submitAnswer(answer("PS-" + unique(), questionCode, "11", ErrorCause.CALCULATION_ERROR));

        ExplanationVO vo = practiceService.explanation(questionCode, null);
        assertThat(vo).isNotNull();
        assertThat(vo.getSections()).isNotEmpty();
        assertThat(vo.getWrongPath()).isNotBlank();
    }

    // ------------------------------------------------------------------ TC-08-05 / TC-11-14

    @Test
    @DisplayName("TC-08-05 / TC-11-14 判定来源受限：只出现 rule / rule+model，数据库同样受限")
    void judgeSource_onlyRuleOrRulePlusModel() {
        TestKid kid = givenKid();
        String questionCode = givenQuestion();
        loginAsKid(kid.kidUserId, "UTkid");

        AnswerResultVO result = practiceService.submitAnswer(answer("PS-" + unique(), questionCode, "12.5", null));

        assertThat(result.getSource()).isIn(JudgeSource.RULE.getCode(), JudgeSource.RULE_AND_MODEL.getCode());
        assertThat(result.getSource()).isNotEqualTo("ai");
        assertThat(result.getSource()).isNotEqualTo("model");

        // ⚠ 这里的 SQL **不能写死月份表名**：
        // 生产侧按服务端时钟拼表名（study_practice_record_yyyyMM，见 StudyPracticeServiceImpl#practiceRecordTable），
        // 2026-11-01 跨月当天，写死 study_practice_record_202610 就查不到行 → NPE（真实踩过一次）。
        // 改为把库里真实存在的全部分表逐个查一遍，逐行判定 —— 比只查一张表更严。
        List<String> tables = practiceRecordTables();
        assertThat(tables).as("扫描有效性：库里必须存在作答记录分表").isNotEmpty();
        List<String> dbSources = new ArrayList<>();
        for (String table : tables) {
            for (Map<String, Object> row : queryList(
                    "SELECT judge_source FROM " + table + " WHERE child_id = ?", kid.childId)) {
                Object source = row.get("judge_source");
                if (source != null) {
                    dbSources.add(String.valueOf(source));
                }
            }
        }
        assertThat(dbSources).as("孩子必须已有作答记录落库（分表都查过一遍）").isNotEmpty();
        assertThat(dbSources)
                .as("落库的 judge_source 只允许 rule / rule+model（数据库层同样受限）")
                .allMatch(s -> JudgeSource.RULE.getCode().equals(s) || JudgeSource.RULE_AND_MODEL.getCode().equals(s));
    }

    // ------------------------------------------------------------------ TC-08-06

    @Test
    @DisplayName("TC-08-06 作答幂等：同一 sessionId+contentId 提交 3 次，只判定 1 次、只入池 1 条、结果一致")
    void submitAnswer_idempotent() {
        TestKid kid = givenKid();
        String questionCode = givenQuestion();
        loginAsKid(kid.kidUserId, "UTkid");

        AnswerCommand cmd = answer("PS-IDEMPOTENT-" + unique(), questionCode, "12", ErrorCause.CALCULATION_ERROR);
        AnswerResultVO first = practiceService.submitAnswer(cmd);
        AnswerResultVO second = practiceService.submitAnswer(cmd);
        AnswerResultVO third = practiceService.submitAnswer(cmd);

        assertThat(first.getCorrect()).isFalse();
        assertThat(second.getCorrect()).isEqualTo(first.getCorrect());
        assertThat(third.getCorrect()).isEqualTo(first.getCorrect());
        assertThat(second.getBasis()).isEqualTo(first.getBasis());
        assertThat(second.getSource()).isEqualTo(first.getSource());
        assertThat(second.getErrorId()).isEqualTo(first.getErrorId());
        assertThat(second.getNextReviewAt()).isEqualTo(first.getNextReviewAt());
        assertThat(second.getDuplicated()).isTrue();

        // 作答记录只有 1 条，错题池只有 1 条
        assertThat(count("SELECT COUNT(*) FROM " + recordTable()
                + " WHERE child_id = ?", kid.childId)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM study_error_pool WHERE child_id = ?", kid.childId)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ TC-08-10

    @Test
    @DisplayName("TC-08-10 数值判定容差边界：容差 0.05，边界内判对、边界外判错")
    void numericTolerance_boundary() {
        TestKid kid = givenKid();
        String questionCode = givenQuestion();
        loginAsKid(kid.kidUserId, "UTkid");

        // 差值 0 / 0.04 / 0.04 均小于容差 0.05 → 判对（judge 域口径：差值 < 容差才算对）
        assertThat(submit(questionCode, "12.5", null).getCorrect()).isTrue();
        assertThat(submit(questionCode, "12.54", null).getCorrect()).isTrue();
        assertThat(submit(questionCode, "12.46", null).getCorrect()).isTrue();

        // 差值 0.10 超出容差 → 判错，并入错题池
        AnswerResultVO wrong = submit(questionCode, "12.60", ErrorCause.CALCULATION_ERROR);
        assertThat(wrong.getCorrect()).isFalse();
        assertThat(wrong.getErrorId()).isNotBlank();
        assertThat(count("SELECT COUNT(*) FROM study_error_pool WHERE child_id = ?", kid.childId)).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-08-10 数值判定容差边界：差值恰好等于容差 0.05 时判错")
    void numericTolerance_exactBoundaryIsWrong() {
        TestKid kid = givenKid();
        String questionCode = givenQuestion();
        loginAsKid(kid.kidUserId, "UTkid");

        // 差值 0.05 恰好等于容差 → 判错（不是「≤ 容差」）
        AnswerResultVO boundary = submit(questionCode, "12.55", ErrorCause.CALCULATION_ERROR);
        assertThat(boundary.getCorrect()).isFalse();
        assertThat(boundary.getErrorId()).isNotBlank();
        assertThat(count("SELECT COUNT(*) FROM study_error_pool WHERE child_id = ?", kid.childId)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ TC-08-11

    @Test
    @DisplayName("TC-08-11 判定依据非空且人可读")
    void judgeBasis_alwaysPresent() {
        TestKid kid = givenKid();
        String questionCode = givenQuestion();
        loginAsKid(kid.kidUserId, "UTkid");

        AnswerResultVO right = submit(questionCode, "12.5", null);
        AnswerResultVO wrong = submit(questionCode, "11", ErrorCause.TIME_SHORTAGE);

        assertThat(right.getBasis()).isNotBlank();
        assertThat(wrong.getBasis()).isNotBlank();
        assertThat(right.getBasis()).contains("容差");
    }

    // ------------------------------------------------------------------ TC-11-03

    @Test
    @DisplayName("TC-11-03 错因强制：答错未选错因提交被拒（4001）")
    void wrongAnswerWithoutCause_rejected() {
        TestKid kid = givenKid();
        String questionCode = givenQuestion();
        loginAsKid(kid.kidUserId, "UTkid");

        assertThatThrownBy(() -> practiceService.submitAnswer(
                answer("PS-" + unique(), questionCode, "11", null)))
                .isInstanceOf(JeecgBootException.class)
                .satisfies(e -> assertThat(((JeecgBootException) e).getErrCode())
                        .isEqualTo(StudyErrorCode.MISSING_ERROR_CAUSE));

        // 被拒的作答不落库，也不入错题池
        assertThat(count("SELECT COUNT(*) FROM " + recordTable() + " WHERE child_id = ?", kid.childId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM study_error_pool WHERE child_id = ?", kid.childId)).isZero();
    }

    // ------------------------------------------------------------------ 数据范围

    @Test
    @DisplayName("数据范围：家长身份不能走学习端练习接口（4010）")
    void parentCannotPractice() {
        TestKid kid = givenKid();
        givenQuestion();
        loginAsParent(kid.parentId, "UTparent");

        assertThatThrownBy(() -> practiceService.nextQuestion("math"))
                .isInstanceOf(JeecgBootException.class)
                .satisfies(e -> assertThat(((JeecgBootException) e).getErrCode())
                        .isEqualTo(StudyErrorCode.FORBIDDEN_SCOPE));
    }

    // ------------------------------------------------------------------ 工具

    @Override
    protected void cleanTestData() {
        try {
            jdbc.update("DELETE FROM " + recordTable()
                    + " WHERE child_id IN (SELECT id FROM study_child WHERE nickname LIKE 'UT%')");
            jdbc.update("DELETE FROM study_review_schedule WHERE child_id IN"
                    + " (SELECT id FROM study_child WHERE nickname LIKE 'UT%')");
            jdbc.update("DELETE FROM study_error_pool WHERE child_id IN"
                    + " (SELECT id FROM study_child WHERE nickname LIKE 'UT%')");
            jdbc.update("DELETE FROM study_question WHERE question_code LIKE 'UTQST%'");
        } catch (Exception e) {
            System.out.println("[StudyPracticeServiceTest] 清理测试数据失败：" + e.getMessage());
        }
        super.cleanTestData();
    }

    /**
     * 作答记录所在月表名。
     */
    private String recordTable() {
        return "study_practice_record_" + YearMonth.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
    }

    /**
     * 造一个「家庭 → 孩子」最小闭环，并以孩子身份登录。
     */
    private TestKid givenKid() {
        String parentName = "ut_p_" + unique();
        String parentId = givenParentAccount(parentName);
        loginAsParent(parentId, parentName);
        familyService.create("UT家庭" + unique());
        ChildCreateVO child = childService.create(new ChildCreateRequest().setNickname("UT娃" + unique()));
        Map<String, Object> row = queryOne("SELECT user_id FROM study_child WHERE id = ?", child.getChildId());
        return new TestKid(parentId, child.getChildId(),
                row == null ? null : String.valueOf(row.get("user_id")));
    }

    /**
     * 造一道数值题：标准答案 12.5，容差 0.05。
     */
    private String givenQuestion() {
        String questionCode = "UTQST-" + unique();
        jdbc.update("INSERT INTO study_question (id, question_code, question_type, subject_code, knowledge_point_code,"
                + " stem, answer, answer_tolerance, analysis, difficulty, source_type, copyright_type, status,"
                + " del_flag, create_time)"
                + " VALUES (?, ?, 'NUMERIC', 'MA', ?, '两车相遇求时间', ?, ?, '先求速度和，再算相遇时间', 3,"
                + " 'SELF', 'SELF', 1, 0, NOW())",
                com.baomidou.mybatisplus.core.toolkit.IdWorker.get32UUID(), questionCode,
                "UTKPT-" + unique(), ANSWER, TOLERANCE);
        return questionCode;
    }

    private AnswerCommand answer(String sessionId, String contentId, String value, String errorCause) {
        AnswerCommand cmd = new AnswerCommand();
        cmd.setSessionId(sessionId);
        cmd.setContentId(contentId);
        cmd.setAnswer(value);
        cmd.setDurationMs(30000);
        cmd.setErrorCause(errorCause);
        return cmd;
    }

    /**
     * 每个用例用独立会话，避免命中幂等分支。
     */
    private AnswerResultVO submit(String contentId, String value, String errorCause) {
        return practiceService.submitAnswer(answer("PS-" + unique(), contentId, value, errorCause));
    }

    private static void collectKeys(JsonNode node, List<String> keys) {
        if (node == null) {
            return;
        }
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            keys.add(names.next());
        }
        Iterator<JsonNode> children = node.elements();
        while (children.hasNext()) {
            collectKeys(children.next(), keys);
        }
    }

    /**
     * 测试用的孩子上下文。
     */
    private static final class TestKid {
        private final String parentId;
        private final String childId;
        private final String kidUserId;

        private TestKid(String parentId, String childId, String kidUserId) {
            this.parentId = parentId;
            this.childId = childId;
            this.kidUserId = kidUserId;
        }
    }
}
