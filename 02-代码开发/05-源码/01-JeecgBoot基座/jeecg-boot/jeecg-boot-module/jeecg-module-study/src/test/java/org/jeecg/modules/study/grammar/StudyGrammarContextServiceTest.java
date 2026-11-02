package org.jeecg.modules.study.grammar;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.jeecg.modules.study.grammar.service.StudyGrammarContextService;
import org.jeecg.modules.study.grammar.vo.GrammarContextItemVO;
import org.jeecg.modules.study.grammar.vo.GrammarContextResultVO;
import org.jeecg.modules.study.support.StudyTestSupport;

/**
 * 例句语境库自动化测试（V3 / TC-05-07、TC-05-08）。
 *
 * <p>核心断言只有一条，但很硬：<b>语法域查到的例句与背单词域关联的是同一批</b>——
 * 编码一致、数量一致、主例句正文就是背单词域 {@code study_word.context_sentence} 那一句。</p>
 *
 * <p>跑在真实 {@code jeecg-study} 库上；测试数据一律 {@code UT} 前缀并在用例后清理。</p>
 *
 * @author jeecg-study
 */
class StudyGrammarContextServiceTest extends StudyTestSupport {

    /** 测试词条的拼写（前缀 UT，避免与种子词条撞唯一键） */
    private static final String TEST_SPELLING = "UTapple";

    /** 测试词条的释义 */
    private static final String TEST_MEANING = "测试苹果";

    /** 背单词域那一条例句（语境库主例句必须是同一句） */
    private static final String WORD_SENTENCE = "I eat an UTapple every morning.";

    /** 真题语境例句 */
    private static final String EXAM_SENTENCE = "The doctor asked me to eat an UTapple every morning.";

    /** 原声素材语境例句 */
    private static final String ORIGINAL_SENTENCE = "I bite into this UTapple every single morning.";

    @Autowired
    private StudyGrammarContextService contextService;

    /** 测试词条业务ID */
    private String wordCode;

    @BeforeEach
    void seedContextLibrary() {
        wordCode = "UT-WRD-" + unique();

        // 背单词域：一条词条 + 它自己那条例句
        jdbc.update("INSERT INTO study_word (id, word_code, spelling, meaning, line_type, context_sentence, "
                        + "frequency, memory_strength, decay_lambda, feedback_count, known_flag, status, del_flag, "
                        + "create_by, create_time) "
                        + "VALUES (?,?,?,?,?,?,?,0,2.0000,0,0,1,0,'ut',NOW())",
                "ut_w_" + unique(), wordCode, TEST_SPELLING, TEST_MEANING, "OUT", WORD_SENTENCE, 10);

        // 语境库：同一批例句，三类语境各一条
        seedSentence(wordCode, "TEXTBOOK", WORD_SENTENCE, TEST_MEANING);
        seedSentence(wordCode, "EXAM", EXAM_SENTENCE, "医生让我每天早上吃一个测试苹果。");
        seedSentence(wordCode, "ORIGINAL", ORIGINAL_SENTENCE, "我每天早上都要咬一口这个测试苹果。");

        // 关联表：主例句 1 条 + 拓展用法 2 条
        seedRelation("TEXTBOOK", "MAIN", 1);
        seedRelation("EXAM", "EXTEND", 2);
        seedRelation("ORIGINAL", "EXTEND", 3);
    }

