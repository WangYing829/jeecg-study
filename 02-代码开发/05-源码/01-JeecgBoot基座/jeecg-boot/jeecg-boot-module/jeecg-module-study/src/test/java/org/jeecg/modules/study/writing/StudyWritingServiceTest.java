package org.jeecg.modules.study.writing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.study.error.constant.ErrorCause;
import org.jeecg.modules.study.error.service.StudyErrorPoolService;
import org.jeecg.modules.study.error.vo.ReviewQueueItemVO;
import org.jeecg.modules.study.family.service.StudyChildService;
import org.jeecg.modules.study.family.service.StudyFamilyService;
import org.jeecg.modules.study.family.vo.ChildCreateRequest;
import org.jeecg.modules.study.family.vo.ChildCreateVO;
import org.jeecg.modules.study.support.StudyTestSupport;
import org.jeecg.modules.study.writing.service.StudyWritingService;
import org.jeecg.modules.study.writing.vo.WritingDiffVO;
import org.jeecg.modules.study.writing.vo.WritingDissectionLayerVO;
import org.jeecg.modules.study.writing.vo.WritingDissectionListVO;
import org.jeecg.modules.study.writing.vo.WritingDissectionVO;
import org.jeecg.modules.study.writing.vo.WritingDraftRequest;
import org.jeecg.modules.study.writing.vo.WritingDraftResultVO;
import org.jeecg.modules.study.writing.vo.WritingOutlineBody;
import org.jeecg.modules.study.writing.vo.WritingOutlineRequest;
import org.jeecg.modules.study.writing.vo.WritingOutlineResultVO;

/**
 * 语文写作域自动化测试（V4 / TC-06-01 … TC-06-07）。
 *
 * <p>跑在真实 {@code jeecg-study} 库上：拆解库分组、提纲与初稿的落库、
 * 幂等、错题入池都必须在真实 MySQL 上才验得准。</p>
 *
 * @author jeecg-study
 */
class StudyWritingServiceTest extends StudyTestSupport {

    /** 题目（贯穿全文） */
    private static final String TOPIC = "一次难忘的坚持";

    /**
     * 一篇<b>刻意留了毛病</b>的作文：没有连接词、没有细节词、没有描写，
     * 结尾也没有回扣开头 —— 用来验证「对照式批改只给三条」。
     */
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

    /** 一篇<b>含病句</b>的作文：关联词缺半句、助词重复 */
    private static final String ESSAY_WITH_ILL_SENTENCES = """
            天刚亮的时候，屋子里还是灰蒙蒙的。我把台灯拧亮，翻开摊在桌上的练习册。
            因为错过了第一节的车，我走路去了学校，一路上都在想昨天的事。
            他的的的作业本落在教室里了，我拿去还给了他，他连声道谢。
            窗外的天色一点点暗下来，值日的人推门进来，把灯一盏一盏打开。
            我把谱子上的音标抄在本子的末页，旁边记着每一次卡住的地方。
            那段路我走过很多次，脚步都不自觉地慢下来，连当时跑调的调子都还记得。
            这件事我一直没有忘。
            """;

    @Autowired
    private StudyWritingService writingService;

    @Autowired
    private StudyErrorPoolService errorPoolService;

    @Autowired
    private StudyFamilyService familyService;

    @Autowired
    private StudyChildService childService;

    /** JSON 序列化（响应体自检用） */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 当前测试孩子的 ID */
    private String childId;

    /** 当前测试孩子的业务ID */
    private String childCode;

    /** 拆解集编码 */
    private String dissectionCode;

    /** 拆解库「题目钩子」层的业务ID */
    private String hookCode;

    // ------------------------------------------------------------------ 夹具

