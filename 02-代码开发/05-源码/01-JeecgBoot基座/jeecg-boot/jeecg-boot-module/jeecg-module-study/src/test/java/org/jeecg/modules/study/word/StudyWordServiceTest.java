package org.jeecg.modules.study.word;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.study.common.constant.StudyErrorCode;
import org.jeecg.modules.study.error.constant.ErrorCause;
import org.jeecg.modules.study.error.service.impl.StudyErrorPoolServiceImpl;
import org.jeecg.modules.study.error.vo.ReviewQueueItemVO;
import org.jeecg.modules.study.family.service.StudyChildService;
import org.jeecg.modules.study.family.service.StudyFamilyService;
import org.jeecg.modules.study.family.vo.ChildCreateRequest;
import org.jeecg.modules.study.family.vo.ChildCreateVO;
import org.jeecg.modules.study.support.StudyTestSupport;
import org.jeecg.modules.study.word.service.StudyWordService;
import org.jeecg.modules.study.word.vo.WordDueVO;
import org.jeecg.modules.study.word.vo.WordFeedbackOptionVO;
import org.jeecg.modules.study.word.vo.WordFeedbackRequest;
import org.jeecg.modules.study.word.vo.WordKnownTestRequest;
import org.jeecg.modules.study.word.vo.WordKnownTestVO;
import org.jeecg.modules.study.word.vo.WordListItemVO;
import org.jeecg.modules.study.word.vo.WordListVO;
import org.jeecg.modules.study.word.vo.WordPracticeRequest;
import org.jeecg.modules.study.word.vo.WordPracticeResultVO;
import org.jeecg.modules.study.word.vo.WordPracticeVO;
import org.jeecg.modules.study.word.vo.WordStageVO;
import org.jeecg.modules.study.word.vo.WordStatsVO;
import org.jeecg.modules.study.word.vo.WordUnitCompleteVO;
import org.jeecg.modules.study.word.vo.WordUnitRequest;
import org.jeecg.modules.study.word.vo.WordUnitVO;

/**
 * 背单词模块自动化测试（F-WRD-01…F-WRD-06）。
 *
 * <p>覆盖测试用例 TC-03-01 … TC-03-11。</p>
 *
 * <p>统一错题池由 {@code error} 域并行实现，本测试用 mock 顶替，
 * 只保留本模块真正依赖的「今日复习队列」这一条行为。</p>
 *
 * @author jeecg-study
 */
class StudyWordServiceTest extends StudyTestSupport {

    /** 作答记录分表后缀格式 */
    private static final DateTimeFormatter RECORD_TABLE_SUFFIX = DateTimeFormatter.ofPattern("yyyyMM");

    @Autowired
    private StudyWordService wordService;

    @Autowired
    private StudyFamilyService familyService;

    @Autowired
    private StudyChildService childService;

    /**
     * 统一错题池：另一个域并行实现，这里用 mock 顶替。
     *
     * <p><b>为什么 mock 具体实现类而不是接口</b>：{@code StudyErrorPoolServiceImpl}
     * 同时实现 {@code StudyErrorPoolService} 与 {@code StudyErrorManageService}，
     * 是这两个契约唯一的 Bean。若 mock 接口，Spring 会把这个唯一 Bean 整体替换掉，
     * {@code StudyErrorPoolController} 注入 {@code StudyErrorManageService} 就会断链，
     * 整个 Spring 上下文起不来（连带其它测试类一起挂）。
     * mock 具体类时，替身仍然同时实现两个接口，契约不受影响。</p>
     */
    @MockBean
    private StudyErrorPoolServiceImpl errorPoolService;

    private String parentUserId;

    private String childId;

    private String childCode;

    private String versionId;

    @BeforeEach
    void setUp() {
        String parentName = "ut_word_" + unique();
        parentUserId = givenParentAccount(parentName);
        loginAsParent(parentUserId, parentName);
        familyService.create("UT背单词家庭");
        ChildCreateVO child = childService.create(new ChildCreateRequest()
                .setNickname("UT娃" + unique())
                .setRelationLabel("妈妈"));
        childId = child.getChildId();
        childCode = child.getChildCode();
        versionId = "UT-TBV-" + unique();
    }

