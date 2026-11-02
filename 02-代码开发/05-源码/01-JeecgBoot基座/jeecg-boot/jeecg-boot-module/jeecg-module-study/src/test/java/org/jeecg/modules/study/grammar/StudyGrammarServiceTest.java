package org.jeecg.modules.study.grammar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.study.family.service.StudyChildService;
import org.jeecg.modules.study.family.service.StudyFamilyService;
import org.jeecg.modules.study.family.vo.ChildCreateRequest;
import org.jeecg.modules.study.family.vo.ChildCreateVO;
import org.jeecg.modules.study.grammar.constant.GrammarTransformKind;
import org.jeecg.modules.study.grammar.service.StudyGrammarService;
import org.jeecg.modules.study.grammar.vo.GrammarCollectionResultVO;
import org.jeecg.modules.study.grammar.vo.GrammarCollectionVO;
import org.jeecg.modules.study.grammar.vo.GrammarDetectRequest;
import org.jeecg.modules.study.grammar.vo.GrammarDetectResultVO;
import org.jeecg.modules.study.grammar.vo.GrammarErrorDetailRequest;
import org.jeecg.modules.study.grammar.vo.GrammarErrorFeedbackVO;
import org.jeecg.modules.study.grammar.vo.GrammarErrorVO;
import org.jeecg.modules.study.grammar.vo.GrammarPointListVO;
import org.jeecg.modules.study.grammar.vo.GrammarPointVO;
import org.jeecg.modules.study.grammar.vo.GrammarTransformRequest;
import org.jeecg.modules.study.grammar.vo.GrammarTransformResultVO;
import org.jeecg.modules.study.grammar.vo.GrammarTransformVO;
import org.jeecg.modules.study.support.StudyTestSupport;

/**
 * 英语语法域自动化测试（V3 / TC-05-01 … TC-05-06）。
 *
 * <p>跑在真实 {@code jeecg-study} 库上：错句入池、收藏馆筛选、
 * 统一错题池的写入与幂等都必须在真实 MySQL 上才验得准。</p>
 *
 * @author jeecg-study
 */
class StudyGrammarServiceTest extends StudyTestSupport {

    /** 测试用语法点的业务ID 前缀（清理时按它删） */
    private static final String GRAMMAR_CODE_PREFIX = "UT-GRP-";

    /** 语料：同一个中文意思的五种表达 */
    private static final String TRANSFORM_LIBRARY = """
            [{
              "meaning": "我想去商店",
              "grammarCode": "UT-GRP-LIB",
              "variants": [
                {"type": "LITERAL",    "text": "I want to go to the store.",       "usage": "意思对得过去，但母语者听起来有点生硬"},
                {"type": "COLLOQUIAL", "text": "I feel like going to the store.",  "usage": "日常口语里最自然的说法"},
                {"type": "WRITTEN",    "text": "I would like to go to the store.", "usage": "书面表达更稳妥"},
                {"type": "EXAM_SAFE",  "text": "I would like to go to the store.", "usage": "口语考试与写作里的稳妥选择"},
                {"type": "WRONG",      "text": "I am wanting to go to the store.", "usage": "错误示范，只用来对照"}
              ]
            }]
            """;

    @Autowired
    private StudyGrammarService grammarService;

    @Autowired
    private StudyFamilyService familyService;

    @Autowired
    private StudyChildService childService;

    /** 当前测试孩子的 ID */
    private String childId;

    // ------------------------------------------------------------------ 夹具

    @BeforeEach
    void givenKidAndGrammarPoint() {
        String parentName = "ut_gp_" + unique();
        String parentId = givenParentAccount(parentName);
        loginAsParent(parentId, parentName);
        familyService.create("UT语法家庭");

        ChildCreateRequest request = new ChildCreateRequest();
        request.setNickname("UT语法娃");
        request.setGradeCode("G3");
        request.setPin("1234");
        request.setRelationLabel("妈妈");
        ChildCreateVO child = childService.create(request);
        childId = child.getChildId();

        // 换成孩子身份：study_child.user_id 指向孩子的 sys_user.id
        String kidAccountId = (String) queryOne("SELECT user_id FROM study_child WHERE id = ?", childId)
                .get("user_id");
        loginAsKid(kidAccountId, child.getUsername());

        seedGrammarPoint();
    }

    @AfterEach
    void cleanGrammarData() {
        try {
            jdbc.update("DELETE FROM study_grammar_point WHERE grammar_code LIKE 'UT-%'");
            jdbc.update("DELETE FROM study_error_pool WHERE child_id IN "
                    + "(SELECT id FROM study_child WHERE nickname LIKE 'UT%')");
        } catch (Exception e) {
            System.out.println("[StudyGrammarServiceTest] 清理测试数据失败：" + e.getMessage());
        }
    }

