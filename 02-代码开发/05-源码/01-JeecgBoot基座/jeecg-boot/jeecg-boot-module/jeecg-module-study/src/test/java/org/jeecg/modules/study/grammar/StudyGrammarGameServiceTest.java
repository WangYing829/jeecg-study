package org.jeecg.modules.study.grammar;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.jeecg.modules.study.family.service.StudyChildService;
import org.jeecg.modules.study.family.service.StudyFamilyService;
import org.jeecg.modules.study.family.vo.ChildCreateRequest;
import org.jeecg.modules.study.family.vo.ChildCreateVO;
import org.jeecg.modules.study.grammar.gameplay.service.GrammarGameService;
import org.jeecg.modules.study.grammar.gameplay.vo.GrammarDetectiveJudgeRequest;
import org.jeecg.modules.study.grammar.gameplay.vo.GrammarDetectiveJudgeVO;
import org.jeecg.modules.study.grammar.gameplay.vo.GrammarDetectiveRoundVO;
import org.jeecg.modules.study.grammar.gameplay.vo.GrammarGameCollectionItemVO;
import org.jeecg.modules.study.grammar.gameplay.vo.GrammarGameCollectionResultVO;
import org.jeecg.modules.study.grammar.gameplay.vo.GrammarTransformJudgeRequest;
import org.jeecg.modules.study.grammar.gameplay.vo.GrammarTransformJudgeVO;
import org.jeecg.modules.study.grammar.gameplay.vo.GrammarTransformRoundVO;
import org.jeecg.modules.study.support.StudyTestSupport;

/**
 * 语法趣味玩法自动化测试（V3 / TC-05-09 … TC-05-12）。
 *
 * <p>三条红线在这里落地：<b>取题不给答案</b>（R-01）、<b>判定是确定性的</b>（R-02）、
 * <b>只看得到自己孩子的错句</b>（R-04）。</p>
 *
 * <p>跑在真实 {@code jeecg-study} 库上；测试数据一律 {@code UT} 前缀并在用例后清理。</p>
 *
 * @author jeecg-study
 */
class StudyGrammarGameServiceTest extends StudyTestSupport {

    /** 句子侦探题号 */
    private static final String DETECTIVE_CODE = "UT-GAM-DETECTIVE";

    /** 变形闯关题号 */
    private static final String TRANSFORM_CODE = "UT-GAM-TRANSFORM";

    /** 这一关的错句（会被规则 GR-ERR-05 命中） */
    private static final String WRONG_SENTENCE = "He go to school every day.";

    /** 变形闯关目标表达 */
    private static final String TARGET_SENTENCE = "The doctor asked me to eat an apple every morning.";

    /**
     * 句子侦探用的三句正例：规则一条都不命中。
     *
     * <p>写成常量数组，是为了让「按正文取 sentence_code」能一一对应，
     * 不再靠 {@code ORDER BY id} 的下标去猜哪一句是哪一句（id 是随机 UUID，顺序每轮都不同）。</p>
     */
    private static final String[] CLEAN_SENTENCES = {
            "I eat an apple every morning.",
            "We read books in the library every weekend.",
            "My bag is blue."
    };

    /** 孩子 A 的错句 */
    private static final String CHILD_A_SENTENCE = "He go to school every morning.";

    /** 孩子 B 的错句 */
    private static final String CHILD_B_SENTENCE = "I read an book last night.";

    @Autowired
    private GrammarGameService gameService;

    @Autowired
    private StudyFamilyService familyService;

    @Autowired
    private StudyChildService childService;

    /** 孩子 A 的 ID */
    private String childAId;

    /** 孩子 B 的 ID */
    private String childBId;

    /** 孩子 A 的登录账号 */
    private String childAAccount;

    /** 孩子 B 的登录账号 */
    private String childBAccount;

