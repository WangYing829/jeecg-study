package org.jeecg.modules.study.writing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.study.common.exception.StudyForbiddenException;
import org.jeecg.modules.study.family.service.StudyChildService;
import org.jeecg.modules.study.family.service.StudyFamilyService;
import org.jeecg.modules.study.family.vo.ChildCreateRequest;
import org.jeecg.modules.study.family.vo.ChildCreateVO;
import org.jeecg.modules.study.support.StudyTestSupport;
import org.jeecg.modules.study.writing.service.StudyWritingParentService;
import org.jeecg.modules.study.writing.service.StudyWritingService;
import org.jeecg.modules.study.writing.vo.WritingDraftBriefVO;
import org.jeecg.modules.study.writing.vo.WritingDraftRequest;
import org.jeecg.modules.study.writing.vo.WritingDraftResultVO;
import org.jeecg.modules.study.writing.vo.WritingFeedbackListVO;
import org.jeecg.modules.study.writing.vo.WritingFeedbackRequest;
import org.jeecg.modules.study.writing.vo.WritingFeedbackVO;
import org.jeecg.modules.study.writing.vo.WritingOutlineBody;
import org.jeecg.modules.study.writing.vo.WritingOutlineRequest;

/**
 * 家长端写作反馈自动化测试（V4 / TC-06-08 … TC-06-09）。
 *
 * <p>覆盖两件事：反馈超 100 字被拒且不落库；家长的反馈绝不进入孩子端的任何位置。</p>
 *
 * @author jeecg-study
 */
class StudyWritingParentServiceTest extends StudyTestSupport {

    /** 题目 */
    private static final String TOPIC = "一次难忘的坚持";

    /** 一篇够长的作文（批改只影响差异条数，不影响本测试） */
    private static final String ESSAY = """
            天刚亮的时候，屋子里还是灰蒙蒙的。我把台灯拧亮，翻开摊在桌上的练习册。
            第一道题卡住了，我盯着那行字看了很久，手里的笔迟迟没有落下。
            同桌的脚步声从过道那边传过来，我忽然想起昨晚在楼道里练的那一遍。
            那时候我连音都数不准，唱到第二句就走调，被老师叫到了后面。
            那之后我每天放学都留在音乐教室，把同一段从头唱一遍，唱到不再跑调为止。
            窗外的天色一点点暗下来，值日的人推门进来，把灯一盏一盏打开。
            我把谱子上的音标抄在本子的末页，旁边记着每一次卡住的地方。
            同学推门进来问我怎么还不走，我说还想再练一遍，他就坐在旁边等着。
            那段路我走过很多次，脚步都不自觉地慢下来，连当时跑调的调子都还记得。
            这件事我一直没有忘。
            """;

    @Autowired
    private StudyWritingService writingService;

    @Autowired
    private StudyWritingParentService writingParentService;

    @Autowired
    private StudyFamilyService familyService;

    @Autowired
    private StudyChildService childService;

    /** JSON 序列化（响应体自检用） */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 家长账号 ID */
    private String parentId;

    /** 家长账号名 */
    private String parentName;

    /** 孩子账号 ID */
    private String kidAccountId;

    /** 孩子 ID */
    private String childId;

    /** 孩子业务ID */
    private String childCode;

    /** 拆解集编码 */
    private String dissectionCode;

    /** 拆解库「题目钩子」层的业务ID */
    private String hookCode;

    /** 孩子已提交的初稿 */
    private WritingDraftResultVO draft;

    // ------------------------------------------------------------------ 夹具

    @BeforeEach
    void givenParentChildAndDraft() {
        parentName = "ut_wr_" + unique();
        parentId = givenParentAccount(parentName);
        loginAsParent(parentId, parentName);
        familyService.create("UT写作家庭");

        ChildCreateRequest request = new ChildCreateRequest();
        request.setNickname("UT写作娃");
        request.setGradeCode("G3");
        request.setPin("1234");
        request.setRelationLabel("妈妈");
        ChildCreateVO child = childService.create(request);
        childId = child.getChildId();
        childCode = child.getChildCode();
        kidAccountId = (String) queryOne("SELECT user_id FROM study_child WHERE id = ?", childId)
                .get("user_id");

        seedDissection();

        // 孩子先走完「提纲 → 初稿」，才有可反馈的对象
        loginAsKid(kidAccountId, child.getUsername());
        writingService.submitOutline(outlineRequest());
        draft = writingService.submitDraft(draftRequest());

        // 回到家长身份
        loginAsParent(parentId, parentName);
    }

