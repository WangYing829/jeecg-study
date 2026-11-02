package org.jeecg.modules.study.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.study.common.constant.StudyErrorCode;
import org.jeecg.modules.study.context.constant.StudyStageGrade;
import org.jeecg.modules.study.context.service.StudyContextService;
import org.jeecg.modules.study.context.service.StudyTextbookVersionService;
import org.jeecg.modules.study.context.vo.ContextSaveRequest;
import org.jeecg.modules.study.context.vo.ContextVO;
import org.jeecg.modules.study.context.vo.TextbookVersionVO;
import org.jeecg.modules.study.family.service.StudyChildService;
import org.jeecg.modules.study.family.service.StudyFamilyService;
import org.jeecg.modules.study.family.vo.ChildCreateRequest;
import org.jeecg.modules.study.family.vo.ChildCreateVO;
import org.jeecg.modules.study.support.StudyTestSupport;

/**
 * 全局上下文 + 教材版本自动化测试。
 *
 * <p>覆盖测试用例 TC-01-01 … TC-01-07。</p>
 *
 * <p>上下文按 {@code child_id} 隔离：测试先造「家长 + 家庭 + 孩子」，
 * 再以家长 / 孩子身份操作上下文；测试数据一律以 {@code UT} 前缀命名，便于清理。</p>
 *
 * @author jeecg-study
 */
class StudyContextServiceTest extends StudyTestSupport {

    @Autowired
    private StudyContextService contextService;

    @Autowired
    private StudyTextbookVersionService textbookVersionService;

    @Autowired
    private StudyFamilyService familyService;

    @Autowired
    private StudyChildService childService;

    /**
     * 上下文行也要清理：先按孩子删，再让基类删孩子。
     */
    @Override
    protected void cleanTestData() {
        try {
            jdbc.update("DELETE FROM study_error_pool WHERE error_code LIKE 'UTERR-%'");
            jdbc.update("DELETE FROM study_context WHERE child_id IN "
                    + "(SELECT id FROM study_child WHERE nickname LIKE 'UT%')");
        } catch (Exception e) {
            System.out.println("[StudyContextServiceTest] 清理上下文测试数据失败：" + e.getMessage());
        }
        super.cleanTestData();
    }

    // ------------------------------------------------------------------ TC-01-01

    @Test
    @DisplayName("TC-01-01 首次使用引导配置：未配置上下文返回 configured=false 且不报错")
    void current_notConfigured_returnsConfiguredFalse() {
        ChildFixture fixture = givenParentAndChild();

        ContextVO vo = contextService.current(null);

        assertThat(vo).isNotNull();
        assertThat(vo.getConfigured()).isFalse();
        assertThat(vo.getContextCode()).isNull();
        assertThat(count("SELECT COUNT(*) FROM study_context WHERE child_id = ?", fixture.childId)).isZero();
    }

    // ------------------------------------------------------------------ TC-01-02