    @AfterEach
    void cleanContextData() {
        try {
            jdbc.update("DELETE FROM study_word_context WHERE word_code = ?", wordCode);
            jdbc.update("DELETE FROM study_sentence_context WHERE word_code = ?", wordCode);
            jdbc.update("DELETE FROM study_word WHERE word_code = ?", wordCode);
        } catch (Exception e) {
            System.out.println("[StudyGrammarContextServiceTest] 清理测试数据失败：" + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ TC-05-07

    @Test
    @DisplayName("TC-05-07 语境库与背单词是同一批例句")
    void context_returnsTheSameSentenceBatchAsTheWordDomain() {
        GrammarContextResultVO result = contextService.context(wordCode, null, null, null);

        assertThat(result.getWordCode()).isEqualTo(wordCode);
        assertThat(result.getSpelling()).isEqualTo(TEST_SPELLING);
        assertThat(result.getMeaning()).isEqualTo(TEST_MEANING);

        // 1) 数量一致：关联表里几条，接口就返回几条
        List<Map<String, Object>> relations = queryList(
                "SELECT sentence_code FROM study_word_context WHERE word_code = ? AND del_flag = 0", wordCode);
        assertThat(relations).hasSize(3);
        assertThat(result.getTotal()).isEqualTo(relations.size());
        assertThat(result.getItems()).hasSize(relations.size());

        // 2) 编码一致：两边的例句业务ID 集合完全相同
        List<String> expected = relations.stream()
                .map(row -> String.valueOf(row.get("sentence_code"))).sorted().collect(Collectors.toList());
        List<String> actual = result.getItems().stream()
                .map(GrammarContextItemVO::getSentenceCode).sorted().collect(Collectors.toList());
        assertThat(actual).isEqualTo(expected);

        // 3) 主例句就是背单词域那一句（不是复制，是同一句）
        GrammarContextItemVO main = result.getItems().stream()
                .filter(item -> "MAIN".equals(item.getRelationType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("没有主例句"));
        assertThat(main.getSentence()).isEqualTo(WORD_SENTENCE);
        assertThat(result.getWordExampleSentence()).isEqualTo(WORD_SENTENCE);

        // 4) 背单词域那一列没被改动过（跨域只读）
        Map<String, Object> word = queryOne("SELECT context_sentence FROM study_word WHERE word_code = ?", wordCode);
        assertThat(String.valueOf(word.get("context_sentence"))).isEqualTo(WORD_SENTENCE);
    }

    // ------------------------------------------------------------------ TC-05-08

    @Test
    @DisplayName("TC-05-08 三类语境都覆盖")
    void context_coversTextbookExamAndOriginal() {
        GrammarContextResultVO result = contextService.context(wordCode, null, null, null);

        List<String> types = result.getItems().stream()
                .map(GrammarContextItemVO::getContextType).collect(Collectors.toList());
        assertThat(types).containsExactlyInAnyOrder("TEXTBOOK", "EXAM", "ORIGINAL");

        for (GrammarContextItemVO item : result.getItems()) {
            assertThat(item.getSentence()).isNotBlank();
            assertThat(item.getContextTypeName()).isNotBlank();
            assertThat(item.getUsageNote()).isNotBlank();
        }

        // 按语境类型过滤，只剩一类
        GrammarContextResultVO examOnly = contextService.context(wordCode, null, "EXAM", null);
        assertThat(examOnly.getItems()).hasSize(1);
        assertThat(examOnly.getItems().get(0).getContextType()).isEqualTo("EXAM");
        assertThat(examOnly.getItems().get(0).getSentence()).isEqualTo(EXAM_SENTENCE);
    }

    // ------------------------------------------------------------------ 工具

    /**
     * 造一条语境库例句。
     */
    private void seedSentence(String code, String contextType, String sentence, String zhMeaning) {
        jdbc.update("INSERT INTO study_sentence_context (id, sentence_code, sentence, zh_meaning, context_type, "
                        + "word_code, usage_note, source_ref, copyright_type, status, del_flag, create_by, create_time) "
                        + "VALUES (?,?,?,?,?,?,?,?,'SELF',1,0,'ut',NOW())",
                "ut_s_" + unique(), "UT-SCT-" + unique(), sentence, zhMeaning, contextType, code,
                "测试用例说明", "自编");
    }

    /**
     * 造一条词条与例句的关联（按语境类型取该词已建的那条例句）。
     */
    private void seedRelation(String contextType, String relationType, int sortNo) {
        Map<String, Object> row = queryOne(
                "SELECT sentence_code FROM study_sentence_context WHERE word_code = ? AND context_type = ? "
                        + "AND del_flag = 0 LIMIT 1", wordCode, contextType);
        if (row == null) {
            return;
        }
        jdbc.update("INSERT INTO study_word_context (id, word_code, sentence_code, relation_type, sort_no, "
                        + "status, del_flag, create_by, create_time) VALUES (?,?,?,?,?,1,0,'ut',NOW())",
                "ut_r_" + unique(), wordCode, String.valueOf(row.get("sentence_code")),
                relationType, sortNo);
    }

}
