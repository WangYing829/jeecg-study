package org.jeecg.modules.study.parent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.study.common.constant.StudyErrorCode;
import org.jeecg.modules.study.family.service.StudyChildService;
import org.jeecg.modules.study.family.service.StudyFamilyService;
import org.jeecg.modules.study.family.vo.ChildCreateRequest;
import org.jeecg.modules.study.family.vo.ChildCreateVO;
import org.jeecg.modules.study.parent.controller.StudyParentReportController;
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
import org.jeecg.modules.study.support.StudyTestSupport;

/**
 * 家长端只读报表自动化测试（F-PAR-01 / R-04）。
 *
 * <p>覆盖 TC-07-01 … TC-07-08、TC-11-10，以及 R04-1 … R04-6 在家长端的落点。</p>
 *
 * @author jeecg-study
 */
class StudyParentReportServiceTest extends StudyTestSupport {

    /** 家长端响应 VO 里绝不允许出现的字段关键词 */
    private static final List<String> FORBIDDEN_FIELDS = Arrays.asList(
            "answer", "analysis", "correctanswer", "explanation", "advice", "suggestion",
            "teaching", "tutoring", "rank", "percentile", "ranking");

    /** 家长端响应 VO 清单（只读边界的检查对象） */
    private static final List<Class<?>> PARENT_VOS = Arrays.asList(
            ParentOverviewVO.class, ParentEyeUsageVO.class, ParentStreakVO.class, ParentMasteryVO.class,
            ParentErrorCauseVO.class, ParentWeakPointVO.class, ParentWritingTrendVO.class,
            ParentWeeklyVO.class, ParentDailyItemVO.class, ParentErrorReportVO.class);

    /** 用户可见文案里不允许出现的横向对比措辞 */
    private static final List<String> FORBIDDEN_COPY = Arrays.asList(
            "排名", "排行", "百分位", "名次", "超过同学", "超过…同学",
            "谁的孩子更", "比上次", "比上个月", "比另一个孩子", "比别的孩子", "更进步");

    @Autowired
    private StudyParentReportService reportService;

    @Autowired
    private StudyFamilyService familyService;

    @Autowired
    private StudyChildService childService;

    private String parentName;
    private String parentId;
    private String childId;
    private String childCode;

    @BeforeEach
    void prepareData() {
        parentName = "ut_par_p_" + unique();
        parentId = givenParentAccount(parentName);
        loginAsParent(parentId, parentName);
        familyService.create("UT家长报表家庭");

        ChildCreateRequest request = new ChildCreateRequest();
        request.setNickname("UT报表娃" + unique());
        request.setPin("1234");
        request.setRelationLabel("妈妈");
        ChildCreateVO child = childService.create(request);
        childId = child.getChildId();
        childCode = child.getChildCode();

        givenEyeUsage();
        givenErrorPool();
        givenWriting();
    }

    @AfterEach
    void cleanData() {
        jdbc.update("DELETE FROM study_error_pool WHERE child_id = ?", childId);
        jdbc.update("DELETE FROM study_writing WHERE child_id = ?", childId);
        jdbc.update("DELETE FROM study_eye_usage WHERE child_id = ?", childId);
        jdbc.update("DELETE FROM study_context WHERE child_id = ?", childId);
        jdbc.update("DELETE FROM study_knowledge_point WHERE point_code LIKE 'UT-KPT-%'");
    }

    // ------------------------------------------------------------------ TC-07-05