    @AfterEach
    void cleanWritingData() {
        try {
            jdbc.update("DELETE FROM study_writing WHERE reference_code LIKE 'UT%'");
            jdbc.update("DELETE FROM study_writing WHERE child_id IN "
                    + "(SELECT id FROM study_child WHERE nickname LIKE 'UT%')");
            jdbc.update("DELETE FROM study_error_pool WHERE child_id IN "
                    + "(SELECT id FROM study_child WHERE nickname LIKE 'UT%')");
        } catch (Exception e) {
            System.out.println("[StudyWritingParentServiceTest] 清理测试数据失败：" + e.getMessage());
        }
    }

    private void seedDissection() {
        String group = "UTDS" + unique();
        dissectionCode = group;
        String[] layers = {"HOOK", "OPEN", "MATERIAL", "STRUCT", "HAND", "END"};
        String[] titles = {TOPIC, "开篇三句", "选材角度", "结构图", "三个手法例句", "结尾回扣"};
        for (int i = 0; i < layers.length; i++) {
            jdbc.update("INSERT INTO study_writing (id, child_id, writing_code, layer, title, content, "
                            + "teaching_point, prompt_question, subject_code, genre, stage_code, reference_code, "
                            + "copyright_type, status, del_flag, create_by, create_time) "
                            + "VALUES (?,NULL,?,?,?,?,?,?,?,?,?,?,?,1,0,'ut',NOW())",
                    "ut_wr_" + unique(),
                    "WRT-" + layers[i] + "-" + group,
                    layers[i],
                    titles[i],
                    "佳作在「" + titles[i] + "」这一层是这样做的。",
                    "这一层可以迁移的写法。",
                    "换到你的题目上，" + titles[i] + "你会怎么写？",
                    "CN", "记叙文", "G3", group, "SELF");
        }
        hookCode = "WRT-HOOK-" + group;
    }

    // ------------------------------------------------------------------ TC-06-08

