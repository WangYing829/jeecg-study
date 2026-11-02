package org.jeecg.modules.study.eye;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.jeecg.modules.study.eye.service.StudyEyeService;
import org.jeecg.modules.study.eye.vo.EyeHeartbeatRequest;
import org.jeecg.modules.study.eye.vo.EyeSessionVO;
import org.jeecg.modules.study.family.service.StudyChildService;
import org.jeecg.modules.study.family.service.StudyFamilyService;
import org.jeecg.modules.study.family.vo.ChildCreateRequest;
import org.jeecg.modules.study.family.vo.ChildCreateVO;
import org.jeecg.modules.study.support.StudyTestSupport;

/**
 * 护眼域自动化测试（R-03 护眼硬指标）。
 *
 * <p>覆盖 TC-07-09 … TC-07-15，以及 R03-1 … R03-5 在护眼域的落点。</p>
 *
 * <p><b>时间怎么模拟</b>：用眼时长由服务端按心跳基线累计，测试不能真的等 10 分钟，
 * 因此直接回拨库里的心跳基线（{@code update_time}）来模拟「经过了 N 秒」，
 * 被回拨的秒数只出现在测试代码里，<b>客户端接口没有上报时长的入口</b>。</p>
 *
 * @author jeecg-study
 */
class StudyEyeServiceTest extends StudyTestSupport {

    /** 一次心跳的名义间隔（秒） */
    private static final int HEARTBEAT_SECONDS = 30;

    @Autowired
    private StudyEyeService eyeService;

    @Autowired
    private StudyFamilyService familyService;

    @Autowired
    private StudyChildService childService;

    private String childId;
    private String childCode;

    @BeforeEach
    void prepareChild() {
        String parentName = "ut_eye_p_" + unique();
        String parentId = givenParentAccount(parentName);
        loginAsParent(parentId, parentName);
        familyService.create("UT护眼家庭");

        ChildCreateRequest request = new ChildCreateRequest();
        request.setNickname("UT护眼娃" + unique());
        request.setPin("1234");
        request.setRelationLabel("妈妈");
        ChildCreateVO child = childService.create(request);
        childId = child.getChildId();
        childCode = child.getChildCode();

        // 切换成孩子身份：护眼通道只服务学习端
        Map<String, Object> row = queryOne("SELECT user_id FROM study_child WHERE id = ?", childId);
        loginAsKid((String) row.get("user_id"), "ut_eye_kid");
    }

    @AfterEach
    void cleanEyeRows() {
        jdbc.update("DELETE FROM study_eye_usage WHERE child_id = ?", childId);
        jdbc.update("DELETE FROM study_context WHERE child_id = ?", childId);
    }

    // ------------------------------------------------------------------ TC-07-09

    @Test
    @DisplayName("TC-07-09 用眼时长由服务端心跳累计，切后台时段不计")
    void usage_accumulatesOnServerAndSkipsAwayGap() {
        EyeSessionVO session = eyeService.startSession();
        String sessionId = session.getSessionId();

        // 7 分钟有效学习：14 次心跳，每次间隔 30 秒
        for (int i = 0; i < 14; i++) {
            backdateHeartbeat(sessionId, HEARTBEAT_SECONDS);
            eyeService.heartbeat(sessionId, new EyeHeartbeatRequest().setActive(true));
        }

        Map<String, Object> row = queryOne(
                "SELECT active_minutes, heartbeat_count FROM study_eye_usage WHERE session_id = ?", sessionId);
        assertThat(((Number) row.get("active_minutes")).intValue()).isEqualTo(7);
        assertThat(((Number) row.get("heartbeat_count")).intValue()).isEqualTo(14);

        // 切后台 3 分钟：客户端停报，回到前台后的第一次心跳间隔 180 秒 → 该段不计入
        backdateHeartbeat(sessionId, 180);
        EyeSessionVO afterAway = eyeService.heartbeat(sessionId, new EyeHeartbeatRequest().setActive(true));

        row = queryOne(
                "SELECT active_minutes, heartbeat_count FROM study_eye_usage WHERE session_id = ?", sessionId);
        assertThat(((Number) row.get("active_minutes")).intValue()).isEqualTo(7);
        assertThat(((Number) row.get("heartbeat_count")).intValue()).isEqualTo(14);
        assertThat(afterAway.getRestRequired()).isFalse();
    }

    // ------------------------------------------------------------------ TC-07-10