    @Test
    @DisplayName("TC-01-02 上下文一次配置全局生效：配置后各模块共用同一当前上下文")
    void save_once_appliesToAllModules() {
        ChildFixture fixture = givenParentAndChild();
        String versionId = pickVersionId("PRIMARY", null);

        ContextVO saved = contextService.save(new ContextSaveRequest()
                .setProvinceCode("32")
                .setStageCode("PRIMARY")
                .setGradeCode("G5")
                .setTextbookVersionId(versionId), null);

        assertThat(saved.getConfigured()).isTrue();
        assertThat(saved.getContextCode()).startsWith("CTX-");
        assertThat(saved.getStageName()).isEqualTo("小学");
        assertThat(saved.getGradeName()).isEqualTo("五年级");
        assertThat(saved.getProvinceName()).isEqualTo("江苏省");
        assertThat(saved.getTextbookVersionName()).isNotBlank();

        // 再次取上下文仍是同一份（各模块默认取数按当前上下文，不再弹选择框）
        ContextVO current = contextService.current(null);
        assertThat(current.getContextCode()).isEqualTo(saved.getContextCode());
        assertThat(current.getTextbookVersionId()).isEqualTo(versionId);

        // 一个孩子同时只有一个当前上下文
        assertThat(count("SELECT COUNT(*) FROM study_context WHERE child_id = ? AND is_current = 1",
                fixture.childId)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM study_context WHERE child_id = ?",
                fixture.childId)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ TC-01-03

    @Test
    @DisplayName("TC-01-03 学段联动过滤年级与教材：年级必须落在所属学段内")
    void stageGradeLinkage_illegalCombinationRejected() {
        // 学段 → 年级区间
        assertThat(StudyStageGrade.gradesOf("PRIMARY"))
                .containsExactly("G1", "G2", "G3", "G4", "G5", "G6");
        assertThat(StudyStageGrade.gradesOf("JUNIOR")).containsExactly("G7", "G8", "G9");
        assertThat(StudyStageGrade.gradesOf("SENIOR")).containsExactly("G10", "G11", "G12");

        ChildFixture fixture = givenParentAndChild();
        String versionId = pickVersionId("PRIMARY", null);

        // 小学 + 七年级 = 非法组合 → 4004
        assertThatThrownBy(() -> contextService.save(new ContextSaveRequest()
                .setProvinceCode("32")
                .setStageCode("PRIMARY")
                .setGradeCode("G7")
                .setTextbookVersionId(versionId), null))
                .isInstanceOf(JeecgBootException.class)
                .satisfies(e -> assertThat(((JeecgBootException) e).getErrCode())
                        .isEqualTo(StudyErrorCode.CONTEXT_NOT_CONFIGURED));

        // 非法组合不落库
        assertThat(count("SELECT COUNT(*) FROM study_context WHERE child_id = ?", fixture.childId)).isZero();

        // 教材版本按学段过滤，且每个学段都有可用版本
        assertThat(textbookVersionService.list("PRIMARY")).isNotEmpty();
        assertThat(textbookVersionService.list("PRIMARY")).allSatisfy(v ->
                assertThat(v.getStageCode()).isEqualTo("PRIMARY"));
        assertThat(textbookVersionService.list("JUNIOR")).isNotEmpty();
        assertThat(textbookVersionService.list("SENIOR")).isNotEmpty();
    }

    // ------------------------------------------------------------------ TC-01-04 / TC-01-05

    @Test
    @DisplayName("TC-01-04 / TC-01-05 切换教材版本：生成新上下文，旧上下文保留可追溯")
    void switchTextbookVersion_keepsOldContextAsHistory() {
        ChildFixture fixture = givenParentAndChild();
        String versionA = pickVersionId("PRIMARY", null);
        String versionB = pickVersionId("PRIMARY", versionA);

        ContextVO first = contextService.save(new ContextSaveRequest()
                .setProvinceCode("32").setStageCode("PRIMARY").setGradeCode("G5")
                .setTextbookVersionId(versionA), null);
        ContextVO second = contextService.save(new ContextSaveRequest()
                .setProvinceCode("32").setStageCode("PRIMARY").setGradeCode("G6")
                .setTextbookVersionId(versionB), null);

        assertThat(second.getContextCode()).isNotEqualTo(first.getContextCode());
        assertThat(second.getTextbookVersionId()).isEqualTo(versionB);

        // 旧上下文保留，只是不再是当前
        Map<String, Object> old = queryOne("SELECT * FROM study_context WHERE context_code = ?",
                first.getContextCode());
        assertThat(old).isNotNull();
        assertThat(((Number) old.get("is_current")).intValue()).isZero();

        // 历史列表能查到两个上下文
        List<ContextVO> history = contextService.list(null);
        assertThat(history).hasSize(2);
        assertThat(history).extracting(ContextVO::getContextCode)
                .containsExactlyInAnyOrder(first.getContextCode(), second.getContextCode());
    }

    // ------------------------------------------------------------------ TC-01-06

    @Test
    @DisplayName("TC-01-06 历史记录不随上下文切换：旧错题仍归旧上下文，不混入新队列")
    void switchContext_oldErrorRecordsStayInOldContext() {
        ChildFixture fixture = givenParentAndChild();
        String versionId = pickVersionId("PRIMARY", null);

        ContextVO first = contextService.save(new ContextSaveRequest()
                .setProvinceCode("32").setStageCode("PRIMARY").setGradeCode("G5")
                .setTextbookVersionId(versionId), null);

        // 在旧上下文下留一条历史错题
        String errorCode = "UTERR-" + unique();
        jdbc.update("INSERT INTO study_error_pool (id, child_id, error_code, context_id, source_module, "
                        + "content_type, content_id, error_cause, judge_source, mastery_state, correct_streak, "
                        + "review_status, upgrade_flag, copyright_type, del_flag) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                unique32(), fixture.childId, errorCode, first.getContextId(), "ERROR", "QUESTION",
                "UTCONTENT-" + unique(), "CONCEPT", "rule", 1, 0, 1, 0, "SELF", 0);

        ContextVO second = contextService.save(new ContextSaveRequest()
                .setProvinceCode("32").setStageCode("PRIMARY").setGradeCode("G6")
                .setTextbookVersionId(versionId), null);
        assertThat(second.getContextId()).isNotEqualTo(first.getContextId());

        // 新上下文下查不到旧错题；旧上下文下仍能查到
        assertThat(count("SELECT COUNT(*) FROM study_error_pool WHERE context_id = ?",
                second.getContextId())).isZero();
        assertThat(count("SELECT COUNT(*) FROM study_error_pool WHERE context_id = ?",
                first.getContextId())).isEqualTo(1);

        // 旧上下文本身仍在库里（没被删）
        assertThat(count("SELECT COUNT(*) FROM study_context WHERE context_code = ?",
                first.getContextCode())).isEqualTo(1);
    }

    // ------------------------------------------------------------------ TC-01-07

    @Test
    @DisplayName("TC-01-07 教材版本 7 个全可选：人教/苏教/北师大/浙教/沪教/京教/鲁教")
    void textbookVersions_sevenPublishers() {
        List<TextbookVersionVO> all = textbookVersionService.list(null);

        assertThat(all).hasSize(7);
        assertThat(all).extracting(TextbookVersionVO::getPublisher)
                .containsExactlyInAnyOrder("人教", "苏教", "北师大", "浙教", "沪教", "京教", "鲁教");
        assertThat(all).extracting(TextbookVersionVO::getVersionName)
                .containsExactlyInAnyOrder("人教版", "苏教版", "北师大版", "浙教版", "沪教版", "京教版", "鲁教版");
        assertThat(all).extracting(TextbookVersionVO::getSortNo).doesNotHaveDuplicates();
    }

    // ------------------------------------------------------------------ 护眼间隔

    @Test
    @DisplayName("护眼提醒间隔可配置，越界与非本人上下文被拒")
    void eyeInterval_configurableAndScoped() {
        ChildFixture fixture = givenParentAndChild();
        String versionId = pickVersionId("PRIMARY", null);
        ContextVO saved = contextService.save(new ContextSaveRequest()
                .setProvinceCode("32").setStageCode("PRIMARY").setGradeCode("G5")
                .setTextbookVersionId(versionId), null);

        ContextVO updated = contextService.updateEyeInterval(saved.getContextId(), 25);
        assertThat(updated.getEyeIntervalMin()).isEqualTo(25);

        assertThatThrownBy(() -> contextService.updateEyeInterval(saved.getContextId(), 0))
                .isInstanceOf(JeecgBootException.class);

        // 另一个家长访问该上下文 → 4010
        String otherName = "ut_ctx_other_" + unique();
        String otherId = givenParentAccount(otherName);
        loginAsParent(otherId, otherName);
        familyService.create("UT他人家庭");

        assertThatThrownBy(() -> contextService.updateEyeInterval(saved.getContextId(), 30))
                .isInstanceOf(JeecgBootException.class)
                .satisfies(e -> assertThat(((JeecgBootException) e).getErrCode())
                        .isEqualTo(StudyErrorCode.FORBIDDEN_SCOPE));

        // 越权读取同样是 4010（不用 404，避免资源探测）
        assertThatThrownBy(() -> contextService.current(fixture.childCode))
                .isInstanceOf(JeecgBootException.class)
                .satisfies(e -> assertThat(((JeecgBootException) e).getErrCode())
                        .isEqualTo(StudyErrorCode.FORBIDDEN_SCOPE));
    }

    // ------------------------------------------------------------------ 孩子身份

    @Test
    @DisplayName("孩子身份与家长身份看到同一个当前上下文")
    void kidAndParentSeeSameContext() {
        ChildFixture fixture = givenParentAndChild();
        String versionId = pickVersionId("PRIMARY", null);
        ContextVO saved = contextService.save(new ContextSaveRequest()
                .setProvinceCode("32").setStageCode("PRIMARY").setGradeCode("G5")
                .setTextbookVersionId(versionId), null);

        loginAsKid(fixture.childUserId, "ut_kid");
        ContextVO kidView = contextService.current(null);

        assertThat(kidView.getConfigured()).isTrue();
        assertThat(kidView.getContextCode()).isEqualTo(saved.getContextCode());
        assertThat(kidView.getChildId()).isEqualTo(fixture.childId);
    }

    // ------------------------------------------------------------------ 内部工具

    /**
     * 造一个「家长 + 家庭 + 孩子」，并以家长身份登录。
     */
    private ChildFixture givenParentAndChild() {
        String userName = "ut_ctx_" + unique();
        String userId = givenParentAccount(userName);
        loginAsParent(userId, userName);
        familyService.create("UT上下文家庭");
        ChildCreateVO child = childService.create(new ChildCreateRequest()
                .setNickname("UT孩子" + unique())
                .setRelationLabel("妈妈")
                .setGradeCode("G5"));
        String childUserId = (String) queryOne("SELECT user_id FROM study_child WHERE id = ?",
                child.getChildId()).get("user_id");
        return new ChildFixture(userId, userName, child.getChildCode(), child.getChildId(), childUserId);
    }

    /**
     * 取一个指定学段的教材版本ID；{@code excludeVersionId} 非空时取另一个。
     */
    private String pickVersionId(String stageCode, String excludeVersionId) {
        for (TextbookVersionVO version : textbookVersionService.list(stageCode)) {
            if (excludeVersionId == null || !excludeVersionId.equals(version.getVersionId())) {
                return version.getVersionId();
            }
        }
        throw new IllegalStateException("没有可用教材版本：" + stageCode);
    }

    private String unique32() {
        return unique() + unique();
    }

    /**
     * 测试夹具：家长 / 孩子身份信息。
     */
    private static final class ChildFixture {
        private final String parentUserId;
        private final String parentName;
        private final String childCode;
        private final String childId;
        private final String childUserId;

        private ChildFixture(String parentUserId, String parentName, String childCode,
                             String childId, String childUserId) {
            this.parentUserId = parentUserId;
            this.parentName = parentName;
            this.childCode = childCode;
            this.childId = childId;
            this.childUserId = childUserId;
        }
    }
}