    // update-begin---author:mavis ---date:2026-10-31  for：【TC-05-11】两个家庭必须由两个家长账号各建一个——业务规则是「一个账号只能属于一个家庭」，同账号建第二个家庭必然被拒-----------
    @BeforeEach
    void givenKidsAndGameBank() {
        // 家长 A：家庭 A + 孩子 A
        String parentNameA = "ut_gg_a_" + unique();
        String parentIdA = givenParentAccount(parentNameA);
        loginAsParent(parentIdA, parentNameA);

        familyService.create("UT玩法家庭A");
        ChildCreateVO childA = newChild("UT玩法娃A");
        childAId = childA.getChildId();
        childAAccount = childA.getUsername();

        // 家长 B：**换一个账号**建家庭 B + 孩子 B
        String parentNameB = "ut_gg_b_" + unique();
        String parentIdB = givenParentAccount(parentNameB);
        loginAsParent(parentIdB, parentNameB);

        familyService.create("UT玩法家庭B");
        ChildCreateVO childB = newChild("UT玩法娃B");
        childBId = childB.getChildId();
        childBAccount = childB.getUsername();

        seedContextSentences();
        seedGameQuestions();

        loginAsKid(childAccountId(childAId), childAAccount);
    }
    // update-end---author:mavis ---date:2026-10-31  for：【TC-05-11】两个家庭必须由两个家长账号各建一个——业务规则是「一个账号只能属于一个家庭」，同账号建第二个家庭必然被拒-----------