    @BeforeEach
    void givenKidAndDissection() {
        String parentName = "ut_wr_" + unique();
        String parentId = givenParentAccount(parentName);
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

        String kidAccountId = (String) queryOne("SELECT user_id FROM study_child WHERE id = ?", childId)
                .get("user_id");
        loginAsKid(kidAccountId, child.getUsername());

        seedDissection();
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
            System.out.println("[StudyWritingServiceTest] 清理测试数据失败：" + e.getMessage());
        }
    }

    /**
     * 造一组佳作拆解（六层）。
     */
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

    // ------------------------------------------------------------------ TC-06-01

    @Test
    @DisplayName("TC-06-01 拆解库六层呈现")
    void listDissections_presentsSixLayersInOrder() {
        WritingDissectionListVO list = writingService.listDissections(null, null);
        assertThat(list.getTotal()).isGreaterThanOrEqualTo(1);

        WritingDissectionVO dissection = list.getItems().stream()
                .filter(item -> dissectionCode.equals(item.getDissectionCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("拆解库里没有刚才造的这组拆解"));

        // 六层齐全，且顺序固定
        assertThat(dissection.getLayers()).hasSize(6);
        List<String> layerCodes = dissection.getLayers().stream()
                .map(WritingDissectionLayerVO::getLayer).collect(Collectors.toList());
        assertThat(layerCodes).containsExactly("HOOK", "OPEN", "MATERIAL", "STRUCT", "HAND", "END");
        List<String> layerNames = dissection.getLayers().stream()
                .map(WritingDissectionLayerVO::getLayerName).collect(Collectors.toList());
        assertThat(layerNames)
                .containsExactly("题目钩子", "开篇三句", "选材角度", "结构图", "三个手法例句", "结尾回扣");

        // 拆解详情与列表一致
        WritingDissectionVO detail = writingService.getDissection(hookCode);
        assertThat(detail.getLayers()).hasSize(6);
        assertThat(detail.getDissectionCode()).isEqualTo(dissectionCode);
        assertThat(detail.getTitle()).isEqualTo(TOPIC);
    }

    // ------------------------------------------------------------------ TC-06-02

    @Test
    @DisplayName("TC-06-02 每层附提示题")
    void dissection_everyLayerCarriesPromptQuestion() {
        WritingDissectionVO dissection = writingService.getDissection(hookCode);

        assertThat(dissection.getLayers()).hasSize(6);
        for (WritingDissectionLayerVO layer : dissection.getLayers()) {
            assertThat(layer.getPromptQuestion()).as("层「%s」要有提示题", layer.getLayer()).isNotBlank();
            assertThat(layer.getTeachingPoint()).as("层「%s」要有写法要点", layer.getLayer()).isNotBlank();
            assertThat(layer.getContent()).as("层「%s」要有正文", layer.getLayer()).isNotBlank();
        }
    }

    // ------------------------------------------------------------------ TC-06-03

    @Test
    @DisplayName("TC-06-03 对照式批改只给三条")
    void submitDraft_diffListIsCappedAtThree() throws Exception {
        passOutline();
        WritingDraftResultVO result = writingService.submitDraft(draftRequest(ESSAY));

        // 命中了四条规则，但只给最该改的三条
        assertThat(result.getDiffList()).hasSize(3);
        assertThat(result.getDiffCount()).isEqualTo(3);
        assertThat(result.getDiffCount()).isLessThanOrEqualTo(3);
        assertThat(result.getSource()).isEqualTo("rule");

        // 文案不含「扣了几分」这类说法
        for (WritingDiffVO diff : result.getDiffList()) {
            assertThat(diff.getTitle()).doesNotContain("扣了几");
            assertThat(diff.getDetail()).doesNotContain("扣了几");
            assertThat(diff.getSuggestion()).doesNotContain("扣了几");
        }
        assertThat(objectMapper.writeValueAsString(result)).doesNotContain("扣了几");
    }

    // ------------------------------------------------------------------ TC-06-04

    @Test
    @DisplayName("TC-06-04 差异清单是清单不是分数")
    void submitDraft_diffListHasNoScoreOrLevel() throws Exception {
        passOutline();
        WritingDraftResultVO result = writingService.submitDraft(draftRequest(ESSAY));

        // 每条差异都有维度 / 说明 / 建议
        assertThat(result.getDiffList()).isNotEmpty();
        for (WritingDiffVO diff : result.getDiffList()) {
            assertThat(diff.getDimension()).isNotBlank();
            assertThat(diff.getTitle()).isNotBlank();
            assertThat(diff.getDetail()).isNotBlank();
            assertThat(diff.getSuggestion()).isNotBlank();
        }

        // 响应体里没有分数、等级一类的内容
        String json = objectMapper.writeValueAsString(result);
        assertThat(json).doesNotContain("分数");
        assertThat(json).doesNotContain("等级");
        assertThat(json).doesNotContain("级别");
        assertThat(json).doesNotContain("得分");
        assertThat(json).doesNotContain("打分");
        assertThat(json).doesNotContain("扣了几");
        assertThat(json).doesNotContain("名次");
        assertThat(json).doesNotContain("排名");
    }

    // ------------------------------------------------------------------ TC-06-05

    @Test
    @DisplayName("TC-06-05 三步训练流顺序")
    void trainFlow_outlineMustPassBeforeDraft() {
        // 提纲没过：停在原地，不能进下一步
        WritingOutlineRequest incomplete = outlineRequest();
        incomplete.getOutline().setEnding("");
        WritingOutlineResultVO failed = writingService.submitOutline(incomplete);
        assertThat(failed.getPassed()).isFalse();
        assertThat(failed.getNextStep()).isEqualTo("OUTLINE");
        assertThat(failed.getFeedback()).isNotEmpty();

        // 提纲过了：nextStep = DRAFT
        WritingOutlineResultVO passed = writingService.submitOutline(outlineRequest());
        assertThat(passed.getPassed()).isTrue();
        assertThat(passed.getNextStep()).isEqualTo("DRAFT");
        assertThat(passed.getFeedback()).isNotEmpty();

        // 过了之后才允许写初稿
        WritingDraftResultVO draft = writingService.submitDraft(draftRequest(ESSAY));
        assertThat(draft.getContentId()).isNotBlank();
    }

    // ------------------------------------------------------------------ TC-06-06

    @Test
    @DisplayName("TC-06-06 防代写服务端校验")
    void submitDraft_withoutPassedOutlineReturns4002AndWritesNothing() {
        WritingDraftRequest request = draftRequest(ESSAY);

        JeecgBootException error = catchThrowableOfType(
                () -> writingService.submitDraft(request), JeecgBootException.class);

        assertThat(error).as("跳过提纲直接提交初稿必须被拦下").isNotNull();
        assertThat(error.getErrCode()).isEqualTo(4002);
        assertThat(count("SELECT COUNT(*) FROM study_writing WHERE child_id = ? AND layer = 'DRAFT'",
                childId)).as("被拦下的初稿不能落库").isZero();
    }

    // ------------------------------------------------------------------ TC-06-07

    @Test
    @DisplayName("TC-06-07 写作病句进错题池")
    void submitDraft_illSentencesEnterUnifiedErrorPool() {
        passOutline();
        WritingDraftResultVO result = writingService.submitDraft(draftRequest(ESSAY_WITH_ILL_SENTENCES));

        // 病句落进统一错题池（不自建错句表）
        List<Map<String, Object>> rows = queryList(
                "SELECT * FROM study_error_pool WHERE child_id = ? AND source_module = 'WRITING'",
                childId);
        assertThat(rows).as("病句要进统一错题池").isNotEmpty();
        for (Map<String, Object> row : rows) {
            assertThat(String.valueOf(row.get("content_type"))).isEqualTo("PARAGRAPH");
            assertThat(String.valueOf(row.get("judge_source"))).isEqualTo("rule");
            assertThat(String.valueOf(row.get("child_id"))).isEqualTo(childId);
            assertThat(ErrorCause.isValid(String.valueOf(row.get("error_cause")))).isTrue();
            assertThat(String.valueOf(row.get("content_id"))).startsWith(result.getContentId());
            assertThat(String.valueOf(row.get("raw_answer"))).isNotBlank();
        }

        // 与其它模块的错题共享同一套复习排期
        List<ReviewQueueItemVO> queue = errorPoolService.todayQueue(childId);
        assertThat(queue).isNotEmpty();
        assertThat(queue.stream().anyMatch(item -> "WRITING".equals(item.getSourceModule())))
                .as("写作错题要进统一复习队列").isTrue();

        // 幂等：同一篇作文重复提交，病句只入池一次
        int before = count("SELECT COUNT(*) FROM study_error_pool WHERE child_id = ?", childId);
        writingService.submitDraft(draftRequest(ESSAY_WITH_ILL_SENTENCES));
        int after = count("SELECT COUNT(*) FROM study_error_pool WHERE child_id = ?", childId);
        assertThat(after).isEqualTo(before);
    }

    // ------------------------------------------------------------------ 工具

    /**
     * 提交一份能通过的提纲。
     */
    private void passOutline() {
        WritingOutlineResultVO result = writingService.submitOutline(outlineRequest());
        assertThat(result.getPassed()).isTrue();
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

    private WritingDraftRequest draftRequest(String content) {
        WritingDraftRequest request = new WritingDraftRequest();
        request.setWritingCode(hookCode);
        request.setTopic(TOPIC);
        request.setContent(content);
        return request;
    }

    /**
     * 提纲提交幂等：重复提交不产生重复行。
     */
    @Test
    @DisplayName("附加 提纲与初稿提交幂等")
    void submitOutlineAndDraft_areIdempotent() {
        passOutline();
        int outlineRows = count("SELECT COUNT(*) FROM study_writing WHERE child_id = ? AND layer = 'OUTLINE'",
                childId);
        writingService.submitOutline(outlineRequest());
        assertThat(count("SELECT COUNT(*) FROM study_writing WHERE child_id = ? AND layer = 'OUTLINE'",
                childId)).isEqualTo(outlineRows);

        writingService.submitDraft(draftRequest(ESSAY));
        int draftRows = count("SELECT COUNT(*) FROM study_writing WHERE child_id = ? AND layer = 'DRAFT'",
                childId);
        assertThat(draftRows).isEqualTo(1);
        writingService.submitDraft(draftRequest(ESSAY));
        assertThat(count("SELECT COUNT(*) FROM study_writing WHERE child_id = ? AND layer = 'DRAFT'",
                childId)).isEqualTo(draftRows);
    }

    /**
     * 家长身份调孩子端接口一律 4010（不接受前端传来的 childId）。
     */
    @Test
    @DisplayName("附加 家长身份访问孩子端被拒")
    void kidEndpoint_rejectsParentIdentity() {
        String parentName = "ut_wr_p_" + unique();
        String parentId = givenParentAccount(parentName);
        loginAsParent(parentId, parentName);

        assertThatThrownBy(() -> writingService.submitOutline(outlineRequest()))
                .hasMessageContaining("孩子");
        assertThat(count("SELECT COUNT(*) FROM study_writing WHERE child_id = ? AND layer = 'OUTLINE'",
                childId)).isZero();
    }
}