    /**
     * 造一个带语料的语法点。
     */
    private void seedGrammarPoint() {
        jdbc.update("INSERT INTO study_grammar_point (id, grammar_code, title, rule, example, counter_example, "
                        + "exam_type, subject_code, transform_variants, status, del_flag, create_by, create_time) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,1,0,'ut',NOW())",
                "ut_gp_" + unique(),
                GRAMMAR_CODE_PREFIX + unique(),
                "want to do sth",
                "want 后面接动词原形。",
                "I want to go home.",
                "I want going home.",
                "语法填空",
                "EN",
                TRANSFORM_LIBRARY);
    }

    // ------------------------------------------------------------------ TC-05-01

    @Test
    @DisplayName("TC-05-01 句子变形器五态输出")
    void transform_returnsFiveVariantsEachWithUsage() {
        GrammarTransformRequest request = new GrammarTransformRequest();
        request.setZhMeaning("我想去商店");

        GrammarTransformResultVO result = grammarService.transform(request);

        assertThat(result.getSource()).isEqualTo("library");
        assertThat(result.getVariants()).hasSize(5);
        List<String> kinds = result.getVariants().stream()
                .map(GrammarTransformVO::getKind).collect(Collectors.toList());
        assertThat(kinds).containsExactly("LITERAL", "COLLOQUIAL", "WRITTEN", "EXAM_SAFE", "WRONG");
        for (GrammarTransformVO variant : result.getVariants()) {
            assertThat(variant.getText()).isNotBlank();
            assertThat(variant.getUsage()).isNotBlank();
            assertThat(variant.getName()).isNotBlank();
        }
    }

    // ------------------------------------------------------------------ TC-05-02