    @Test
    @DisplayName("TC-07-05 家长可看趋势：用眼 / 连续天数 / 掌握度 / 错因 / 薄弱考点 / 写作趋势")
    void overview_containsAllTrends() {
        ParentOverviewVO vo = reportService.overview(childCode);

        assertThat(vo.getChildCode()).isEqualTo(childCode);
        assertThat(vo.getEyeUsage()).isNotNull();
        assertThat(vo.getEyeUsage().getTodayMinutes()).isEqualTo(30);
        assertThat(vo.getEyeUsage().getWeekMinutes()).isEqualTo(50);
        assertThat(vo.getEyeUsage().getRestCount()).isEqualTo(1);
        assertThat(vo.getEyeUsage().getRestConfirmedCount()).isEqualTo(1);

        assertThat(vo.getStreak()).isNotNull();
        assertThat(vo.getStreak().getDays()).isEqualTo(2);
        assertThat(vo.getStreak().getTodayCompleted()).isTrue();

        assertThat(vo.getMastery()).isNotNull();
        assertThat(vo.getMastery().getMastered()).isEqualTo(1);
        assertThat(vo.getMastery().getLearning()).isEqualTo(1);
        assertThat(vo.getMastery().getUnknown()).isEqualTo(1);
        assertThat(vo.getMastery().getFamiliar()).isEqualTo(0);

        assertThat(vo.getErrorTop()).isNotEmpty();
        assertThat(vo.getErrorTop().get(0).getCount()).isEqualTo(2);
        assertThat(vo.getErrorTop()).allSatisfy(item -> {
            assertThat(item.getCause()).isNotBlank();
            assertThat(item.getCauseName()).isNotBlank();
            assertThat(item.getCount()).isGreaterThan(0);
        });

        assertThat(vo.getWeakPoints()).isNotEmpty();
        assertThat(vo.getWeakPoints().get(0).getPointCode()).isNotBlank();
        assertThat(vo.getWeakPoints().get(0).getPointName()).isNotBlank();
        assertThat(vo.getWeakPoints().get(0).getErrorCount()).isEqualTo(2);

        assertThat(vo.getWritingTrend()).isNotEmpty();
        assertThat(vo.getWritingTrend().get(0).getWritingCount()).isEqualTo(1);

        // 周报：逐日明细
        ParentWeeklyVO weekly = reportService.weekly(childCode);
        assertThat(weekly.getDays()).hasSize(7);
        assertThat(weekly.getTotalEyeMinutes()).isEqualTo(50);
        assertThat(weekly.getActiveDays()).isEqualTo(2);
        assertThat(weekly.getTotalErrorCount()).isEqualTo(3);
        assertThat(weekly.getTotalWritingCount()).isEqualTo(1);

        // 错因分布
        ParentErrorReportVO errors = reportService.errors(childCode);
        assertThat(errors.getTotal()).isEqualTo(3);
        assertThat(errors.getCauses()).hasSize(2);
        assertThat(errors.getWeakPoints()).isNotEmpty();
    }

    // ------------------------------------------------------------------ TC-07-01 / 07-06

    @Test
    @DisplayName("TC-07-01 / TC-07-06 家长端报告接口全部是 GET，没有任何写操作")
    void parentEndpoints_areAllReadOnly() {
        List<Method> endpoints = parentEndpoints();
        assertThat(endpoints).as("家长端报告端点清单").hasSizeGreaterThanOrEqualTo(3);
        for (Method method : endpoints) {
            assertThat(method.getAnnotation(GetMapping.class))
                    .as(method.getName() + " 必须是 GET").isNotNull();
            assertThat(method.getAnnotation(PostMapping.class))
                    .as(method.getName() + " 不允许出现新增入口").isNull();
            assertThat(method.getAnnotation(PutMapping.class))
                    .as(method.getName() + " 不允许出现修改入口").isNull();
            assertThat(method.getAnnotation(DeleteMapping.class))
                    .as(method.getName() + " 不允许出现删除入口").isNull();
        }
    }

    // ------------------------------------------------------------------ TC-07-02 / 07-03 / 07-04

    @Test
    @DisplayName("TC-07-02 / TC-07-03 / TC-07-04 家长端响应没有答案、辅导建议与横向对比字段")
    void parentResponses_haveNoAnswerAdviceOrRankingFields() {
        for (Class<?> vo : PARENT_VOS) {
            for (Field field : vo.getDeclaredFields()) {
                String name = field.getName().toLowerCase();
                for (String forbidden : FORBIDDEN_FIELDS) {
                    assertThat(name)
                            .as(vo.getSimpleName() + "#" + field.getName() + " 不得包含「" + forbidden + "」")
                            .doesNotContain(forbidden);
                }
            }
        }
    }

    // ------------------------------------------------------------------ TC-11-10 / R04-1

    @Test
    @DisplayName("TC-11-10 / R04-1 数据表与家长端响应都没有横向对比类字段")
    void noRankingColumnInDatabase() {
        List<String> columns = jdbc.queryForList(
                "SELECT TABLE_NAME || '.' || COLUMN_NAME FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME LIKE 'study\\_%' "
                        + "AND (COLUMN_NAME LIKE '%rank%' OR COLUMN_NAME LIKE '%percentile%')",
                String.class);
        assertThat(columns).as("study_* 表不得有横向对比类列").isEmpty();
    }

    // ------------------------------------------------------------------ TC-07-07 / 07-08

