package org.jeecg.modules.study.redline;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.jeecg.modules.study.common.constant.StudyErrorCode;
import org.jeecg.modules.study.common.enums.JudgeSource;
import org.jeecg.modules.study.family.service.StudyFamilyService;
import org.jeecg.modules.study.family.vo.FamilyCreateVO;
import org.jeecg.modules.study.family.vo.FamilyMemberVO;
import org.jeecg.modules.study.support.StudyTestSupport;

/**
 * **24 条红线断言的可执行套件**（T-V0-13）。
 *
 * <p>红线是**发布否决项**，所以断言必须落在**可执行代码**里，而不是文档里。
 * 本类把《测试用例》§10 的 24 条断言逐条实现为 JUnit 用例：</p>
 *
 * <ul>
 *   <li><b>静态扫描</b>（源码 / 前端文案 / 依赖 / 隐藏式实现）→ 见 {@link RedLineScanner}；</li>
 *   <li><b>反射扫描</b>（端点响应 VO 的字段名）→ 依据真实的 Controller 签名与权限注解，</li>
 *   <li><b>数据库扫描</b>（枚举取值、列名）→ 真实连 {@code jeecg-study} 库；</li>
 *   <li><b>运行时断言</b>（家长互不可见）→ 真实调用服务。</li>
 * </ul>
 *
 * <p>与《红线断言规格》§4 的登记表一一对应。</p>
 *
 * @author jeecg-study
 */