    @Test
    @DisplayName("TC-07-10 蓝光过滤开关落库，刷新页面后状态保持")
    void blueLight_persistedAcrossSessions() {
        EyeSessionVO session = eyeService.startSession();
        eyeService.heartbeat(session.getSessionId(),
                new EyeHeartbeatRequest().setActive(true).setBlueLight(true));

        Map<String, Object> row = queryOne(
                "SELECT blue_light_on FROM study_eye_usage WHERE session_id = ?", session.getSessionId());
        assertThat(((Number) row.get("blue_light_on")).intValue()).isEqualTo(1);

        // 刷新页面 = 重新开一次会话，开关状态沿用
        EyeSessionVO reopened = eyeService.startSession();
        assertThat(reopened.getBlueLightOn()).isTrue();
        row = queryOne(
                "SELECT blue_light_on FROM study_eye_usage WHERE session_id = ?", reopened.getSessionId());
        assertThat(((Number) row.get("blue_light_on")).intValue()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ TC-07-11

    @Test
    @DisplayName("TC-07-11 到达阈值后服务端下发休息阻断标志")
    void restRequired_flaggedWhenThresholdReached() {
        givenContextEyeInterval(1);
        EyeSessionVO session = eyeService.startSession();
        String sessionId = session.getSessionId();

        EyeSessionVO current = null;
        for (int i = 0; i < 2; i++) {
            backdateHeartbeat(sessionId, HEARTBEAT_SECONDS);
            current = eyeService.heartbeat(sessionId, new EyeHeartbeatRequest().setActive(true));
        }

        assertThat(current.getRestRequired()).isTrue();
        Map<String, Object> row = queryOne(
                "SELECT rest_count, rest_confirmed FROM study_eye_usage WHERE session_id = ?", sessionId);
        assertThat(((Number) row.get("rest_count")).intValue()).isEqualTo(1);
        assertThat(((Number) row.get("rest_confirmed")).intValue()).isEqualTo(0);
    }

    // ------------------------------------------------------------------ TC-07-12

    @Test
    @DisplayName("TC-07-12 遮罩不自动消失，只能由显式休息确认清除")
    void restMask_onlyClearedByExplicitConfirm() {
        givenContextEyeInterval(1);
        EyeSessionVO session = eyeService.startSession();
        String sessionId = session.getSessionId();

        EyeSessionVO triggered = null;
        for (int i = 0; i < 2; i++) {
            backdateHeartbeat(sessionId, HEARTBEAT_SECONDS);
            triggered = eyeService.heartbeat(sessionId, new EyeHeartbeatRequest().setActive(true));
        }
        assertThat(triggered.getRestRequired()).isTrue();

        // 继续心跳、静置都不会让遮罩自动消失
        for (int i = 0; i < 3; i++) {
            backdateHeartbeat(sessionId, HEARTBEAT_SECONDS);
            assertThat(eyeService.heartbeat(sessionId, new EyeHeartbeatRequest().setActive(true)).getRestRequired())
                    .isTrue();
        }

        // 显式确认休息 → 遮罩清除
        EyeSessionVO confirmed = eyeService.confirmRest(sessionId);
        assertThat(confirmed.getRestRequired()).isFalse();
        Map<String, Object> row = queryOne(
                "SELECT rest_count, rest_confirmed FROM study_eye_usage WHERE session_id = ?", sessionId);
        assertThat(((Number) row.get("rest_count")).intValue()).isEqualTo(1);
        assertThat(((Number) row.get("rest_confirmed")).intValue()).isEqualTo(1);

        // 护眼域不允许出现定时任务（定时任务会自动清掉遮罩）
        for (Path file : eyeSources()) {
            assertThat(readSource(file)).doesNotContain("@Scheduled");
        }
    }

    // ------------------------------------------------------------------ TC-07-13

    @Test
    @DisplayName("TC-07-13 休息提醒文案含具体可执行动作")
    void restPrompt_containsConcreteAction() {
        EyeSessionVO session = eyeService.startSession();

        assertThat(session.getRestPrompt()).isNotBlank();
        assertThat(session.getRestPrompt()).contains("看向 6 米外保持 20 秒");
    }

    // ------------------------------------------------------------------ TC-07-14

    @Test
    @DisplayName("TC-07-14 护眼间隔优先取上下文配置，没有上下文则用全局配置")
    void interval_prefersContextOverGlobalConfig() {
        // 没有上下文：全局默认 15 分钟 → 用眼 1 分钟不触发
        EyeSessionVO plain = eyeService.startSession();
        EyeSessionVO plainState = null;
        for (int i = 0; i < 2; i++) {
            backdateHeartbeat(plain.getSessionId(), HEARTBEAT_SECONDS);
            plainState = eyeService.heartbeat(plain.getSessionId(), new EyeHeartbeatRequest().setActive(true));
        }
        assertThat(plainState.getRestRequired()).isFalse();

        // 上下文里配 1 分钟 → 用眼 1 分钟即触发
        givenContextEyeInterval(1);
        EyeSessionVO configured = eyeService.startSession();
        EyeSessionVO configuredState = null;
        for (int i = 0; i < 2; i++) {
            backdateHeartbeat(configured.getSessionId(), HEARTBEAT_SECONDS);
            configuredState = eyeService.heartbeat(configured.getSessionId(),
                    new EyeHeartbeatRequest().setActive(true));
        }
        assertThat(configuredState.getRestRequired()).isTrue();
    }

    // ------------------------------------------------------------------ TC-07-15

    @Test
    @DisplayName("TC-07-15 孩子端响应不出现用眼时长数字")
    void kidResponse_hasNoUsageMinutes() throws Exception {
        List<String> fields = Arrays.stream(EyeSessionVO.class.getDeclaredFields())
                .map(Field::getName).map(name -> name.toLowerCase()).toList();
        assertThat(fields).noneMatch(name -> name.contains("activeminutes") || name.contains("eyeminutes")
                || name.contains("useminutes") || name.contains("eyeminute"));

        // 序列化后的真实响应里同样没有时长字段
        EyeSessionVO session = eyeService.startSession();
        String json = new ObjectMapper().writeValueAsString(session);
        assertThat(json).doesNotContain("activeMinutes").doesNotContain("eyeMinutes").doesNotContain("usageMinutes");

        // 客户端上报入口里也不允许有时长字段
        List<String> requestFields = Arrays.stream(EyeHeartbeatRequest.class.getDeclaredFields())
                .map(Field::getName).map(name -> name.toLowerCase()).toList();
        assertThat(requestFields).noneMatch(name -> name.contains("minute") || name.contains("duration")
                || name.contains("second"));
    }

    // ------------------------------------------------------------------ R-03

    @Test
    @DisplayName("R03-3 护眼域以服务端心跳基线累计时长，不采信客户端上报")
    void source_usesServerSideHeartbeatBaseline() {
        List<Path> sources = eyeSources();
        assertThat(sources).as("护眼域源码应可被扫描到").isNotEmpty();
        for (Path file : sources) {
            String content = readSource(file);
            assertThat(content).as(file + " 缺少服务端心跳基线").contains("lastHeartbeat");
            String lower = content.toLowerCase();
            assertThat(lower).as(file + " 疑似信任客户端上报时长")
                    .doesNotContain("clientreported").doesNotContain("clientminutes")
                    .doesNotContain("reportedminutes");
        }
    }

    // ------------------------------------------------------------------ 内部

    /**
     * 回拨心跳基线，模拟「距上一次心跳已经过了 N 秒」。
     */
    private void backdateHeartbeat(String sessionId, int seconds) {
        jdbc.update("UPDATE study_eye_usage SET update_time = DATE_SUB(update_time, INTERVAL "
                + seconds + " SECOND) WHERE session_id = ?", sessionId);
    }

    /**
     * 造一条当前上下文，并把护眼提醒间隔设成指定分钟数。
     */
    private void givenContextEyeInterval(int intervalMin) {
        jdbc.update("INSERT INTO study_context (id, child_id, context_code, province_code, province_name,"
                + " stage_code, grade_code, textbook_version_id, eye_interval_min, is_current, effective_from,"
                + " del_flag, create_time) VALUES (?,?,?,?,?,?,?,?,?,1,NOW(),0,NOW())",
                "utctx" + unique(), childId, "CTX-UT-" + unique(), "11", "北京市",
                "PRIMARY", "G3", "ut-textbook", intervalMin);
    }

    /**
     * 护眼域主源码文件列表。
     */
    private List<Path> eyeSources() {
        Path root = Paths.get("").toAbsolutePath()
                .resolve("src").resolve("main").resolve("java")
                .resolve("org").resolve("jeecg").resolve("modules").resolve("study").resolve("eye");
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
}