    @Override
    protected void cleanTestData() {
        try {
            jdbc.update("DELETE FROM study_word WHERE word_code LIKE 'UT%'");
            jdbc.update("DELETE FROM study_error_pool WHERE error_code LIKE 'UT%'");
            for (String table : recordTables()) {
                try {
                    jdbc.update("DELETE FROM " + table + " WHERE content_id LIKE 'UT%'");
                } catch (Exception ignored) {
                    // 上月分表可能尚未建立（分表按月建），不存在时跳过即可，
                    // 不能让它中断后续清理，否则本用例造的词会残留到下一轮
                }
            }
        } catch (Exception e) {
            // 清理失败不影响断言结果，下一轮仍按唯一命名隔离
            System.out.println("[StudyWordServiceTest] 清理测试数据失败：" + e.getMessage());
        }
        super.cleanTestData();
    }

    // ------------------------------------------------------------------ TC-03-01

    @Test
    @DisplayName("TC-03-01 课内单元 1:1 映射：词表与教材单元完全对应，顺序一致")
    void unitMapsToTextbookOneToOne() {
        String first = givenWord("IN", versionId, "UTU1", 1, "释义一");
        String second = givenWord("IN", versionId, "UTU1", 2, "释义二");
        givenWord("IN", versionId, "UTU2", 1, "释义三");

        List<WordUnitVO> units = wordService.listUnits("IN", versionId);
        assertThat(units).hasSize(2);
        assertThat(units.get(0).getUnitCode()).isEqualTo("UTU1");
        assertThat(units.get(0).getWordCount()).isEqualTo(2);
        assertThat(units.get(1).getUnitCode()).isEqualTo("UTU2");
        assertThat(units.get(1).getWordCount()).isEqualTo(1);

        WordListVO list = wordService.listWords("IN", versionId, "UTU1", 1, 50);
        assertThat(list.getItems()).extracting(WordListItemVO::getWordCode)
                .containsExactly(first, second);
    }

    // ------------------------------------------------------------------ TC-03-02