    @Test
    @DisplayName("TC-05-02 错误示范版明确标错")
    void transform_wrongVariantIsClearlyMarked() {
        GrammarTransformRequest request = new GrammarTransformRequest();
        request.setZhMeaning("我想去商店");

        GrammarTransformResultVO result = grammarService.transform(request);
        GrammarTransformVO wrong = result.getVariants().stream()
                .filter(v -> GrammarTransformKind.WRONG.equals(v.getKind()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("没有返回错误示范版"));

        assertThat(wrong.getWrong()).isTrue();
        assertThat(wrong.getText()).startsWith(GrammarTransformKind.WRONG_MARK);
        assertThat(wrong.getText()).contains("✗ 不要这样写");
        assertThat(wrong.getText()).contains("I am wanting to go to the store.");
    }

    // ------------------------------------------------------------------ TC-05-03

    @Test
    @DisplayName("TC-05-03 错误即入口链路")
    void detect_marksErrorAndOffersThreePiecesWithOneRetry() {
        GrammarDetectResultVO result = grammarService.detect(detectRequest("He go to school every day."));

        assertThat(result.getHasError()).isTrue();
        assertThat(result.getSource()).isEqualTo("rule");
        assertThat(result.getErrors()).isNotEmpty();

        GrammarErrorVO error = result.getErrors().get(0);
        // 三件套：最小讲解 + 同类例句 + 反例
        assertThat(error.getRule()).isNotBlank();
        assertThat(error.getExample()).isNotBlank();
        assertThat(error.getCounterExample()).isNotBlank();
        // 可立即改一次
        assertThat(result.getCanRetry()).isTrue();
        assertThat(result.getMaxRetry()).isEqualTo(1);

        // 点开标出的错误 → 弹出三件套
        GrammarErrorDetailRequest detail = new GrammarErrorDetailRequest();
        detail.setSentence("He go to school every day.");
        detail.setErrorId(error.getErrorId());
        GrammarErrorFeedbackVO feedback = grammarService.errorDetail(detail);

        assertThat(feedback.getError()).isNotNull();
        assertThat(feedback.getError().getErrorId()).isEqualTo(error.getErrorId());
        assertThat(feedback.getError().getRule()).isNotBlank();
        assertThat(feedback.getError().getExample()).isNotBlank();
        assertThat(feedback.getError().getCounterExample()).isNotBlank();
        assertThat(feedback.getCanRetry()).isTrue();
        assertThat(feedback.getMaxRetry()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ TC-05-04

    @Test
    @DisplayName("TC-05-04 讲解从真实错误出发")
    void detect_explanationStartsFromTheChildSentence() {
        GrammarDetectResultVO result = grammarService.detect(detectRequest("He go to school every day."));

        GrammarErrorVO error = result.getErrors().get(0);
        assertThat(error.getFragment()).isEqualTo("He go");
        // 讲解直接引用孩子刚写的那一段，不是预防性灌输
        assertThat(error.getRule()).contains("He go");
        assertThat(error.getRule()).contains("第三人称单数");
        // 讲解能落到改正后的句子
        assertThat(error.getFix()).isEqualTo("He goes to school every day.");
    }

    // ------------------------------------------------------------------ TC-05-05

    @Test
    @DisplayName("TC-05-05 考纲题型标注")
    void listPoints_everyPointCarriesValidExamType() {
        GrammarPointListVO list = grammarService.listPoints(null);
        assertThat(list.getItems()).isNotEmpty();
        for (GrammarPointVO point : list.getItems()) {
            assertThat(point.getExamType()).isIn("单选", "完形", "语法填空", "书面表达");
        }

        // 按题型过滤
        GrammarPointListVO filtered = grammarService.listPoints("语法填空");
        assertThat(filtered.getItems()).isNotEmpty();
        for (GrammarPointVO point : filtered.getItems()) {
            assertThat(point.getExamType()).isEqualTo("语法填空");
        }

        // 语法点详情带三件套
        String code = filtered.getItems().get(0).getGrammarCode();
        GrammarPointVO detail = grammarService.getPoint(code);
        assertThat(detail.getRule()).isNotBlank();
        assertThat(detail.getExample()).isNotBlank();
        assertThat(detail.getCounterExample()).isNotBlank();

        // 库里不允许出现没标题型或标了非法题型的启用语法点
        Integer bad = count("SELECT COUNT(*) FROM study_grammar_point WHERE del_flag = 0 AND status = 1 "
                + "AND (exam_type IS NULL OR exam_type NOT IN ('单选','完形','语法填空','书面表达'))");
        assertThat(bad).isZero();

        // 非法题型直接被拒
        assertThatThrownBy(() -> grammarService.listPoints("填空题"))
                .isInstanceOf(JeecgBootException.class);
    }

    // ------------------------------------------------------------------ TC-05-06

    @Test
    @DisplayName("TC-05-06 错句入池与收藏馆")
    void detect_wrongSentenceEntersErrorPoolAndShowsInCollection() {
        String sentence = "He go to school every day.";
        GrammarDetectResultVO result = grammarService.detect(detectRequest(sentence));

        assertThat(result.getErrorCode()).isNotBlank();

        // 错句落在统一错题池里（不自建错句表）
        Map<String, Object> row = queryOne("SELECT * FROM study_error_pool WHERE error_code = ?",
                result.getErrorCode());
        assertThat(row).isNotNull();
        assertThat(String.valueOf(row.get("source_module"))).isEqualTo("GRAMMAR");
        assertThat(String.valueOf(row.get("content_type"))).isEqualTo("SENTENCE");
        assertThat(String.valueOf(row.get("child_id"))).isEqualTo(childId);
        assertThat(String.valueOf(row.get("raw_answer"))).isEqualTo(sentence);
        assertThat(String.valueOf(row.get("judge_source"))).isEqualTo("rule");
        assertThat(String.valueOf(row.get("error_cause"))).isNotBlank();

        // 收藏馆筛得到这条错句
        GrammarCollectionResultVO collection = grammarService.collection(null);
        assertThat(collection.getTotal()).isGreaterThanOrEqualTo(1);
        boolean hit = collection.getItems().stream()
                .anyMatch(item -> sentence.equals(item.getOriginalSentence()));
        assertThat(hit).isTrue();

        GrammarCollectionVO item = collection.getItems().stream()
                .filter(i -> sentence.equals(i.getOriginalSentence()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("收藏馆里没有这条错句"));
        assertThat(item.getCorrectedSentence()).isEqualTo("He goes to school every day.");
        assertThat(item.getErrorCode()).isEqualTo(result.getErrorCode());

        // 按错因筛选
        GrammarCollectionResultVO filtered = grammarService.collection("概念不清");
        assertThat(filtered.getItems()).isNotEmpty();

        // 幂等：同一句话重复提交只入池一次
        int before = count("SELECT COUNT(*) FROM study_error_pool WHERE child_id = ?", childId);
        grammarService.detect(detectRequest(sentence));
        int after = count("SELECT COUNT(*) FROM study_error_pool WHERE child_id = ?", childId);
        assertThat(after).isEqualTo(before);
    }

    // ------------------------------------------------------------------ 工具

    private GrammarDetectRequest detectRequest(String sentence) {
        GrammarDetectRequest request = new GrammarDetectRequest();
        request.setSentence(sentence);
        return request;
    }
}
