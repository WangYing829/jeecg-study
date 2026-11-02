package org.jeecg.modules.study.grammar.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import org.jeecg.modules.study.grammar.vo.GrammarCollectionRow;

/**
 * 错句收藏馆查询 Mapper（**只读**，跨域读统一错题池）。
 *
 * <p>错题池由 {@code error} 域独占维护，语法域<b>只读</b>，
 * 不新增列、不改写入路径（R-04 / ADR-024 跨域只读约定）。</p>
 *
 * @author jeecg-study
 */
@Mapper
public interface StudyGrammarErrorQueryMapper {

    /**
     * 查某个孩子的错句（来源模块 GRAMMAR）。
     *
     * @param childId 孩子ID
     * @return 错句行列表
     */
    @Select("SELECT error_code AS errorCode, content_id AS contentId, error_cause AS errorCause, "
            + "raw_answer AS originalSentence, correct_answer AS correctedSentence, "
            + "judge_source AS judgeSource, next_review_at AS nextReviewAt, "
            + "mastery_state AS masteryState, review_status AS reviewStatus "
            + "FROM study_error_pool WHERE child_id = #{childId} AND source_module = 'GRAMMAR' "
            + "AND del_flag = 0 ORDER BY create_time DESC")
    List<GrammarCollectionRow> selectGrammarSentences(@Param("childId") String childId);
}
