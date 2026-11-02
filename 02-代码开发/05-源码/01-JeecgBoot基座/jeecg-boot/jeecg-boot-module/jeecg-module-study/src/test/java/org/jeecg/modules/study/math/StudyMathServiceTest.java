package org.jeecg.modules.study.math;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.study.common.enums.JudgeSource;
import org.jeecg.modules.study.error.constant.ErrorCause;
import org.jeecg.modules.study.error.service.StudyErrorPoolService;
import org.jeecg.modules.study.error.vo.ErrorPoolItemVO;
import org.jeecg.modules.study.family.service.StudyChildService;
import org.jeecg.modules.study.family.service.StudyFamilyService;
import org.jeecg.modules.study.family.vo.ChildCreateRequest;
import org.jeecg.modules.study.family.vo.ChildCreateVO;
import org.jeecg.modules.study.judge.engine.JudgeRequest;
import org.jeecg.modules.study.judge.engine.JudgeResult;
import org.jeecg.modules.study.judge.service.JudgeEngine;
import org.jeecg.modules.study.math.engine.MotherVariantRuleEngine;
import org.jeecg.modules.study.math.entity.StudyMotherQuestion;
import org.jeecg.modules.study.math.entity.StudyQuestionVariant;
import org.jeecg.modules.study.math.mapper.StudyMotherQuestionMapper;
import org.jeecg.modules.study.math.mapper.StudyQuestionVariantMapper;
import org.jeecg.modules.study.math.service.StudyMathService;
import org.jeecg.modules.study.math.vo.ConditionTypeVO;
import org.jeecg.modules.study.math.vo.GeneratedVariantVO;
import org.jeecg.modules.study.math.vo.MathProgressVO;
import org.jeecg.modules.study.math.vo.MotherDetailVO;
import org.jeecg.modules.study.math.vo.MotherListItemVO;
import org.jeecg.modules.study.math.vo.MotherPassCommand;
import org.jeecg.modules.study.math.vo.MotherPassResultVO;
import org.jeecg.modules.study.math.vo.MotherSaveCommand;
import org.jeecg.modules.study.math.vo.MotherStepVO;
import org.jeecg.modules.study.math.vo.NarrativeCommand;
import org.jeecg.modules.study.math.vo.NarrativeResultVO;
import org.jeecg.modules.study.math.vo.StepTagVO;
import org.jeecg.modules.study.math.vo.VariantAnswerCommand;
import org.jeecg.modules.study.math.vo.VariantAnswerResultVO;
import org.jeecg.modules.study.math.vo.VariantGenerateResultVO;
import org.jeecg.modules.study.math.vo.VariantResultCommand;
import org.jeecg.modules.study.math.vo.VariantVO;
import org.jeecg.modules.study.support.StudyTestSupport;

/**
 * 数学母题模块自动化测试（TC-04-01 … TC-04-12）。
 *
 * <p>跑在真实的 {@code jeecg-study} 库上：五段校验、三层变式的可复现性、
 * 过关判据、步骤级错因打标、闯关排序、关卡进度都要落到真实数据上才验得准。</p>
 *
 * <p><b>测试数据约定</b>：母题标题与考点业务ID一律以 {@code UT} 开头，
 * 便于每轮结束后清理。</p>
 *
 * @author jeecg-study
 */