    @Test
    @DisplayName("TC-06-08 家长一句话反馈不超过 100 字")
    void submitFeedback_over100CharsRejectedAndNotStored() {
        WritingFeedbackRequest request = new WritingFeedbackRequest();
        request.setTargetType("WRITING");
        request.setTargetCode(draft.getContentId());
        request.setContent("字".repeat(150));

        JeecgBootException error = catchThrowableOfType(
                () -> writingParentService.submitFeedback(request), JeecgBootException.class);

        assertThat(error).as("150 字反馈必须被拒绝").isNotNull();
        assertThat(error.getErrCode()).isEqualTo(400);
        assertThat(count("SELECT COUNT(*) FROM study_writing WHERE layer = 'FEEDBACK' AND child_id = ?",
                childId)).as("被拒绝的反馈不能落库").isZero();

        // 100 字以内正常提交
        request.setContent("字".repeat(100));
        WritingFeedbackVO vo = writingParentService.submitFeedback(request);
        assertThat(vo.getFeedbackCode()).isNotBlank();
        assertThat(vo.getContent()).hasSize(100);
        assertThat(vo.getTargetCode()).isEqualTo(draft.getContentId());
        assertThat(count("SELECT COUNT(*) FROM study_writing WHERE layer = 'FEEDBACK' AND child_id = ?",
                childId)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ TC-06-09

    @Test
    @DisplayName("TC-06-09 家长反馈不进入孩子端")
    void parentFeedback_neverAppearsOnKidSide() throws Exception {
        String feedbackContent = "开头写得挺稳，可以再写细一点";
        WritingFeedbackRequest request = new WritingFeedbackRequest();
        request.setTargetType("WRITING");
        request.setTargetCode(draft.getContentId());
        request.setContent(feedbackContent);

        WritingFeedbackVO feedback = writingParentService.submitFeedback(request);
        assertThat(feedback.getFeedbackCode()).isNotBlank();

        // 切回孩子身份，把孩子端的每个接口都走一遍
        loginAsKid(kidAccountId, "UT写作娃");
        StringBuilder seen = new StringBuilder();
        seen.append(objectMapper.writeValueAsString(writingService.listDissections(null, null)));
        seen.append(objectMapper.writeValueAsString(writingService.getDissection(hookCode)));
        seen.append(objectMapper.writeValueAsString(writingService.submitOutline(outlineRequest())));
        seen.append(objectMapper.writeValueAsString(writingService.submitDraft(draftRequest())));

        assertThat(seen.toString()).as("孩子端的任何响应里都不能出现家长反馈").doesNotContain(feedbackContent);

        // 孩子端的响应结构里也没有「家长反馈」这个口子
        for (Field field : WritingDraftResultVO.class.getDeclaredFields()) {
            assertThat(field.getName().toLowerCase(Locale.ROOT)).doesNotContain("feedback");
        }

        // 反馈单独存一层，孩子端读不到
        Map<String, Object> row = queryOne(
                "SELECT * FROM study_writing WHERE layer = 'FEEDBACK' AND child_id = ?", childId);
        assertThat(row).isNotNull();
        assertThat(String.valueOf(row.get("content"))).isEqualTo(feedbackContent);
    }

    // ------------------------------------------------------------------ 附加：家长之间互不可见

    @Test
    @DisplayName("附加 家长之间互不可见")
    void parentFeedback_isInvisibleToOtherParents() {
        // 第二个家长与第二个孩子
        SecondChild second = createSecondChildWithDraft();

        // 家长二给自己家的孩子提交反馈
        loginAsParent(second.parentId, second.parentName);
        WritingFeedbackRequest request = new WritingFeedbackRequest();
        request.setTargetType("WRITING");
        request.setTargetCode(second.draftCode);
        request.setContent("这是第二个家长的反馈内容");
        writingParentService.submitFeedback(request);

        // 家长一看不到家长二的反馈
        loginAsParent(parentId, parentName);
        WritingFeedbackListVO list = writingParentService.listFeedbacks(childCode);
        assertThat(list.getItems())
                .as("家长之间互不可见")
                .noneMatch(item -> item.getContent().contains("第二个家长的反馈内容"));

        // 家长一给别的孩子的作文提交反馈 → 4010
        WritingFeedbackRequest cross = new WritingFeedbackRequest();
        cross.setTargetType("WRITING");
        cross.setTargetCode(second.draftCode);
        cross.setContent("越权的反馈");
        assertThatThrownBy(() -> writingParentService.submitFeedback(cross))
                .isInstanceOf(StudyForbiddenException.class);
    }

    // ------------------------------------------------------------------ 附加：初稿清单

    @Test
    @DisplayName("附加 初稿清单只含自己关联的孩子")
    void listDrafts_onlyContainsOwnChildren() throws Exception {
        List<WritingDraftBriefVO> drafts = writingParentService.listDrafts(null);

        assertThat(drafts).as("自己孩子的初稿要能看到").isNotEmpty();
        WritingDraftBriefVO mine = drafts.stream()
                .filter(item -> draft.getContentId().equals(item.getWritingCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("初稿清单里没有自己孩子的这篇作文"));
        assertThat(mine.getTitle()).isEqualTo(TOPIC);
        assertThat(mine.getChildCode()).isEqualTo(childCode);
        assertThat(mine.getCreateTime()).isNotNull();

        // 家长端只读边界：不下发作文正文
        String json = objectMapper.writeValueAsString(drafts);
        assertThat(json).doesNotContain("天刚亮的时候");
        assertThat(json).doesNotContain(ESSAY);

        // 指定孩子业务ID时只返回这一个孩子的
        List<WritingDraftBriefVO> scoped = writingParentService.listDrafts(childCode);
        assertThat(scoped).hasSize(1);
        assertThat(scoped.get(0).getWritingCode()).isEqualTo(draft.getContentId());
    }

    @Test
    @DisplayName("附加 初稿清单越权返回 4010")
    void listDrafts_otherChildCodeReturns4010() {
        // 不存在的 childCode → 4010（不用 404，避免资源探测）
        assertThatThrownBy(() -> writingParentService.listDrafts("CHD-999999"))
                .isInstanceOf(StudyForbiddenException.class);

        SecondChild second = createSecondChildWithDraft();

        // 别的家长的孩子 → 4010
        assertThatThrownBy(() -> writingParentService.listDrafts(second.childCode))
                .isInstanceOf(StudyForbiddenException.class);

        // 省略 childCode 时也看不到别人的作文
        List<WritingDraftBriefVO> drafts = writingParentService.listDrafts(null);
        assertThat(drafts).noneMatch(item -> second.draftCode.equals(item.getWritingCode()));
    }

    // ------------------------------------------------------------------ 工具

    /**
     * 造第二个家长 + 第二个孩子，并让第二个孩子提交一篇作文。
     *
     * @return 第二个家长与孩子的标识
     */
    private SecondChild createSecondChildWithDraft() {
        String parent2Name = "ut_wr2_" + unique();
        String parent2Id = givenParentAccount(parent2Name);
        loginAsParent(parent2Id, parent2Name);
        familyService.create("UT写作家庭二");

        ChildCreateRequest request2 = new ChildCreateRequest();
        request2.setNickname("UT写作娃二");
        request2.setGradeCode("G3");
        request2.setPin("1234");
        request2.setRelationLabel("妈妈");
        ChildCreateVO child2 = childService.create(request2);
        String child2Id = child2.getChildId();
        String kid2AccountId = (String) queryOne("SELECT user_id FROM study_child WHERE id = ?", child2Id)
                .get("user_id");

        // 孩子二提交作文
        loginAsKid(kid2AccountId, child2.getUsername());
        writingService.submitOutline(outlineRequest());
        WritingDraftResultVO draft2 = writingService.submitDraft(draftRequest());

        SecondChild second = new SecondChild();
        second.parentId = parent2Id;
        second.parentName = parent2Name;
        second.childCode = child2.getChildCode();
        second.draftCode = draft2.getContentId();
        // 恢复到第一个家长的身份：造完第二家，调用方仍然以家长一视角断言
        loginAsParent(parentId, parentName);
        return second;
    }

    /**
     * 第二个家长与孩子的标识。
     */
    private static final class SecondChild {

        /** 第二个家长账号 ID */
        private String parentId;

        /** 第二个家长账号名 */
        private String parentName;

        /** 第二个孩子的业务ID */
        private String childCode;

        /** 第二个孩子的初稿业务ID */
        private String draftCode;
    }

    private WritingOutlineRequest outlineRequest() {
        WritingOutlineBody body = new WritingOutlineBody();
        body.setOpening("从清晨说起");
        body.setBody(Arrays.asList("第一段写阻力", "第二段写转折"));
        body.setEnding("回到清晨的那句话");

        WritingOutlineRequest request = new WritingOutlineRequest();
        request.setWritingCode(hookCode);
        request.setTopic(TOPIC);
        request.setOutline(body);
        return request;
    }

    private WritingDraftRequest draftRequest() {
        WritingDraftRequest request = new WritingDraftRequest();
        request.setWritingCode(hookCode);
        request.setTopic(TOPIC);
        request.setContent(ESSAY);
        return request;
    }

    /**
     * 反馈清单只含自己关联的孩子。
     */
    @Test
    @DisplayName("附加 反馈清单只含自己关联的孩子")
    void listFeedbacks_onlyContainsOwnChildren() {
        WritingFeedbackRequest request = new WritingFeedbackRequest();
        request.setTargetType("WRITING");
        request.setTargetCode(draft.getContentId());
        request.setContent("自己孩子的反馈");
        writingParentService.submitFeedback(request);

        WritingFeedbackListVO list = writingParentService.listFeedbacks(childCode);
        assertThat(list.getTotal()).isEqualTo(1);
        assertThat(list.getItems().get(0).getContent()).isEqualTo("自己孩子的反馈");

        // 指向别的孩子的 childCode → 4010
        assertThatThrownBy(() -> writingParentService.listFeedbacks("CHD-999999"))
                .isInstanceOf(StudyForbiddenException.class);
    }
}