    @Test
    @DisplayName("TC-07-07 / TC-07-08 家长端端点只挂家长角色与家长权限码，孩子进不来")
    void parentEndpoints_requireParentRole() {
        for (Method method : parentEndpoints()) {
            RequiresRoles roles = method.getAnnotation(RequiresRoles.class);
            assertThat(roles).as(method.getName() + " 必须声明角色").isNotNull();
            assertThat(roles.value()).as(method.getName() + " 角色必须是家长").contains("study_parent");

            RequiresPermissions permissions = method.getAnnotation(RequiresPermissions.class);
            assertThat(permissions).as(method.getName() + " 必须声明权限码").isNotNull();
            assertThat(permissions.value()).as(method.getName() + " 权限码必须在家长端命名空间下")
                    .allMatch(value -> value.startsWith("study:parent:"));
            // 家长端不持有任何作答类权限码 —— 家长无法作答（TC-07-07）
            assertThat(permissions.value()).as(method.getName() + " 不得持有作答类权限码")
                    .noneMatch(value -> value.contains("practice:do") || value.contains("practice:answer")
                            || value.contains("error:add") || value.contains("error:edit"));
        }
    }

    // ------------------------------------------------------------------ 数据范围

    @Test
    @DisplayName("家长 A 查看孩子 B 的报告 → 4010")
    void crossParentAccess_forbidden() {
        String nameB = "ut_par_b_" + unique();
        String userB = givenParentAccount(nameB);
        loginAsParent(userB, nameB);
        familyService.create("UT他家家庭");
        ChildCreateRequest request = new ChildCreateRequest();
        request.setNickname("UT他家娃" + unique());
        request.setPin("1234");
        ChildCreateVO other = childService.create(request);

        loginAsParent(parentId, parentName);
        assertThatThrownBy(() -> reportService.overview(other.getChildCode()))
                .isInstanceOf(JeecgBootException.class)
                .satisfies(e -> assertThat(((JeecgBootException) e).getErrCode())
                        .isEqualTo(StudyErrorCode.FORBIDDEN_SCOPE));

        // 不存在的 childCode 与越权返回同一种错误码（不用于资源探测）
        assertThatThrownBy(() -> reportService.overview("CHD-999999"))
                .isInstanceOf(JeecgBootException.class)
                .satisfies(e -> assertThat(((JeecgBootException) e).getErrCode())
                        .isEqualTo(StudyErrorCode.FORBIDDEN_SCOPE));
    }

    // ------------------------------------------------------------------ R04-5

    @Test
    @DisplayName("R04-5 家长之间互不可见：响应里不出现另一位家长的任何标识")
    void parentsCannotSeeEachOther() throws Exception {
        String nameB = "ut_par_b_" + unique();
        String userB = givenParentAccount(nameB);
        loginAsParent(userB, nameB);
        familyService.create("UT别家家庭");
        ChildCreateRequest request = new ChildCreateRequest();
        request.setNickname("UT别家娃" + unique());
        request.setPin("1234");
        ChildCreateVO other = childService.create(request);

        loginAsParent(parentId, parentName);
        ParentOverviewVO vo = reportService.overview(childCode);
        String json = new ObjectMapper().writeValueAsString(vo);

        assertThat(json).doesNotContain(userB);
        assertThat(json).doesNotContain(nameB);
        assertThat(json).doesNotContain(other.getChildCode());
    }

    // ------------------------------------------------------------------ R04-6

    @Test
    @DisplayName("R04-6 家长端文案没有家庭内横向对比措辞")
    void parentCopy_hasNoComparisonWording() {
        List<Path> sources = parentSources();
        assertThat(sources).as("家长端源码应可被扫描到").isNotEmpty();
        for (Path file : sources) {
            for (String literal : chineseStringLiterals(readSource(file))) {
                for (String word : FORBIDDEN_COPY) {
                    assertThat(literal)
                            .as(file + " 的文案含「" + word + "」")
                            .doesNotContain(word);
                }
            }
        }
    }

    // ------------------------------------------------------------------ 内部

    /**
     * 家长端控制器上的全部端点方法。
     */
    private List<Method> parentEndpoints() {
        List<Method> endpoints = new ArrayList<>();
        for (Method method : StudyParentReportController.class.getDeclaredMethods()) {
            if (method.getAnnotation(GetMapping.class) != null
                    || method.getAnnotation(PostMapping.class) != null
                    || method.getAnnotation(PutMapping.class) != null
                    || method.getAnnotation(DeleteMapping.class) != null) {
                endpoints.add(method);
            }
        }
        return endpoints;
    }