class StudyMathServiceTest extends StudyTestSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 关键关系（口述判据的关键词来源） */
    private static final String KEY_RELATION = "路程和 = 速度和 × 相遇时间";

    /** 完整口述：三个关键词全命中 */
    private static final String FULL_NARRATIVE = "路程和等于速度和乘相遇时间";

    /** 与关键关系无关的口述 */
    private static final String UNRELATED_NARRATIVE = "我喜欢去公园里散步，路上还看见了小狗。";

    /** 部分命中的口述 */
    private static final String PARTIAL_NARRATIVE = "我先算了路程和";

    @Autowired
    private StudyMathService mathService;

    @Autowired
    private StudyFamilyService familyService;

    @Autowired
    private StudyChildService childService;

    @Autowired
    private StudyErrorPoolService errorPoolService;

    @Autowired
    private JudgeEngine judgeEngine;

    @Autowired
    private MotherVariantRuleEngine ruleEngine;

    @Autowired
    private StudyMotherQuestionMapper motherMapper;

    @Autowired
    private StudyQuestionVariantMapper variantMapper;

    private String parentUserId;

    private String childId;

    private String childCode;

    private String childUserId;

    @BeforeEach
    void setUp() {
        String parentName = "ut_math_" + unique();
        parentUserId = givenParentAccount(parentName);
        loginAsParent(parentUserId, parentName);
        familyService.create("UT数学家庭");
        ChildCreateVO child = childService.create(new ChildCreateRequest()
                .setNickname("UT数学娃" + unique())
                .setRelationLabel("妈妈"));
        childId = child.getChildId();
        childCode = child.getChildCode();
        Map<String, Object> row = queryOne("SELECT user_id FROM study_child WHERE id = ?", childId);
        Object userId = row == null ? null : row.get("user_id");
        childUserId = userId == null ? null : String.valueOf(userId);
    }

    @Override
    protected void cleanTestData() {
        try {
            String table = "study_practice_record_"
                    + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
            String motherScope = "(SELECT mother_code FROM study_mother_question WHERE title LIKE 'UT%')";
            String variantScope = "(SELECT variant_code FROM study_question_variant WHERE mother_code IN "
                    + motherScope + ")";
            jdbc.update("DELETE FROM study_review_schedule WHERE error_code IN "
                    + "(SELECT error_code FROM study_error_pool WHERE content_id IN " + variantScope + ")");
            jdbc.update("DELETE FROM study_error_pool WHERE content_id IN " + variantScope);
            jdbc.update("DELETE FROM " + table + " WHERE content_id IN " + variantScope);
            jdbc.update("DELETE FROM study_question_variant WHERE mother_code IN " + motherScope);
            jdbc.update("DELETE FROM study_mother_question WHERE title LIKE 'UT%'");
            jdbc.update("DELETE FROM study_knowledge_point WHERE point_code LIKE 'UT%'");
        } catch (Exception e) {
            // 清理失败不影响断言结果，下一轮仍按唯一命名隔离
            System.out.println("[StudyMathServiceTest] 清理测试数据失败：" + e.getMessage());
        }
        super.cleanTestData();
    }

    // ================================================================= TC-04-01

    @Test
    @DisplayName("TC-04-01 母题五段齐全才可保存")
    void tc04_01_motherNeedsAllFiveSections() {
        assertSectionMissing("题型名", command -> command.setTitle("  "));
        assertSectionMissing("已知条件类型", command -> command.setConditionTypes(new ArrayList<>()));
        assertSectionMissing("关键关系", command -> command.setKeyRelation(null));
        assertSectionMissing("解题步骤链", command -> command.setSteps(new ArrayList<>()));
        assertSectionMissing("易错点清单", command -> command.setPitfalls(new ArrayList<>()));

        // 五段齐全才允许保存
        MotherDetailVO detail = mathService.saveMother(fullCommand("UT齐全" + unique()));
        assertThat(detail.getMotherCode()).startsWith("MTH-MA-");
        assertThat(detail.getTitle()).startsWith("UT齐全");
        assertThat(detail.getConditionTypes()).hasSize(3);
        assertThat(detail.getSteps()).hasSize(2);
        assertThat(detail.getPitfalls()).hasSize(2);
    }

    @Test
    @DisplayName("TC-04-01 母题保存幂等（同学科同考点同题型名只更新不新建）")
    void tc04_01_motherSaveIsIdempotent() {
        MotherDetailVO first = mathService.saveMother(fullCommand("UT幂等" + unique()));
        String pointCode = mathService.motherDetail(first.getMotherCode()).getKnowledgePointCode();
        MotherDetailVO second = mathService.saveMother(fullCommand(first.getTitle(), pointCode));
        assertThat(second.getMotherCode()).isEqualTo(first.getMotherCode());
        assertThat(count("SELECT COUNT(*) FROM study_mother_question WHERE mother_code = ?",
                first.getMotherCode())).isEqualTo(1);
    }

    // ================================================================= TC-04-02

    @Test
    @DisplayName("TC-04-02 五段内容正确展示")
    void tc04_02_motherDetailShowsFiveSectionsInOrder() {
        String title = "UT展示" + unique();
        String code = createMother(title);
        MotherDetailVO detail = mathService.motherDetail(code);

        // 依次展示：题型名 / 已知条件类型 / 关键关系 / 步骤链 / 易错点
        assertThat(detail.getSectionOrder())
                .containsExactly("TITLE", "CONDITION_TYPES", "KEY_RELATION", "STEPS", "PITFALLS");
        assertThat(detail.getTitle()).isEqualTo(title);
        assertThat(detail.getConditionTypes()).hasSize(3);
        assertThat(detail.getConditionTypes().get(0).getKey()).isEqualTo("S");
        assertThat(detail.getKeyRelation()).isEqualTo(KEY_RELATION);
        assertThat(detail.getSteps()).hasSize(2);
        assertThat(detail.getSteps().get(1).getFormula()).isEqualTo("S / (v1 + v2)");
        assertThat(detail.getPitfalls()).containsExactly("把两车速度当成一辆", "忘了单位换算");

        // 关键关系高亮
        assertThat(detail.isKeyRelationHighlighted()).isTrue();
        assertThat(detail.getHighlightSection()).isEqualTo("KEY_RELATION");
    }

    // ================================================================= TC-04-03

    @Test
    @DisplayName("TC-04-03 三层变式生成")
    void tc04_03_generateThreeLayerVariants() {
        String code = createMother("UT三层" + unique());
        VariantGenerateResultVO result = mathService.generateVariants(code, childId);

        assertThat(result.getVariants()).hasSize(3);
        assertThat(result.getVariants()).extracting(VariantVO::getLayer).containsExactly(1, 2, 3);
        assertThat(result.getVariants()).extracting(VariantVO::getLayerName)
                .containsExactly("换数字", "换情境", "逆向");
        assertThat(result.getPassVariantCount()).isEqualTo(3);
        for (VariantVO variant : result.getVariants()) {
            assertThat(variant.getStem()).isNotBlank();
            assertThat(variant.getVariantCode()).startsWith("QST-VAR-");
        }
    }

    @Test
    @DisplayName("TC-04-03 变式生成幂等（同一孩子同一母题同一层级只生成一条）")
    void tc04_03_variantGenerationIsIdempotent() {
        String code = createMother("UT变式幂等" + unique());
        VariantGenerateResultVO first = mathService.generateVariants(code, childId);
        VariantGenerateResultVO second = mathService.generateVariants(code, childId);

        assertThat(second.isDuplicated()).isTrue();
        assertThat(second.getVariants()).hasSize(3);
        assertThat(second.getVariants()).extracting(VariantVO::getVariantCode)
                .containsExactlyElementsOf(first.getVariants().stream()
                        .map(VariantVO::getVariantCode).collect(java.util.stream.Collectors.toList()));
        assertThat(count("SELECT COUNT(*) FROM study_question_variant WHERE mother_code = ? AND child_id = ?",
                code, childId)).isEqualTo(3);
    }

    // ================================================================= TC-04-04

    @Test
    @DisplayName("TC-04-04 变式答案可被规则验算")
    void tc04_04_variantAnswersAreVerifiableByRule() {
        String code = createMother("UT验算" + unique());
        mathService.generateVariants(code, childId);
        StudyMotherQuestion mother = motherMapper.selectByMotherCode(code);
        List<StudyQuestionVariant> variants = variantMapper.selectByMotherAndChild(code, childId);
        assertThat(variants).hasSize(3);

        for (StudyQuestionVariant variant : variants) {
            String answer = variant.getAnswer();
            assertThat(answer).isNotBlank();

            // 用判定策略验算：标准答案与作答一致 → 判定为正确
            JudgeRequest request = new JudgeRequest();
            request.setContentType("NUMERIC");
            request.setExpectedAnswer(answer);
            request.setActualAnswer(answer);
            request.setTolerance(variant.getAnswerTolerance());
            JudgeResult judge = judgeEngine.judge(request);
            assertThat(judge.isCorrect()).as("变式 %s 的答案应能被判为正确", variant.getVariantCode()).isTrue();
            // 判定来自规则，不是模型
            assertThat(judge.getJudgeSource()).isEqualTo(JudgeSource.RULE.getCode());

            // 答案由规则产出：规则引擎重算一遍，结果必须一致
            GeneratedVariantVO draft = ruleEngine.generateLayer(mother, variant.getLayer());
            assertThat(draft.getAnswer()).isEqualTo(answer);
        }
    }

    @Test
    @DisplayName("TC-04-04 变式生成可复现（同一母题必然算出同样答案）")
    void tc04_04_variantGenerationIsReproducible() {
        String code = createMother("UT可复现" + unique());
        mathService.generateVariants(code, childId);
        StudyMotherQuestion mother = motherMapper.selectByMotherCode(code);

        for (int layer = 1; layer <= 3; layer++) {
            GeneratedVariantVO first = ruleEngine.generateLayer(mother, layer);
            GeneratedVariantVO second = ruleEngine.generateLayer(mother, layer);
            assertThat(second.getAnswer()).isEqualTo(first.getAnswer());
            assertThat(second.getStem()).isEqualTo(first.getStem());
        }
    }

    // ================================================================= TC-04-05

    @Test
    @DisplayName("TC-04-05 generatedBy 不出现纯 MODEL")
    void tc04_05_generatedByNeverPureModel() {
        String code = createMother("UT生成方式" + unique());
        mathService.generateVariants(code, childId);

        List<String> values = jdbc.queryForList(
                "SELECT DISTINCT generated_by FROM study_question_variant WHERE mother_code = ?",
                String.class, code);
        assertThat(values).isNotEmpty();
        for (String value : values) {
            assertThat(value.toUpperCase()).isIn("RULE", "RULE+MODEL");
            assertThat(value.toUpperCase()).isNotEqualTo("MODEL");
        }
        for (StudyQuestionVariant variant : variantMapper.selectByMotherAndChild(code, childId)) {
            assertThat(variant.getGeneratedBy()).isIn("RULE", "RULE+MODEL");
        }
    }

    // ================================================================= TC-04-06

    @Test
    @DisplayName("TC-04-06 过关判据：变式必须全对")
    void tc04_06_passRequiresAllVariantsCorrect() {
        String code = createMother("UT变式未全对" + unique());
        mathService.generateVariants(code, childId);
        List<StudyQuestionVariant> variants = variantMapper.selectByMotherAndChild(code, childId);
        loginKid();

        List<VariantResultCommand> results = new ArrayList<>();
        results.add(correctResult(variants.get(0)));
        results.add(correctResult(variants.get(1)));
        results.add(wrongResult(variants.get(2)));

        MotherPassCommand command = new MotherPassCommand()
                .setVariantResults(results)
                .setNarrative(FULL_NARRATIVE);
        MotherPassResultVO vo = mathService.submitPass(code, command);

        assertThat(vo.isVariantPassed()).isFalse();
        assertThat(vo.isPassed()).isFalse();
        assertThat(vo.getVariantCorrectCount()).isEqualTo(2);
        assertThat(vo.getVariantRequiredCount()).isEqualTo(3);
        // 口述讲对了也不能放过变式
        assertThat(vo.isNarrativePassed()).isTrue();
        // 给出仍需完成的提示
        assertThat(vo.getHint()).isNotBlank();
        assertThat(vo.getHint()).contains("1");
    }

    // ================================================================= TC-04-07

    @Test
    @DisplayName("TC-04-07 过关判据：口述必须通过（只给追问，不给答案）")
    void tc04_07_passRequiresNarrativeAndNeverRevealsAnswer() throws Exception {
        String code = createMother("UT口述未过" + unique());
        mathService.generateVariants(code, childId);
        List<StudyQuestionVariant> variants = variantMapper.selectByMotherAndChild(code, childId);
        loginKid();

        List<VariantResultCommand> results = new ArrayList<>();
        for (StudyQuestionVariant variant : variants) {
            results.add(correctResult(variant));
        }
        MotherPassCommand command = new MotherPassCommand()
                .setVariantResults(results)
                .setNarrative(UNRELATED_NARRATIVE);
        MotherPassResultVO vo = mathService.submitPass(code, command);

        // 变式全对，但口述与关键关系无关 → 不判过关
        assertThat(vo.isVariantPassed()).isTrue();
        assertThat(vo.isNarrativePassed()).isFalse();
        assertThat(vo.isPassed()).isFalse();

        // 给一条苏格拉底式追问
        assertThat(vo.getFollowUpQuestion()).isNotBlank();
        assertThat(vo.getFollowUpQuestion()).endsWith("？");
        // 追问不复述关键关系，也不含关键词
        assertThat(vo.getFollowUpQuestion()).doesNotContain(KEY_RELATION);
        assertThat(vo.getFollowUpQuestion()).doesNotContain("路程和", "速度和", "相遇时间");
        // 响应里没有任何答案字段
        assertThat(MAPPER.writeValueAsString(vo)).doesNotContain("answer");
        assertThat(MAPPER.writeValueAsString(vo)).doesNotContain("正确答案是");
    }

    // ================================================================= TC-04-08

    @Test
    @DisplayName("TC-04-08 口述关键词命中反馈")
    void tc04_08_narrativeReturnsHitAndMissingKeywords() throws Exception {
        String code = createMother("UT口述命中" + unique());
        mathService.generateVariants(code, childId);
        loginKid();

        NarrativeResultVO vo = mathService.submitNarrative(code,
                new NarrativeCommand().setNarrative(PARTIAL_NARRATIVE));

        // 部分命中 → 返回命中与缺失关键词
        assertThat(vo.getHitKeywords()).containsExactly("路程和");
        assertThat(vo.getMissingKeywords()).containsExactly("速度和", "相遇时间");
        // 不直接判对
        assertThat(vo.isNarrativePassed()).isFalse();
        assertThat(vo.getFollowUpQuestion()).isNotBlank();
        // 不给答案
        assertThat(MAPPER.writeValueAsString(vo)).doesNotContain("answer");
    }

    @Test
    @DisplayName("TC-04-08 口述讲全关键词才判过")
    void tc04_08_narrativePassesWhenAllKeywordsHit() {
        String code = createMother("UT口述全中" + unique());
        mathService.generateVariants(code, childId);
        loginKid();

        NarrativeResultVO vo = mathService.submitNarrative(code,
                new NarrativeCommand().setNarrative(FULL_NARRATIVE));

        assertThat(vo.isNarrativePassed()).isTrue();
        assertThat(vo.getHitKeywords()).containsExactly("路程和", "速度和", "相遇时间");
        assertThat(vo.getMissingKeywords()).isEmpty();
        assertThat(vo.getFollowUpQuestion()).isNull();
    }

    // ================================================================= TC-04-09

    @Test
    @DisplayName("TC-04-09 步骤级错因打标")
    void tc04_09_stepLevelErrorCauseIsRecorded() throws Exception {
        String code = createMother("UT步骤打标" + unique());
        mathService.generateVariants(code, childId);
        List<StudyQuestionVariant> variants = variantMapper.selectByMotherAndChild(code, childId);
        StudyQuestionVariant variant = variants.get(0);

        loginKid();
        VariantAnswerResultVO vo = mathService.answerVariant(code, variant.getVariantCode(),
                new VariantAnswerCommand()
                        .setAnswer("999999")
                        .setErrorCause(ErrorCause.CALCULATION_ERROR)
                        .setStepTags(Arrays.asList(new StepTagVO()
                                .setStepIndex(2)
                                .setErrorCause(ErrorCause.CALCULATION_ERROR))));

        assertThat(vo.isCorrect()).isFalse();
        assertThat(vo.getStepTags()).hasSize(1);
        assertThat(vo.getStepTags().get(0).getStepIndex()).isEqualTo(2);
        assertThat(vo.getErrorCode()).isNotBlank();

        // step_tags 落库：记录步骤与错因
        String table = "study_practice_record_"
                + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        Map<String, Object> row = queryOne("SELECT step_tags FROM " + table
                + " WHERE content_id = ? AND child_id = ?", variant.getVariantCode(), childId);
        assertThat(row).isNotNull();
        Map<String, Object> meta = MAPPER.readValue(String.valueOf(row.get("step_tags")), Map.class);
        List<Map<String, Object>> tags = MAPPER.convertValue(meta.get("tags"),
                new TypeReference<List<Map<String, Object>>>() {
                });
        assertThat(tags).hasSize(1);
        assertThat(tags.get(0).get("stepIndex")).isEqualTo(2);
        assertThat(tags.get(0).get("errorCause")).isEqualTo(ErrorCause.CALCULATION_ERROR);

        // 错因数据可查询（统一错题池，不是数学域自建列表）
        List<ErrorPoolItemVO> pool = errorPoolService.listByChild(childId);
        assertThat(pool).anyMatch(item -> variant.getVariantCode().equals(item.getContentId())
                && ErrorCause.CALCULATION_ERROR.equals(item.getErrorCause())
                && MathConstantsSource.MODULE.equals(item.getSourceModule()));

        // 步骤打标可查询
        List<StepTagVO> query = mathService.listStepTags(code, childId);
        assertThat(query).anyMatch(tag -> Integer.valueOf(2).equals(tag.getStepIndex())
                && ErrorCause.CALCULATION_ERROR.equals(tag.getErrorCause()));
    }

    // ================================================================= TC-04-10

    @Test
    @DisplayName("TC-04-10 错因指导关卡优先级")
    void tc04_10_errorProneLevelMovesForward() {
        String pointCode = createPoint(new BigDecimal("8.00"));
        String errorMother = createMother("UT连续错", pointCode);
        String masteredMother = createMother("UT已会了", pointCode);

        loginKid();
        // 连续答错两次（两次作答不同 → 两条记录，构成「连续错」）
        mathService.generateVariants(errorMother, childId);
        List<StudyQuestionVariant> errorVariants = variantMapper.selectByMotherAndChild(errorMother, childId);
        answerWrong(errorVariants.get(0), "111111");
        answerWrong(errorVariants.get(1), "222222");

        // 另一关做对一次 → 掌握度更高
        mathService.generateVariants(masteredMother, childId);
        List<StudyQuestionVariant> masteredVariants = variantMapper.selectByMotherAndChild(masteredMother, childId);
        answerCorrect(masteredVariants.get(0));

        List<MotherListItemVO> list = mathService.motherList(childId);
        int errorIndex = indexOf(list, errorMother);
        int masteredIndex = indexOf(list, masteredMother);
        assertThat(errorIndex).isGreaterThanOrEqualTo(0);
        assertThat(masteredIndex).isGreaterThanOrEqualTo(0);

        // 连续错的关卡排到已掌握的关卡之前
        assertThat(errorIndex).isLessThan(masteredIndex);
        MotherListItemVO errorItem = list.get(errorIndex);
        assertThat(errorItem.getConsecutiveErrors()).isGreaterThanOrEqualTo(2);
        assertThat(errorItem.getErrorCount()).isGreaterThanOrEqualTo(2);
        assertThat(errorItem.getMastery()).isLessThan(list.get(masteredIndex).getMastery());
    }

    // ================================================================= TC-04-11

    @Test
    @DisplayName("TC-04-11 闯关顺序 = 考纲优先级")
    void tc04_11_levelOrderFollowsOutlineWeightThenMastery() {
        String highPoint = createPoint(new BigDecimal("10.00"));
        String lowPoint = createPoint(new BigDecimal("5.00"));

        // 先建权重 10 的甲关并做对一次，再建权重 10 的乙关（未作答），最后建权重 5 的关卡
        String highFirst = createMother("UT权重10甲", highPoint);
        String highSecond = createMother("UT权重10乙", highPoint);
        String low = createMother("UT权重5", lowPoint);

        loginKid();
        mathService.generateVariants(highFirst, childId);
        List<StudyQuestionVariant> variants = variantMapper.selectByMotherAndChild(highFirst, childId);
        answerCorrect(variants.get(0));

        List<MotherListItemVO> list = mathService.motherList(childId);
        int highSecondIndex = indexOf(list, highSecond);
        int highFirstIndex = indexOf(list, highFirst);
        int lowIndex = indexOf(list, low);

        // 权重降序：权重 10 的关卡都在权重 5 的前面
        assertThat(highFirstIndex).isLessThan(lowIndex);
        assertThat(highSecondIndex).isLessThan(lowIndex);
        // 同权重内掌握度升序：没练过的（0.5）排在已做对的（1.0）前面
        assertThat(highSecondIndex).isLessThan(highFirstIndex);
        // 顺序由权重与掌握度决定，不随机：再取一次顺序必须一致
        List<MotherListItemVO> again = mathService.motherList(childId);
        assertThat(again.stream().map(MotherListItemVO::getMotherCode).collect(
                java.util.stream.Collectors.toList()))
                .isEqualTo(list.stream().map(MotherListItemVO::getMotherCode).collect(
                        java.util.stream.Collectors.toList()));
        // 权重确实是降序的
        BigDecimal firstWeight = list.get(0).getOutlineWeight();
        assertThat(firstWeight).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    // ================================================================= TC-04-12

    @Test
    @DisplayName("TC-04-12 关卡进度以母题为单位")
    void tc04_12_progressCountsMotherTypes() throws Exception {
        String code = createMother("UT进度" + unique());
        mathService.generateVariants(code, childId);
        List<StudyQuestionVariant> variants = variantMapper.selectByMotherAndChild(code, childId);

        loginKid();
        for (StudyQuestionVariant variant : variants) {
            answerCorrect(variant);
        }

        MathProgressVO progress = mathService.progress(childId);
        assertThat(progress.getMasteredTypeCount()).isEqualTo(1);
        assertThat(progress.getMasteredMotherCodes()).containsExactly(code);
        // 文案按「类」计，不按「道」计
        assertThat(progress.getProgressText()).isEqualTo("这类题我会了 1 类");
        assertThat(progress.getProgressText()).doesNotContain("道");
        String json = MAPPER.writeValueAsString(progress);
        assertThat(json).doesNotContain("道");
        assertThat(json).doesNotContain("questionCount");
    }

    // ================================================================= 辅助

    /** 缺段断言：必须返回 4007 并提示缺哪一段。 */
    private void assertSectionMissing(String expectedLabel, Consumer<MotherSaveCommand> mutator) {
        MotherSaveCommand command = fullCommand("UT缺段" + unique());
        mutator.accept(command);
        assertThatThrownBy(() -> mathService.saveMother(command))
                .isInstanceOf(JeecgBootException.class)
                .satisfies(error -> {
                    JeecgBootException exception = (JeecgBootException) error;
                    assertThat(exception.getErrCode()).isEqualTo(4007);
                    assertThat(exception.getMessage()).contains(expectedLabel);
                });
    }

    /** 完整的母题入参（五段齐全）。 */
    private MotherSaveCommand fullCommand(String title) {
        return fullCommand(title, createPoint(new BigDecimal("10.00")));
    }

    /** 完整的母题入参（五段齐全，挂到指定考点）。 */
    private MotherSaveCommand fullCommand(String title, String pointCode) {
        MotherSaveCommand command = new MotherSaveCommand();
        command.setTitle(title);
        command.setSubjectCode("MA");
        command.setKnowledgePointCode(pointCode);
        command.setConditionTypes(Arrays.asList(
                condition("S", "总路程", Arrays.asList("总路程", "往返路程"), "120"),
                condition("v1", "甲速度", Arrays.asList("甲速度"), "40"),
                condition("v2", "乙速度", Arrays.asList("乙速度"), "50")));
        command.setKeyRelation(KEY_RELATION);
        command.setSteps(Arrays.asList(
                step(1, "求速度和", "v1 + v2", "先把两个速度加起来"),
                step(2, "求相遇时间", "S / (v1 + v2)", "再用总路程除以速度和")));
        command.setPitfalls(Arrays.asList("把两车速度当成一辆", "忘了单位换算"));
        command.setPassVariantCount(3);
        return command;
    }

    private ConditionTypeVO condition(String key, String label, List<String> variants, String value) {
        return new ConditionTypeVO()
                .setKey(key)
                .setLabel(label)
                .setVariants(variants)
                .setValue(new BigDecimal(value));
    }

    private MotherStepVO step(int index, String title, String formula, String note) {
        return new MotherStepVO().setIndex(index).setTitle(title).setFormula(formula).setNote(note);
    }

    /** 造一个带分值权重的考点（坐标树归考点域维护，测试里直接落库）。 */
    private String createPoint(BigDecimal weight) {
        String pointCode = "UT-KPT-MA-" + unique();
        jdbc.update("INSERT INTO study_knowledge_point (id, point_code, level, subject_code, name,"
                + " question_type, score_weight, version, effective_from, status, sort_no, del_flag,"
                + " create_time, update_time) VALUES (?, ?, 4, 'MA', ?, 'NUMERIC', ?, 'v1', CURDATE(), 1, 1, 0,"
                + " NOW(), NOW())",
                java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 32),
                pointCode, "UT考点" + unique(), weight);
        return pointCode;
    }

    /** 造一道母题并返回业务ID。 */
    private String createMother(String title) {
        return mathService.saveMother(fullCommand(title)).getMotherCode();
    }

    /** 造一道挂在指定考点下的母题并返回业务ID。 */
    private String createMother(String title, String pointCode) {
        return mathService.saveMother(fullCommand(title, pointCode)).getMotherCode();
    }

    /** 切换成孩子身份。 */
    private void loginKid() {
        loginAsKid(childUserId, "ut_math_kid_" + unique());
    }

    /** 变式答对。 */
    private void answerCorrect(StudyQuestionVariant variant) {
        mathService.answerVariant(variant.getMotherCode(), variant.getVariantCode(),
                new VariantAnswerCommand().setAnswer(variant.getAnswer()));
    }

    /** 变式答错（必须带错因）。 */
    private void answerWrong(StudyQuestionVariant variant, String wrongAnswer) {
        mathService.answerVariant(variant.getMotherCode(), variant.getVariantCode(),
                new VariantAnswerCommand()
                        .setAnswer(wrongAnswer)
                        .setErrorCause(ErrorCause.CALCULATION_ERROR));
    }

    private VariantResultCommand correctResult(StudyQuestionVariant variant) {
        return new VariantResultCommand()
                .setVariantCode(variant.getVariantCode())
                .setAnswer(variant.getAnswer());
    }

    private VariantResultCommand wrongResult(StudyQuestionVariant variant) {
        return new VariantResultCommand()
                .setVariantCode(variant.getVariantCode())
                .setAnswer("999999");
    }

    private int indexOf(List<MotherListItemVO> list, String motherCode) {
        for (int i = 0; i < list.size(); i++) {
            if (motherCode.equals(list.get(i).getMotherCode())) {
                return i;
            }
        }
        return -1;
    }

    /** 源码模块常量（避免测试里重复字面量）。 */
    private static final class MathConstantsSource {

        /** 来源模块：数学 */
        static final String MODULE = "MATH";

        private MathConstantsSource() {
        }
    }
}