    @AfterEach
    void cleanGameData() {
        try {
            // 行为表先清（孩子随后被基类清掉，别留孤儿行）
            jdbc.update("DELETE FROM study_grammar_game_record WHERE child_id IN "
                    + "(SELECT id FROM study_child WHERE nickname LIKE 'UT%')");
            jdbc.update("DELETE FROM study_grammar_game_question WHERE question_code LIKE 'UT-GAM-%'");
            jdbc.update("DELETE FROM study_sentence_context WHERE word_code LIKE 'UT-GAM-%'");
        } catch (Exception e) {
            System.out.println("[StudyGrammarGameServiceTest] 清理测试数据失败：" + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ TC-05-09

    @Test
    @DisplayName("TC-05-09 句子侦探取题不含答案")
    void detectiveNext_givesSentencesOnly() {
        GrammarDetectiveRoundVO round = gameService.detectiveNext();

        assertThat(round.getRoundId()).isNotBlank();
        assertThat(round.getSentences()).hasSize(4);
        for (int i = 0; i < round.getSentences().size(); i++) {
            assertThat(round.getSentences().get(i).getPosition()).isEqualTo(i + 1);
            assertThat(round.getSentences().get(i).getText()).isNotBlank();
        }

        // 响应 VO 里不能出现答案类字段名（answer / analysis）
        for (Class<?> type : new Class<?>[]{GrammarDetectiveRoundVO.class,
                org.jeecg.modules.study.grammar.gameplay.vo.GrammarDetectiveSentenceVO.class}) {
            for (Field field : type.getDeclaredFields()) {
                String name = field.getName().toLowerCase(Locale.ROOT);
                assertThat(name).doesNotContain("answer");
                assertThat(name).doesNotContain("analysis");
            }
        }

        // 更进一步：取题 VO 里连「哪句错」都不许出现
        for (Field field : GrammarDetectiveRoundVO.class.getDeclaredFields()) {
            String name = field.getName().toLowerCase(Locale.ROOT);
            assertThat(name).doesNotContain("wrong");
            assertThat(name).doesNotContain("correct");
        }
    }

    // ------------------------------------------------------------------ TC-05-10

    @Test
    @DisplayName("TC-05-10 句子侦探判定是确定性的")
    void detectiveJudge_isDeterministicAndRuleBased() {
        // 先故意指错
        GrammarDetectiveJudgeVO wrong = judgeDetective(1);
        assertThat(wrong.getCorrect()).isFalse();
        assertThat(wrong.getJudgeSource()).isEqualTo("rule");
        assertThat(wrong.getWrongPosition()).isNotNull();
        assertThat(wrong.getRule()).isNotBlank();
        assertThat(wrong.getExample()).isNotBlank();
        assertThat(wrong.getCounterExample()).isNotBlank();
        assertThat(wrong.getFix()).isNotBlank();
        // 没找对 → 错句进统一错题池
        assertThat(wrong.getErrorCode()).isNotBlank();

        // 再指对：位置由服务端按规则重算，不看前端传值
        GrammarDetectiveJudgeVO right = judgeDetective(wrong.getWrongPosition());
        assertThat(right.getCorrect()).isTrue();
        assertThat(right.getJudgeSource()).isEqualTo("rule");
        assertThat(right.getWrongSentence()).isEqualTo(WRONG_SENTENCE);

        // 同一关重复判定，结果稳定（幂等）
        GrammarDetectiveJudgeVO again = judgeDetective(wrong.getWrongPosition());
        assertThat(again.getCorrect()).isTrue();
        assertThat(again.getWrongPosition()).isEqualTo(wrong.getWrongPosition());
        assertThat(again.getFix()).isEqualTo(right.getFix());

        // 判定来源恒为 rule（R-02）
        int notRule = count("SELECT COUNT(*) FROM study_grammar_game_record WHERE child_id = ? "
                + "AND judge_source <> 'rule'", childAId);
        assertThat(notRule).isZero();
    }

    // ------------------------------------------------------------------ TC-05-11

    @Test
    @DisplayName("TC-05-11 错句收藏馆只返回自己孩子的错句")
    void collection_onlyReturnsOwnChildSentences() {
        // 孩子 A 写错一句（用本用例造的那一关，题库是确定的）
        assertThat(gameService.transformNext().getRoundId()).isNotBlank();
        GrammarTransformJudgeVO judgeA = judgeTransform(TRANSFORM_CODE, CHILD_A_SENTENCE);
        assertThat(judgeA.getCorrect()).isFalse();
        assertThat(judgeA.getErrorCode()).isNotBlank();

        // 孩子 B 写错另一句
        loginAsKid(childAccountId(childBId), childBAccount);
        assertThat(gameService.transformNext().getRoundId()).isNotBlank();
        GrammarTransformJudgeVO judgeB = judgeTransform(TRANSFORM_CODE, CHILD_B_SENTENCE);
        assertThat(judgeB.getCorrect()).isFalse();

        // 各自入池，且都挂在自己的 child_id 上
        assertThat(count("SELECT COUNT(*) FROM study_error_pool WHERE child_id = ? AND raw_answer = ?",
                childAId, CHILD_A_SENTENCE)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM study_error_pool WHERE child_id = ? AND raw_answer = ?",
                childBId, CHILD_B_SENTENCE)).isEqualTo(1);

        // 回到孩子 A：只看得到自己的错句
        loginAsKid(childAccountId(childAId), childAAccount);
        GrammarGameCollectionResultVO collection = gameService.collection(null);
        assertThat(collection.getItems()).isNotEmpty();
        List<String> sentences = collection.getItems().stream()
                .map(GrammarGameCollectionItemVO::getOriginalSentence).collect(java.util.stream.Collectors.toList());
        assertThat(sentences).contains(CHILD_A_SENTENCE);
        assertThat(sentences).doesNotContain(CHILD_B_SENTENCE);

        // 孩子 B 的收藏馆里也没有 A 的错句
        loginAsKid(childAccountId(childBId), childBAccount);
        GrammarGameCollectionResultVO collectionB = gameService.collection(null);
        List<String> sentencesB = collectionB.getItems().stream()
                .map(GrammarGameCollectionItemVO::getOriginalSentence).collect(java.util.stream.Collectors.toList());
        assertThat(sentencesB).contains(CHILD_B_SENTENCE);
        assertThat(sentencesB).doesNotContain(CHILD_A_SENTENCE);
    }

    // ------------------------------------------------------------------ TC-05-12

    @Test
    @DisplayName("TC-05-12 变形闯关判定与错句入池")
    void transformJudge_wrongSentenceEntersPoolAndCorrectOnePasses() {
        GrammarTransformRoundVO round = gameService.transformNext();
        assertThat(round.getRoundId()).isNotBlank();
        assertThat(round.getMeaning()).isNotBlank();

        // 写错：给三件套，并进统一错题池
        GrammarTransformJudgeVO wrong = judgeTransform(TRANSFORM_CODE, CHILD_A_SENTENCE);
        assertThat(wrong.getCorrect()).isFalse();
        assertThat(wrong.getJudgeSource()).isEqualTo("rule");
        assertThat(wrong.getRule()).isNotBlank();
        assertThat(wrong.getExample()).isNotBlank();
        assertThat(wrong.getCounterExample()).isNotBlank();
        assertThat(wrong.getFix()).isNotBlank();
        assertThat(wrong.getHint()).isNotBlank();
        assertThat(wrong.getErrorCode()).isNotBlank();

        // 错句进了统一错题池（不自建错句表）
        Map<String, Object> pooled = queryOne("SELECT * FROM study_error_pool WHERE error_code = ?",
                wrong.getErrorCode());
        assertThat(pooled).isNotNull();
        assertThat(String.valueOf(pooled.get("source_module"))).isEqualTo("GRAMMAR");
        assertThat(String.valueOf(pooled.get("child_id"))).isEqualTo(childAId);
        assertThat(String.valueOf(pooled.get("judge_source"))).isEqualTo("rule");

        // 收藏馆里能查到
        GrammarGameCollectionResultVO collection = gameService.collection(null);
        boolean hit = collection.getItems().stream()
                .anyMatch(item -> CHILD_A_SENTENCE.equals(item.getOriginalSentence()));
        assertThat(hit).isTrue();

        // 写对：过关，并给参考表达
        GrammarTransformJudgeVO right = judgeTransform(TRANSFORM_CODE, TARGET_SENTENCE);
        assertThat(right.getCorrect()).isTrue();
        assertThat(right.getJudgeSource()).isEqualTo("rule");
        assertThat(right.getReferenceText()).isNotBlank();

        // 闯关记录按孩子落库，且带 child_id
        int records = count("SELECT COUNT(*) FROM study_grammar_game_record WHERE child_id = ?", childAId);
        assertThat(records).isGreaterThanOrEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM study_grammar_game_record WHERE child_id IS NULL")).isZero();
    }

    // ------------------------------------------------------------------ 工具

    /**
     * 造一个孩子并返回其档案。
     */
    private ChildCreateVO newChild(String nickname) {
        ChildCreateRequest request = new ChildCreateRequest();
        request.setNickname(nickname);
        request.setGradeCode("G3");
        request.setPin("1234");
        request.setRelationLabel("妈妈");
        return childService.create(request);
    }

    /**
     * 取孩子的登录账号（sys_user.id）。
     */
    private String childAccountId(String childId) {
        return (String) queryOne("SELECT user_id FROM study_child WHERE id = ?", childId).get("user_id");
    }

    /**
     * 造三句正例（规则一条都不命中）与一条带中文意思的目标句。
     */
    private void seedContextSentences() {
        for (String sentence : CLEAN_SENTENCES) {
            insertContextSentence(sentence, "这是一句正例。");
        }
        insertContextSentence(TARGET_SENTENCE, "医生让我每天早上吃一个苹果。");
    }

    private void insertContextSentence(String sentence, String zhMeaning) {
        jdbc.update("INSERT INTO study_sentence_context (id, sentence_code, sentence, zh_meaning, context_type, "
                        + "word_code, usage_note, source_ref, copyright_type, status, del_flag, create_by, create_time) "
                        + "VALUES (?,?,?,?,'EXAM','UT-GAM-WORD','测试用例说明','自编','SELF',1,0,'ut',NOW())",
                "ut_s_" + unique(), "UT-GAM-SCT-" + unique(), sentence, zhMeaning);
    }

    /**
     * 造两关题：一句侦探、一关变形闯关。
     *
     * <p>⚠️ <b>这里必须按正文取 sentence_code，不能按 {@code ORDER BY id} 的下标取</b>：
     * 本类造的行 id 是随机 UUID（{@code ut_s_ + unique()}），
     * {@code ORDER BY id} 的相对顺序**每轮都不同**——于是「第 4 句」有时是目标句、有时不是，
     * 变形闯关的「写对」就会随机判错（实测在全量回归里偶发失败，单独跑却常绿）。
     * 这跟 ADR-031 说的是同一类问题：<b>顺序必须可判定</b>。</p>
     */
    private void seedGameQuestions() {
        List<String> cleanCodes = new ArrayList<>();
        for (String sentence : CLEAN_SENTENCES) {
            cleanCodes.add(sentenceCodeOf(sentence));
        }
        String targetCode = sentenceCodeOf(TARGET_SENTENCE);
        assertThat(cleanCodes).as("三句正例的 sentence_code 必须互不相同").doesNotHaveDuplicates();
        assertThat(targetCode).as("目标句必须造出来").isNotBlank();

        String clean = "[\"" + String.join("\",\"", cleanCodes) + "\"]";
        jdbc.update("INSERT INTO study_grammar_game_question (id, question_code, game_type, grammar_code, "
                        + "sentence_code, clean_sentence_codes, wrong_sentence, wrong_position, difficulty, "
                        + "judge_source, status, del_flag, create_by, create_time) "
                        + "VALUES (?,?,'DETECTIVE',NULL,NULL,?,?,2,1,'rule',1,0,'ut',NOW())",
                "ut_q_" + unique(), DETECTIVE_CODE, clean, WRONG_SENTENCE);

        jdbc.update("INSERT INTO study_grammar_game_question (id, question_code, game_type, grammar_code, "
                        + "sentence_code, clean_sentence_codes, wrong_sentence, wrong_position, difficulty, "
                        + "judge_source, status, del_flag, create_by, create_time) "
                        + "VALUES (?,?,'TRANSFORM',NULL,?,NULL,NULL,NULL,1,'rule',1,0,'ut',NOW())",
                "ut_q_" + unique(), TRANSFORM_CODE, targetCode);
    }

    /**
     * 按例句正文取它的 sentence_code。
     *
     * @param sentence 例句正文
     * @return sentence_code
     */
    private String sentenceCodeOf(String sentence) {
        Map<String, Object> row = queryOne(
                "SELECT sentence_code FROM study_sentence_context WHERE sentence = ? AND del_flag = 0", sentence);
        assertThat(row).as("例句没造出来：%s", sentence).isNotNull();
        return String.valueOf(row.get("sentence_code"));
    }

    /**
     * 提交一次句子侦探判定。
     */
    private GrammarDetectiveJudgeVO judgeDetective(Integer pickedIndex) {
        GrammarDetectiveJudgeRequest request = new GrammarDetectiveJudgeRequest();
        request.setRoundId(DETECTIVE_CODE);
        request.setPickedIndex(pickedIndex);
        request.setDurationMs(3000);
        return gameService.detectiveJudge(request);
    }

    /**
     * 提交一次变形闯关判定。
     */
    private GrammarTransformJudgeVO judgeTransform(String roundId, String sentence) {
        GrammarTransformJudgeRequest request = new GrammarTransformJudgeRequest();
        request.setRoundId(roundId);
        request.setSentence(sentence);
        request.setDurationMs(5000);
        return gameService.transformJudge(request);
    }

}
