package org.jeecg.modules.study.grammar.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import org.jeecg.modules.study.grammar.vo.GrammarContextRow;
import org.jeecg.modules.study.grammar.vo.GrammarWordHeaderRow;

/**
 * 例句语境库 Mapper（F-GRM-05）。
 *
 * <p>全部自定义 SQL 用注解写，<b>不写 XML</b>。</p>
 *
 * <p><b>跨域只读说明</b>：本 Mapper 只<b>读</b>背单词域的 {@code study_word}
 * （拼写 / 释义 / 它自己那条例句），不写、不改、不加列——
 * 与 {@code StudyGrammarErrorQueryMapper} 跨域只读 {@code study_error_pool} 同一约定。</p>
 *
 * @author jeecg-study
 */
@Mapper
public interface StudySentenceContextMapper {

    /**
     * 按词条查例句（背单词域与语法域共用同一批）。
     *
     * <p>排序固定：主例句在前，再按课文 → 真题 → 原声素材，
     * 最后用 {@code c.id} 兜底，保证顺序确定（ADR-031）。</p>
     *
     * @param wordCode    词条业务ID
     * @param contextType 语境类型；为空则不过滤
     * @return 例句行列表
     */
    @Select("<script>SELECT c.sentence_code AS sentenceCode, c.sentence AS sentence, "
            + "c.zh_meaning AS zhMeaning, c.context_type AS contextType, c.word_code AS wordCode, "
            + "c.grammar_code AS grammarCode, c.usage_note AS usageNote, c.source_ref AS sourceRef, "
            + "c.copyright_type AS copyrightType, w.relation_type AS relationType, w.sort_no AS sortNo "
            + "FROM study_word_context w "
            + "JOIN study_sentence_context c ON c.sentence_code = w.sentence_code "
            + "AND c.del_flag = 0 AND c.status = 1 "
            + "WHERE w.word_code = #{wordCode} AND w.del_flag = 0 AND w.status = 1 "
            + "<if test='contextType != null and contextType != \"\"'>AND c.context_type = #{contextType}</if> "
            + "ORDER BY FIELD(w.relation_type, 'MAIN', 'EXTEND'), "
            + "FIELD(c.context_type, 'TEXTBOOK', 'EXAM', 'ORIGINAL'), w.sort_no, c.id</script>")
    List<GrammarContextRow> selectByWordCode(@Param("wordCode") String wordCode,
                                             @Param("contextType") String contextType);

    /**
     * 按语法点查例句。
     *
     * @param grammarCode 语法点业务ID
     * @param contextType 语境类型；为空则不过滤
     * @return 例句行列表
     */
    @Select("<script>SELECT c.sentence_code AS sentenceCode, c.sentence AS sentence, "
            + "c.zh_meaning AS zhMeaning, c.context_type AS contextType, c.word_code AS wordCode, "
            + "c.grammar_code AS grammarCode, c.usage_note AS usageNote, c.source_ref AS sourceRef, "
            + "c.copyright_type AS copyrightType, 'EXTEND' AS relationType, 1 AS sortNo "
            + "FROM study_sentence_context c "
            + "WHERE c.grammar_code = #{grammarCode} AND c.del_flag = 0 AND c.status = 1 "
            + "<if test='contextType != null and contextType != \"\"'>AND c.context_type = #{contextType}</if> "
            + "ORDER BY FIELD(c.context_type, 'TEXTBOOK', 'EXAM', 'ORIGINAL'), c.id</script>")
    List<GrammarContextRow> selectByGrammarCode(@Param("grammarCode") String grammarCode,
                                                 @Param("contextType") String contextType);

    /**
     * 按例句业务ID批量取例句（玩法题库把正例句指向语境库）。
     *
     * @param sentenceCodes 例句业务ID 集合
     * @return 例句行列表
     */
    @Select("<script>SELECT c.sentence_code AS sentenceCode, c.sentence AS sentence, "
            + "c.zh_meaning AS zhMeaning, c.context_type AS contextType, c.word_code AS wordCode, "
            + "c.grammar_code AS grammarCode, c.usage_note AS usageNote, c.source_ref AS sourceRef, "
            + "c.copyright_type AS copyrightType, 'EXTEND' AS relationType, 1 AS sortNo "
            + "FROM study_sentence_context c WHERE c.del_flag = 0 AND c.status = 1 "
            + "AND c.sentence_code IN "
            + "<foreach collection='sentenceCodes' item='it' open='(' separator=',' close=')'>#{it}</foreach> "
            + "ORDER BY c.id</script>")
    List<GrammarContextRow> selectByCodes(@Param("sentenceCodes") List<String> sentenceCodes);

    /**
     * 取背单词域的词条抬头（<b>跨域只读</b>）。
     *
     * @param wordCode 词条业务ID
     * @return 抬头信息；词条不存在返回 null
     */
    @Select("SELECT spelling AS spelling, meaning AS meaning, "
            + "context_sentence AS contextSentence FROM study_word "
            + "WHERE word_code = #{wordCode} AND del_flag = 0 LIMIT 1")
    GrammarWordHeaderRow selectWordHeader(@Param("wordCode") String wordCode);

}