class RedLineAssertionTest extends StudyTestSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private StudyFamilyService familyService;

    /** 强违规排名词：任何位置都不允许出现在用户可见文案里 */
    private static final List<String> RANK_WORDS =
            Arrays.asList("排名", "排行", "百分位", "超过…同学", "超过同学", "名次");

    /** 家庭内比较措辞（R-04 强化） */
    private static final List<String> FAMILY_COMPARE_WORDS =
            Arrays.asList("谁的孩子更", "比上次", "比上个月", "比另一个孩子", "比别的孩子", "更进步");

    /** 家长端响应里绝不能出现的字段关键词 */
    private static final List<String> PARENT_FORBIDDEN_FIELDS =
            Arrays.asList("answer", "analysis", "correctanswer", "explanation", "advice", "suggestion",
                    "teaching", "tutoring", "rank", "percentile", "ranking");

    // ================================================================= R-01

    @Test
    @DisplayName("R01-1 取题接口不返回答案字段")
    void r01_1_nextEndpointHasNoAnswerField() {
        Set<Class<?>> nextVos = new LinkedHashSet<>();
        nextVos.addAll(RedLineScanner.endpointVosByPath("/next"));
        nextVos.addAll(RedLineScanner.endpointVosByPath("/question"));
        nextVos.addAll(RedLineScanner.endpointVosByPath("/stem"));
        nextVos.addAll(RedLineScanner.endpointVosByPath("/item"));

        List<String> hits = new ArrayList<>();
        for (Class<?> vo : nextVos) {
            if (!RedLineScanner.isResponseVo(vo)) {
                continue;
            }
            for (String field : RedLineScanner.fieldNames(vo)) {
                if (field.contains("answer") || field.contains("analysis")) {
                    hits.add(vo.getSimpleName() + "#" + field);
                }
            }
        }
        assertThat(hits).as("取题端点的响应 VO 不得含 answer / analysis 字段").isEmpty();
    }

    @Test
    @DisplayName("R01-2 未作答不得取解析（4003）")
    void r01_2_explanationGuardedBy4003() {
        assertThat(StudyErrorCode.PRACTICE_ORDER_VIOLATION).isEqualTo(4003);

        // 守卫必须落在「真正产出解析」的实现层。
        // ⚠ 不能要求练习域**每个**文件都带这个标记：实体 / VO / Mapper 与守序无关，
        //   那种口径既不成立也逼不出更好的代码。
        List<String> hits = new ArrayList<>();
        boolean guardFound = false;
        for (java.nio.file.Path file : RedLineScanner.javaSources()) {
            String rel = RedLineScanner.moduleRelative(file);
            if (!rel.startsWith("practice/")) {
                continue;
            }
            String content = RedLineScanner.read(file);
            // 判据：文件里**引用了**解析 VO（不是文件名里带 VO）
            if (!content.contains("ExplanationVO") && !content.contains("ExplainVO")) {
                continue;
            }
            if (!rel.startsWith("practice/service/impl/")) {
                // 接口 / 控制器 / VO 不产出解析，只需确认它们走 service（不自己解析）
                if (rel.startsWith("practice/controller/") && content.contains("Mapper")
                        && !content.contains("explanation(")) {
                    hits.add(rel + " 疑似绕过 service 直连 mapper 取解析");
                }
                continue;
            }
            // 实现类：explanation 方法体内必须有 4003 守卫
            int at = content.indexOf("explanation(");
            if (at < 0) {
                hits.add(rel + " 产出解析却没有 explanation 方法");
                continue;
            }
            String body = content.substring(at, Math.min(content.length(), at + 2000));
            if (body.contains("PRACTICE_ORDER_VIOLATION")) {
                guardFound = true;
            } else {
                hits.add(rel + " 的 explanation 方法内没有 4003 守卫");
            }
        }
        assertThat(guardFound).as("练习域必须有 service 实现层守住 4003（未作答禁止取解析）").isTrue();
        assertThat(hits).as("练习域必须存在 4003（未作答禁止取解析）守卫").isEmpty();
    }

    @Test
    @DisplayName("R01-3 解析区不在 DOM（学习端不预置解析容器）")
    void r01_3_noExplanationContainerInLearningDom() {
        List<String> hits = new ArrayList<>();
        for (java.nio.file.Path file : RedLineScanner.projectFrontendFiles()) {
            String path = RedLineScanner.frontendRelative(file);
            String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
            if (name.contains("answer") || name.contains("analysis") || name.contains("explanation")
                    || name.contains("答案") || name.contains("解析")) {
                hits.add(path);
            }
        }
        assertThat(hits).as("学习端不得预置解析 / 答案容器").isEmpty();
    }

    @Test
    @DisplayName("R01-4 五个练习页一致：都不存在答案入口")
    void r01_4_fivePracticePagesConsistent() {
        List<String> hits = new ArrayList<>();
        for (java.nio.file.Path file : RedLineScanner.projectFrontendFiles()) {
            String path = RedLineScanner.frontendRelative(file);
            String content = RedLineScanner.read(file).toLowerCase(Locale.ROOT);
            boolean looksLikePracticePage = content.contains("/practice") || content.contains("练习");
            boolean hasAnswerEntry = content.contains("正确答案") || content.contains("查看答案")
                    || content.contains("showanswer") || content.contains("revealanswer");
            if (looksLikePracticePage && hasAnswerEntry) {
                hits.add(path);
            }
        }
        assertThat(hits).as("练习页不得提供「查看答案」入口").isEmpty();
    }

    @Test
    @DisplayName("R01-5 无隐藏式实现（答案不得用 v-show / display:none 藏起来）")
    void r01_5_noHiddenAnswerImplementation() {
        assertThat(RedLineScanner.scanHiddenAnswerImplementation())
                .as("不得用 v-show / display:none 把答案藏起来（必须是「不渲染」而不是「藏起来」）")
                .isEmpty();
    }

    // ================================================================= R-02

    @Test
    @DisplayName("R02-1 判定层对 AI 客户端零依赖")
    void r02_1_judgeLayerHasNoAiDependency() {
        assertThat(RedLineScanner.scanJudgeAiDependency())
                .as("judge 包不得依赖任何 AI 客户端")
                .isEmpty();
    }

    @Test
    @DisplayName("R02-2 数据层 judge_source 只出现 rule / rule+model")
    void r02_2_judgeSourceEnumInDatabase() {
        List<String> values = jdbc.queryForList(
                "SELECT DISTINCT judge_source FROM study_error_pool WHERE judge_source IS NOT NULL",
                String.class);
        for (String value : values) {
            assertThat(JudgeSource.isValid(value))
                    .as("judge_source 非法取值：" + value).isTrue();
        }
    }

    @Test
    @DisplayName("R02-3 接口层必带 judgeSource，且家长端不泄露答案 / 讲解")
    void r02_3_judgeResultCarriesSource() {
        List<String> fields = RedLineScanner.fieldNames(
                org.jeecg.modules.study.judge.engine.JudgeResult.class);
        assertThat(fields).contains("judgesource");
        assertThat(fields).contains("basis");

        // 家长端响应不得下发答案 / 讲解 / 辅导建议
        assertThat(parentResponseForbiddenFieldHits())
                .as("家长端响应 VO 不得含 answer / analysis / explanation / 辅导建议类字段")
                .isEmpty();
    }

    @Test
    @DisplayName("R02-4 写入 judge_source='model' 被拒")
    void r02_4_rejectPureModelJudgeSource() {
        assertThat(JudgeSource.of("model")).isNull();
        assertThat(JudgeSource.isValid("model")).isFalse();
        assertThat(JudgeSource.isValid("rule")).isTrue();
        assertThat(JudgeSource.isValid("rule+model")).isTrue();

        // 落库前必须走枚举校验
        boolean hasGuard = RedLineScanner.javaSources().stream()
                .filter(p -> p.toString().replace('\\', '/').contains("/error/"))
                .anyMatch(p -> {
                    String content = RedLineScanner.read(p);
                    return content.contains("JudgeSource.isValid") || content.contains("JudgeSource.of");
                });
        assertThat(hasGuard).as("错题入池前必须用 JudgeSource 校验 judge_source").isTrue();
    }

    @Test
    @DisplayName("R02-5 变式答案可验算，generatedBy 不出现纯 MODEL")
    void r02_5_variantGeneratedByNeverPureModel() {
        List<String> values = jdbc.queryForList(
                "SELECT DISTINCT generated_by FROM study_question_variant WHERE generated_by IS NOT NULL",
                String.class);
        for (String value : values) {
            assertThat(value.toUpperCase(Locale.ROOT))
                    .as("generatedBy 出现纯 MODEL：" + value)
                    .isIn("RULE", "RULE+MODEL");
        }
    }

    @Test
    @DisplayName("R02-6 申诉通道可用（存在提交反馈 / 申诉的端点）")
    void r02_6_appealChannelExists() {
        boolean hasAppealEndpoint = RedLineScanner.endpointPaths().stream()
                .anyMatch(p -> p.toLowerCase(Locale.ROOT).contains("feedback")
                        || p.toLowerCase(Locale.ROOT).contains("appeal")
                        || p.contains("反馈") || p.contains("申诉"));
        assertThat(hasAppealEndpoint).as("必须存在「提交我不同意 / 申诉」的端点").isTrue();
    }

    @Test
    @DisplayName("R02-7 降级不中断（判定层不依赖外部网络）")
    void r02_7_judgeLayerHasNoExternalCall() {
        List<String> banned = Arrays.asList("resttemplate", "webclient", "okhttp3", "httpclient",
                "openai", "dashscope", "langchain4j");
        List<String> hits = new ArrayList<>();
        for (java.nio.file.Path file : RedLineScanner.javaSources()) {
            String path = file.toString().replace('\\', '/');
            if (!path.contains("/judge/")) {
                continue;
            }
            String content = RedLineScanner.read(file).toLowerCase(Locale.ROOT);
            for (String token : banned) {
                if (content.contains(token)) {
                    hits.add(path + " → " + token);
                }
            }
        }
        assertThat(hits).as("判定层不得依赖外部网络 / AI 服务（AI 超时不得影响判定）").isEmpty();
    }

    // ================================================================= R-03

    @Test
    @DisplayName("R03-1 休息阻断生效（服务端按阈值下发阻断标志）")
    void r03_1_restBlockIsServerDriven() {
        boolean hasRestRequired = RedLineScanner.javaSources().stream()
                .filter(p -> p.toString().replace('\\', '/').contains("/eye/"))
                .anyMatch(p -> RedLineScanner.read(p).contains("restRequired"));
        assertThat(hasRestRequired).as("护眼域必须由服务端下发 restRequired 阻断标志").isTrue();
    }

    @Test
    @DisplayName("R03-2 遮罩不自动消失（清除只能由显式确认触发）")
    void r03_2_restNeverAutoClears() {
        boolean hasConfirm = RedLineScanner.javaSources().stream()
                .filter(p -> p.toString().replace('\\', '/').contains("/eye/"))
                .anyMatch(p -> {
                    String content = RedLineScanner.read(p);
                    return content.contains("restConfirmed") || content.contains("rest_confirmed");
                });
        assertThat(hasConfirm).as("必须有「休息确认」这一显式清除通道").isTrue();

        boolean hasScheduler = RedLineScanner.javaSources().stream()
                .filter(p -> p.toString().replace('\\', '/').contains("/eye/"))
                .anyMatch(p -> RedLineScanner.read(p).contains("@Scheduled"));
        assertThat(hasScheduler).as("不得用定时任务自动清除休息遮罩").isFalse();
    }

    @Test
    @DisplayName("R03-3 用眼时长以服务端心跳累计，不信任客户端上报")
    void r03_3_eyeUsageIsServerAccumulated() {
        List<String> hits = new ArrayList<>();
        for (java.nio.file.Path file : RedLineScanner.javaSources()) {
            String path = file.toString().replace('\\', '/');
            if (!path.contains("/eye/")) {
                continue;
            }
            String content = RedLineScanner.read(file);
            if (!content.contains("lastHeartbeat") && !content.contains("last_heartbeat")) {
                hits.add(path + " 缺少服务端上次心跳时间");
            }
            String lower = content.toLowerCase(Locale.ROOT);
            if (lower.contains("clientreported") || lower.contains("client_report")
                    || lower.contains("reportedminutes") || lower.contains("clientminutes")) {
                hits.add(path + " 疑似信任客户端上报时长");
            }
        }
        assertThat(hits).as("用眼时长必须由服务端心跳差值累计").isEmpty();
    }

    @Test
    @DisplayName("R03-4 蓝光过滤开关生效（后端持久化 blueLightOn）")
    void r03_4_blueLightFilterPersisted() {
        boolean hasField = RedLineScanner.javaSources().stream()
                .filter(p -> p.toString().replace('\\', '/').contains("/eye/"))
                .anyMatch(p -> {
                    String content = RedLineScanner.read(p);
                    return content.contains("blueLightOn") || content.contains("blue_light_on");
                });
        assertThat(hasField).as("蓝光过滤状态必须落库，刷新后保持").isTrue();
    }

    @Test
    @DisplayName("R03-5 孩子端不显示用眼时长")
    void r03_5_kidEndpointsHideEyeMinutes() {
        List<String> hits = new ArrayList<>();
        for (Class<?> vo : RedLineScanner.kidEndpointVos()) {
            for (String field : RedLineScanner.fieldNames(vo)) {
                if (field.contains("activeminutes") || field.contains("eyeminutes")
                        || field.contains("useminutes") || field.contains("eyeminute")) {
                    hits.add(vo.getSimpleName() + "#" + field);
                }
            }
        }
        assertThat(hits).as("孩子端只显示状态图标，不出现用眼时长数字").isEmpty();
    }

    @Test
    @DisplayName("R03-6 家长端可见用眼时长与休息次数")
    void r03_6_parentSeesEyeUsage() {
        List<String> columns = jdbc.queryForList(
                "SELECT COLUMN_NAME FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'study_eye_usage'",
                String.class);
        assertThat(columns).contains("active_minutes", "rest_count");
    }

    // ================================================================= R-04

    @Test
    @DisplayName("R04-1 无排名字段（表结构 + 响应 VO）")
    void r04_1_noRankField() {
        List<String> columns = jdbc.queryForList(
                "SELECT TABLE_NAME || '.' || COLUMN_NAME FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME LIKE 'study\\_%' "
                        + "AND (COLUMN_NAME LIKE '%rank%' OR COLUMN_NAME LIKE '%percentile%')",
                String.class);
        assertThat(columns).as("study_* 表不得有 rank / percentile 列").isEmpty();

        List<String> hits = new ArrayList<>();
        for (Class<?> vo : RedLineScanner.voClasses()) {
            if (!RedLineScanner.isResponseVo(vo)) {
                continue;
            }
            for (String field : RedLineScanner.fieldNames(vo)) {
                if (field.contains("rank") || field.contains("percentile")) {
                    hits.add(vo.getSimpleName() + "#" + field);
                }
            }
        }
        assertThat(hits).as("响应 VO 不得有 rank / percentile 字段").isEmpty();
    }

    @Test
    @DisplayName("R04-2 家长端响应不含排名类数值字段")
    void r04_2_parentResponseHasNoRankingNumbers() {
        List<String> hits = new ArrayList<>();
        for (Class<?> vo : RedLineScanner.parentEndpointVos()) {
            for (String field : RedLineScanner.fieldNames(vo)) {
                if (field.contains("rank") || field.contains("percentile") || field.contains("ranking")) {
                    hits.add(vo.getSimpleName() + "#" + field);
                }
            }
        }
        assertThat(hits).as("家长端响应不得含排名类数值字段").isEmpty();
    }

    @Test
    @DisplayName("R04-3 无排名文案（全站文案扫描）")
    void r04_3_noRankingCopy() {
        List<String> backendHits = RedLineScanner.scanJavaCopy(RANK_WORDS, null);
        List<String> frontendHits = RedLineScanner.scanFrontendCopy(RANK_WORDS, true);
        List<String> globalHits = RedLineScanner.scanFrontendCopy(
                Arrays.asList("百分位", "排行", "谁的孩子更", "比上次"), false);

        assertThat(backendHits).as("后端文案不得出现排名类措辞").isEmpty();
        assertThat(frontendHits).as("学习端 / 家长端文案不得出现排名类措辞").isEmpty();
        assertThat(globalHits).as("前端不得出现强违规比较措辞").isEmpty();
    }

    @Test
    @DisplayName("R04-4 学习端无比较（无分数 / 名次 / 排行入口）")
    void r04_4_learningSideHasNoRanking() {
        List<String> hits = new ArrayList<>();
        for (Class<?> vo : RedLineScanner.kidEndpointVos()) {
            for (String field : RedLineScanner.fieldNames(vo)) {
                if (field.contains("rank") || field.contains("percentile") || field.contains("totalScore")
                        || field.contains("score") || field.contains("points")) {
                    hits.add(vo.getSimpleName() + "#" + field);
                }
            }
        }
        assertThat(hits).as("学习端不得出现分数 / 名次 / 排行类字段").isEmpty();
    }

    @Test
    @DisplayName("R04-5 家长之间互不可见")
    void r04_5_parentsCannotSeeEachOther() throws Exception {
        // 运行时：家长 A 的成员清单里不得出现家长 B 的任何标识
        String nameA = "ut_r04a_" + unique();
        String userA = givenParentAccount(nameA);
        String nameB = "ut_r04b_" + unique();
        String userB = givenParentAccount(nameB);

        loginAsParent(userA, nameA);
        FamilyCreateVO family = familyService.create("UT家庭");
        familyService.addMember(nameB, "爸爸");

        List<FamilyMemberVO> members = familyService.listMembers(null);
        String json = MAPPER.writeValueAsString(members);
        assertThat(json).doesNotContain(userB);
        assertThat(json).doesNotContain(nameB);

        // 结构层：家庭成员视图不得暴露他人标识字段
        List<String> fields = RedLineScanner.fieldNames(FamilyMemberVO.class);
        assertThat(fields).noneMatch(f -> f.equals("userid") || f.equals("username")
                || f.equals("realname") || f.equals("nickname") || f.equals("avatar"));

        assertThat(family.getFamilyId()).isNotBlank();
    }

    @Test
    @DisplayName("R04-6 无家庭内比较措辞")
    void r04_6_noFamilyComparisonCopy() {
        List<String> backendHits = RedLineScanner.scanJavaCopy(FAMILY_COMPARE_WORDS, null);
        List<String> frontendHits = RedLineScanner.scanFrontendCopy(FAMILY_COMPARE_WORDS, true);

        assertThat(backendHits).as("后端文案不得出现家庭内比较措辞").isEmpty();
        assertThat(frontendHits).as("前端文案不得出现家庭内比较措辞").isEmpty();
    }

    /**
     * 家长端响应 VO 不得出现「答案 / 讲解 / 辅导建议」类字段。
     *
     * <p>本方法被 R-04 相关断言复用，独立成方法便于其它测试类引用。</p>
     *
     * @return 命中描述
     */
    static List<String> parentResponseForbiddenFieldHits() {
        List<String> hits = new ArrayList<>();
        for (Class<?> vo : RedLineScanner.parentEndpointVos()) {
            for (String field : RedLineScanner.fieldNames(vo)) {
                for (String forbidden : PARENT_FORBIDDEN_FIELDS) {
                    if (field.contains(forbidden)) {
                        hits.add(vo.getSimpleName() + "#" + field);
                    }
                }
            }
        }
        return hits;
    }

    /**
     * 端点清单（调试用）。
     *
     * @return 权限码 → 响应 VO 列表
     */
    static Map<String, List<String>> endpointInventory() {
        return Map.of(
                "parent", RedLineScanner.parentEndpointVos().stream()
                        .map(Class::getSimpleName).collect(Collectors.toList()),
                "kid", RedLineScanner.kidEndpointVos().stream()
                        .map(Class::getSimpleName).collect(Collectors.toList()));
    }
}
