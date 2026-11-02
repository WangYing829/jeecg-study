package org.jeecg.modules.study.grammar.gameplay.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import org.jeecg.modules.study.grammar.gameplay.entity.StudyGrammarGameQuestion;

/**
 * 语法玩法题库 Mapper（F-GRM-04）。
 *
 * @author jeecg-study
 */
@Mapper
public interface StudyGrammarGameQuestionMapper {

    /**
     * 按玩法取候选题目（难度升序，题号兜底保证顺序确定）。
     *
     * @param gameType 玩法类型
     * @param limit    候选条数上限
     * @return 题目列表
     */
    @Select("SELECT * FROM study_grammar_game_question WHERE game_type = #{gameType} "
            + "AND del_flag = 0 AND status = 1 ORDER BY difficulty, question_code, id LIMIT #{limit}")
    List<StudyGrammarGameQuestion> selectByType(@Param("gameType") String gameType, @Param("limit") int limit);

    /**
     * 按题号取题目。
     *
     * @param questionCode 题目业务ID
     * @return 题目；不存在返回 null
     */
    @Select("SELECT * FROM study_grammar_game_question WHERE question_code = #{questionCode} "
            + "AND del_flag = 0 AND status = 1 LIMIT 1")
    StudyGrammarGameQuestion selectByCode(@Param("questionCode") String questionCode);

    /**
     * 某个玩法的题目总数（闯关进度用）。
     *
     * @param gameType 玩法类型
     * @return 题目总数
     */
    @Select("SELECT COUNT(*) FROM study_grammar_game_question WHERE game_type = #{gameType} "
            + "AND del_flag = 0 AND status = 1")
    int countByType(@Param("gameType") String gameType);

}