    private void givenEyeUsage() {
        String contextId = "ut-ctx-" + unique();
        // 归属日由数据库的 CURDATE() 决定，和服务端统计口径完全一致
        jdbc.update("INSERT INTO study_eye_usage (id, child_id, context_id, session_id, start_at,"
                + " active_minutes, heartbeat_count, rest_count, rest_confirmed, blue_light_on, usage_date,"
                + " del_flag, create_time, update_time)"
                + " VALUES (?,?,?,?,NOW(),?,?,?,?,?,CURDATE(),0,NOW(),NOW())",
                "ut-eye-" + unique(), childId, contextId, "SES-UT-" + unique(),
                30, 60, 1, 1, 1);
        jdbc.update("INSERT INTO study_eye_usage (id, child_id, context_id, session_id, start_at,"
                + " active_minutes, heartbeat_count, rest_count, rest_confirmed, blue_light_on, usage_date,"
                + " del_flag, create_time, update_time)"
                + " VALUES (?,?,?,?,NOW(),?,?,?,?,?,DATE_SUB(CURDATE(), INTERVAL 1 DAY),0,NOW(),NOW())",
                "ut-eye-" + unique(), childId, contextId, "SES-UT-" + unique(),
                20, 40, 0, 0, 0);
    }

    private void givenErrorPool() {
        String contextId = "ut-ctx-" + unique();
        String pointOne = "UT-KPT-" + unique();
        String pointTwo = "UT-KPT-" + unique();
        givenKnowledgePoint(pointOne, "行程问题·相遇");
        givenKnowledgePoint(pointTwo, "分数应用题");
        // 同一考点两条错题（掌握度分别是学习中 / 已掌握），另一个考点一条
        jdbc.update("INSERT INTO study_error_pool (id, child_id, error_code, context_id, source_module,"
                + " content_type, content_id, knowledge_point_code, error_cause, judge_source, mastery_state,"
                + " correct_streak, review_status, del_flag, create_time, update_time)"
                + " VALUES (?,?,?,?,?,?,?,?,?, 'rule', ?, 0, 1, 0, NOW(), NOW())",
                "ut-err-" + unique(), childId, "ERR-UT-" + unique(), contextId,
                "MATH", "QUESTION", "Q-UT-" + unique(), pointOne, "审题偏差", 2);
        jdbc.update("INSERT INTO study_error_pool (id, child_id, error_code, context_id, source_module,"
                + " content_type, content_id, knowledge_point_code, error_cause, judge_source, mastery_state,"
                + " correct_streak, review_status, del_flag, create_time, update_time)"
                + " VALUES (?,?,?,?,?,?,?,?,?, 'rule', ?, 0, 1, 0, NOW(), NOW())",
                "ut-err-" + unique(), childId, "ERR-UT-" + unique(), contextId,
                "MATH", "QUESTION", "Q-UT-" + unique(), pointOne, "审题偏差", 4);
        jdbc.update("INSERT INTO study_error_pool (id, child_id, error_code, context_id, source_module,"
                + " content_type, content_id, knowledge_point_code, error_cause, judge_source, mastery_state,"
                + " correct_streak, review_status, del_flag, create_time, update_time)"
                + " VALUES (?,?,?,?,?,?,?,?,?, 'rule', ?, 0, 1, 0, NOW(), NOW())",
                "ut-err-" + unique(), childId, "ERR-UT-" + unique(), contextId,
                "MATH", "QUESTION", "Q-UT-" + unique(), pointTwo, "方法不会", 1);
    }

    private void givenKnowledgePoint(String pointCode, String pointName) {
        // level / subject_code / effective_from 都没有默认值，必须显式给
        jdbc.update("INSERT INTO study_knowledge_point (id, point_code, name, level, subject_code,"
                + " effective_from, create_time) VALUES (?,?,?,1,'MA',CURDATE(),NOW())",
                "ut-kp-" + unique(), pointCode, pointName);
    }

    private void givenWriting() {
        jdbc.update("INSERT INTO study_writing (id, child_id, writing_code, layer, title, content,"
                + " subject_code, status, del_flag, create_time)"
                + " VALUES (?,?,?,?,?,?, 'CN', 1, 0, NOW())",
                "ut-wrt-" + unique(), childId, "WRT-UT-" + unique(), "DRAFT", "UT标题", "UT正文");
    }

    /**
     * 家长端主源码文件列表。
     */
    private List<Path> parentSources() {
        Path root = Paths.get("").toAbsolutePath()
                .resolve("src").resolve("main").resolve("java")
                .resolve("org").resolve("jeecg").resolve("modules").resolve("study").resolve("parent");
        if (!Files.isDirectory(root)) {
            return new ArrayList<>();
        }
        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> files = new ArrayList<>();
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .forEach(files::add);
            return files;
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    private String readSource(Path path) {
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * 源码里的中文字符串字面量（用户可见文案）。
     */
    private List<String> chineseStringLiterals(String content) {
        List<String> literals = new ArrayList<>();
        Matcher matcher = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(content);
        while (matcher.find()) {
            String raw = matcher.group(1);
            if (raw.codePoints().anyMatch(code -> code >= 0x4e00 && code <= 0x9fa5)) {
                literals.add(raw);
            }
        }
        return literals;
    }
}