    @Test
    @DisplayName("TC-03-02 超纲词被过滤：切换教材版本后非本版本的词不出现")
    void outOfScopeWordsAreFiltered() {
        String otherVersion = "UT-TBV-" + unique();
        String inScope = givenWord("IN", versionId, "UTU1", 1, "释义一");
        String outOfScope = givenWord("IN", otherVersion, "UTU1", 1, "释义二");

        WordListVO list = wordService.listWords("IN", versionId, "UTU1", 1, 50);
        assertThat(list.getItems()).extracting(WordListItemVO::getWordCode)
                .containsExactly(inScope)
                .doesNotContain(outOfScope);

        List<WordUnitVO> units = wordService.listUnits("IN", versionId);
        assertThat(units).hasSize(1);
        assertThat(units.get(0).getWordCount()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ TC-03-03

    @Test
    @DisplayName("TC-03-03 无图回忆环节存在：图片仅作辅助，回忆不依赖图片")
    void recallStageDoesNotDependOnImage() {
        String spelling = spelling();
        String wordCode = givenWord("IN", versionId, "UTU1", 1, "释义", spelling);

        WordPracticeVO vo = wordService.nextWord("IN", versionId, "UTU1");

        assertThat(vo.getWordCode()).isEqualTo(wordCode);
        assertThat(vo.getRecallWithoutImage()).isTrue();
        assertThat(vo.getStages()).anySatisfy(stage -> {
            assertThat(stage.getStageCode()).isEqualTo("MEANING");
            assertThat(stage.getImageRequired()).isFalse();
        });

        // R-01：取词响应不含拼写，也不含答案类字段
        assertThat(vo.getContextSentence()).doesNotContain(spelling);
        assertThat(hasNoAnswerField(WordPracticeVO.class)).isTrue();
        assertThat(hasNoAnswerField(WordListItemVO.class)).isTrue();
    }

    // ------------------------------------------------------------------ TC-03-04

    @Test
    @DisplayName("TC-03-04 四档反馈齐全：认识/模糊/忘记/熟知，不是对错")
    void feedbackOptionsAreExactlyFourLevels() {
        List<WordFeedbackOptionVO> options = wordService.feedbackOptions();

        assertThat(options).hasSize(4);
        assertThat(options).extracting(WordFeedbackOptionVO::getFeedback)
                .containsExactly("KNOWN", "VAGUE", "FORGOT", "FAMILIAR");
        assertThat(options).extracting(WordFeedbackOptionVO::getLabel)
                .containsExactly("认识", "模糊", "忘记", "熟知");
        assertThat(options).extracting(WordFeedbackOptionVO::getLabel)
                .noneMatch(label -> "对".equals(label) || "错".equals(label)
                        || "正确".equals(label) || "错误".equals(label));
    }

    // ------------------------------------------------------------------ TC-03-05

    @Test
    @DisplayName("TC-03-05 四档反馈更新遗忘参数：S/λ 按档位严格更新")
    void fourLevelsUpdateForgettingParams() {
        String known = givenWord("IN", versionId, "UTU1", 1, "释义一");
        String vague = givenWord("IN", versionId, "UTU1", 2, "释义二");
        String forgot = givenWord("IN", versionId, "UTU1", 3, "释义三");
        String familiar = givenWord("IN", versionId, "UTU1", 4, "释义四");

        wordService.feedback(feedbackRequest(known, "KNOWN"));
        wordService.feedback(feedbackRequest(vague, "VAGUE"));
        wordService.feedback(feedbackRequest(forgot, "FORGOT"));
        wordService.feedback(feedbackRequest(familiar, "FAMILIAR"));

        // 初始 S=0.5 λ=2.0
        assertForgettingParams(known, "0.85", "2.0000", "KNOWN");
        assertForgettingParams(vague, "0.60", "2.4000", "VAGUE");
        assertForgettingParams(forgot, "0.0000", "3.2000", "FORGOT");
        assertForgettingParams(familiar, "1.0000", "1.6000", "FAMILIAR");
    }

    // ------------------------------------------------------------------ TC-03-06

    @Test
    @DisplayName("TC-03-06 熟词过滤生效：已判定掌握的词不进队列，过滤量在统计中可见")
    void knownWordsAreFilteredOutOfQueue() {
        // update-begin---author:mavis ---date:2026-10-31  for：【TC-03-06】断言收敛到本用例自己造的词：库里有业务完整版种子词表后，课外线的词表与队列不再为空-----------
        // 队列基线：课外线可能已有业务种子词（业务完整版），断言只针对本用例造的三条词
        int baselineQueue = wordService.stats("OUT", childCode).getQueueWords();

        String known = givenWord("OUT", null, null, null, "释义一");
        String second = givenWord("OUT", null, null, null, "释义二");
        String third = givenWord("OUT", null, null, null, "释义三");

        WordKnownTestVO result = wordService.knownTest(new WordKnownTestRequest()
                .setLineType("OUT")
                .setWordCodes(Arrays.asList(known)));
        assertThat(result.getMarkedCount()).isEqualTo(1);
        assertThat(result.getFilteredKnownCount()).isEqualTo(1);

        WordListVO list = wordService.listWords("OUT", null, null, 1, 500);
        assertThat(list.getItems()).extracting(WordListItemVO::getWordCode)
                .filteredOn(code -> code.startsWith("UT-WRD-"))
                .containsExactlyInAnyOrder(second, third);
        assertThat(list.getItems()).extracting(WordListItemVO::getWordCode).doesNotContain(known);
        assertThat(list.getFilteredKnownCount()).isEqualTo(1);

        WordStatsVO stats = wordService.stats("OUT", childCode);
        assertThat(stats.getFilteredKnownCount()).isEqualTo(1);
        assertThat(stats.getQueueWords()).isEqualTo(baselineQueue + 2);
        // update-end---author:mavis ---date:2026-10-31  for：【TC-03-06】断言收敛到本用例自己造的词：库里有业务完整版种子词表后，课外线的词表与队列不再为空-----------
    }

    // ------------------------------------------------------------------ TC-03-07

    @Test
    @DisplayName("TC-03-07 课内→课外跨线升级：课内背错的词进课外优先复习项，upgradeFlag=1")
    void textbookErrorUpgradesToExtraLine() {
        String spelling = spelling();
        String textbookWord = givenWord("IN", versionId, "UTU1", 1, "释义", spelling);
        String extraWord = givenWord("OUT", null, null, null, "释义", spelling);
        String errorCode = givenErrorPoolRow(textbookWord, 0);
        when(errorPoolService.report(any())).thenReturn(errorCode);

        WordPracticeResultVO result = wordService.practice(new WordPracticeRequest()
                .setChildCode(childCode)
                .setWordCode(textbookWord)
                .setSpelling("wrong-" + unique())
                .setErrorCause(ErrorCause.CONCEPT_UNCLEAR));

        assertThat(result.getCorrect()).isFalse();
        assertThat(result.getUpgraded()).isTrue();
        // R-02：判定来源只能是 rule / rule+model
        assertThat(result.getJudgeSource()).isEqualTo("rule");

        Map<String, Object> row = queryOne("SELECT upgrade_flag FROM study_error_pool WHERE error_code = ?",
                errorCode);
        assertThat(((Number) row.get("upgrade_flag")).intValue()).isEqualTo(1);

        WordDueVO due = wordService.dueWords("OUT", childCode);
        assertThat(due.getUpgradeItems()).extracting(WordListItemVO::getWordCode)
                .contains(extraWord);
    }

    // ------------------------------------------------------------------ TC-03-08

    @Test
    @DisplayName("TC-03-08 复习欠账时不发新词：提示先把昨天的复习做完，新单元不可进入")
    void reviewDebtBlocksNewUnit() {
        givenWord("IN", versionId, "UTU1", 1, "释义一");

        ReviewQueueItemVO pending = new ReviewQueueItemVO();
        pending.setErrorCode("UT-ERR-" + unique());
        when(errorPoolService.todayQueue(anyString())).thenReturn(Collections.singletonList(pending));

        WordUnitRequest request = new WordUnitRequest()
                .setChildCode(childCode)
                .setTextbookVersionId(versionId)
                .setUnitCode("UTU1");

        assertThatThrownBy(() -> wordService.enterUnit(request))
                .isInstanceOf(JeecgBootException.class)
                .hasMessageContaining("先把昨天的复习做完")
                .satisfies(e -> assertThat(((JeecgBootException) e).getErrCode())
                        .isEqualTo(StudyErrorCode.PRACTICE_ORDER_VIOLATION));

        // 复习清空后可进入新单元
        when(errorPoolService.todayQueue(anyString())).thenReturn(Collections.emptyList());
        WordListVO list = wordService.enterUnit(request);
        assertThat(list.getItems()).hasSize(1);
    }

    // ------------------------------------------------------------------ TC-03-09

    @Test
    @DisplayName("TC-03-09 闯关与连续天数：完成单元有可见奖励，连续天数 +1")
    void completeUnitGivesRewardAndIncreasesStreak() {
        givenWord("IN", versionId, "UTU1", 1, "释义一");
        givenPracticeRecord(LocalDate.now().minusDays(1));

        WordUnitRequest request = new WordUnitRequest()
                .setChildCode(childCode)
                .setTextbookVersionId(versionId)
                .setUnitCode("UTU1");

        WordUnitCompleteVO vo = wordService.completeUnit(request);
        assertThat(vo.getRewardText()).isNotBlank();
        assertThat(vo.getStreakDays()).isEqualTo(2);
        assertThat(vo.getGameEnabled()).isTrue();
        assertThat(vo.getRewardVisible()).isTrue();
        assertThat(vo.getStreakVisible()).isTrue();

        // 同一天再完成一次，连续天数不重复累加
        WordUnitCompleteVO again = wordService.completeUnit(request);
        assertThat(again.getStreakDays()).isEqualTo(2);
    }

    // ------------------------------------------------------------------ TC-03-10

    @Test
    @DisplayName("TC-03-10 纯净模式可关闭游戏化：游戏化元素标记为 false")
    void pureModeTurnsOffGamification() {
        WordUnitRequest request = new WordUnitRequest()
                .setChildCode(childCode)
                .setTextbookVersionId(versionId)
                .setUnitCode("UTU1")
                .setPureMode(true);

        WordUnitCompleteVO vo = wordService.completeUnit(request);

        assertThat(vo.getPureMode()).isTrue();
        assertThat(vo.getGameEnabled()).isFalse();
        assertThat(vo.getRewardVisible()).isFalse();
        assertThat(vo.getStreakVisible()).isFalse();
        assertThat(vo.getRewardText()).isEmpty();
    }

    // ------------------------------------------------------------------ TC-03-11

    @Test
    @DisplayName("TC-03-11 词→句→篇三层：释义 → 造句 → 短文填词")
    void wordEntryHasThreeLayers() {
        String spelling = spelling();
        givenWord("IN", versionId, "UTU1", 1, "释义一", spelling);
        givenWord("IN", versionId, "UTU1", 2, "释义二");
        givenWord("IN", versionId, "UTU1", 3, "释义三");

        WordPracticeVO vo = wordService.nextWord("IN", versionId, "UTU1");

        assertThat(vo.getStages()).hasSize(3);
        assertThat(vo.getStages()).extracting(WordStageVO::getStageCode)
                .containsExactly("MEANING", "SENTENCE", "PASSAGE");
        assertThat(vo.getStages()).extracting(WordStageVO::getLayer)
                .containsExactly(1, 2, 3);

        WordStageVO passage = vo.getStages().get(2);
        assertThat(passage.getContent()).isNotBlank();
        assertThat(passage.getContent()).contains("____");
        assertThat(passage.getContent()).doesNotContain(spelling);
    }

    // ------------------------------------------------------------------ 辅助

    /**
     * 造一个单词。词条统一用 {@code UT} 前缀，便于清理。
     *
     * @param lineType 线别 IN / OUT
     * @param version  教材版本ID（课内必填）
     * @param unitCode 单元编码（课内必填）
     * @param unitOrder 单元内顺序
     * @param meaning  释义
     * @param spelling 拼写；不传则随机生成
     * @return 单词业务ID
     */
    private String givenWord(String lineType, String version, String unitCode, Integer unitOrder,
                             String meaning) {
        return givenWord(lineType, version, unitCode, unitOrder, meaning, spelling());
    }

    private String givenWord(String lineType, String version, String unitCode, Integer unitOrder,
                             String meaning, String spelling) {
        String wordCode = "UT-WRD-" + unique();
        jdbc.update("INSERT INTO study_word (id, word_code, spelling, meaning, line_type, "
                        + "textbook_version_id, unit_code, unit_order, context_sentence, frequency, "
                        + "memory_strength, decay_lambda, feedback_count, known_flag, status, del_flag, "
                        + "create_time) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,0,0,1,0,NOW())",
                IdWorker.get32UUID(), wordCode, spelling, meaning, lineType,
                version, unitCode, unitOrder, sentence(spelling), 100,
                new BigDecimal("0.5000"), new BigDecimal("2.0000"));
        return wordCode;
    }

    /**
     * 造一条统一错题池记录（用于跨线升级断言）。
     *
     * @return 错题业务ID
     */
    private String givenErrorPoolRow(String contentId, int upgradeFlag) {
        String errorCode = "UT-ERR-" + unique();
        jdbc.update("INSERT INTO study_error_pool (id, child_id, error_code, context_id, source_module, "
                        + "content_type, content_id, error_cause, judge_source, mastery_state, "
                        + "correct_streak, review_status, upgrade_flag, copyright_type, del_flag, "
                        + "create_time) VALUES (?,?,?,?,?,?,?,?,?,1,0,1,?,?,0,NOW())",
                IdWorker.get32UUID(), childId, errorCode, "", "WORD", "WORD", contentId,
                ErrorCause.CONCEPT_UNCLEAR, "rule", upgradeFlag, "SELF");
        return errorCode;
    }

    /**
     * 造一条历史作答记录（用于连续学习天数）。
     *
     * <p>统一写到当月分表：分表按月建立，跨月首日上月表可能尚未建好。</p>
     */
    private void givenPracticeRecord(LocalDate date) {
        String table = "study_practice_record_" + LocalDate.now().format(RECORD_TABLE_SUFFIX);
        jdbc.update("INSERT INTO " + table + " (id, child_id, context_id, source_module, content_type, "
                        + "content_id, answer, correct, judge_source, practice_date, del_flag, create_time) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,0,NOW())",
                IdWorker.get32UUID(), childId, "", "WORD", "UNIT", "UTU-REC-" + unique(),
                null, 1, "rule", java.sql.Date.valueOf(date));
    }

    private List<String> recordTables() {
        return Arrays.asList(
                "study_practice_record_" + LocalDate.now().format(RECORD_TABLE_SUFFIX),
                "study_practice_record_" + LocalDate.now().minusMonths(1).format(RECORD_TABLE_SUFFIX));
    }

    private WordFeedbackRequest feedbackRequest(String wordCode, String feedback) {
        return new WordFeedbackRequest()
                .setChildCode(childCode)
                .setWordCode(wordCode)
                .setFeedback(feedback);
    }

    private void assertForgettingParams(String wordCode, String strength, String lambda, String feedback) {
        Map<String, Object> row = queryOne("SELECT memory_strength, decay_lambda, feedback_count, "
                + "last_feedback, next_review_at FROM study_word WHERE word_code = ?", wordCode);
        assertThat((BigDecimal) row.get("memory_strength")).isEqualByComparingTo(strength);
        assertThat((BigDecimal) row.get("decay_lambda")).isEqualByComparingTo(lambda);
        assertThat(((Number) row.get("feedback_count")).intValue()).isEqualTo(1);
        assertThat(row.get("last_feedback")).isEqualTo(feedback);
        assertThat(row.get("next_review_at")).isNotNull();
    }

    /**
     * R-01 断言：响应对象不含拼写与答案类字段。
     */
    private boolean hasNoAnswerField(Class<?> type) {
        for (Field field : type.getDeclaredFields()) {
            String name = field.getName().toLowerCase();
            if ("answer".equals(name) || "correctanswer".equals(name)
                    || "analysis".equals(name) || "spelling".equals(name)) {
                return false;
            }
        }
        return true;
    }

    private String spelling() {
        return "w" + unique();
    }

    private String sentence(String spelling) {
        return "I saw a " + spelling + " in the park yesterday.";
    }
}
