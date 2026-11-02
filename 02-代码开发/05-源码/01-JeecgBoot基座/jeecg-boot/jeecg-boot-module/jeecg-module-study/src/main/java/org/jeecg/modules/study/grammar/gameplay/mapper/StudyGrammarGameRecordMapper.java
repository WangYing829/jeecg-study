package org.jeecg.modules.study.grammar.gameplay.mapper;

import java.util.Date;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import org.jeecg.modules.study.grammar.gameplay.entity.StudyGrammarGameRecord;

/**
 * 语法玩法作答记录 Mapper（F-GRM-04）。
 *
 * <p>行为表：每一条记录都带 {@code child_id}，查询一律按孩子过滤（R-04 / ADR-018）。</p>
 *
 * @author jeecg-study
 */
@Mapper
public interface StudyGrammarGameRecordMapper {

    /**
     * 写一条作答记录。
     *
     * <p>归属日用数据库的 {@code CURDATE()} 落库，避免应用与数据库时区不一致时日期漂移。</p>
     *
     * @param record 作答记录（id / child_id 等由调用方填好）
     * @return 影响行数
     */
    @Insert("INSERT INTO study_grammar_game_record (id, child_id, game_type, question_code, grammar_code, "
            + "submitted, correct, judge_source, error_cause, error_code, duration_ms, play_date, del_flag, "
            + "create_by, create_time, update_by, update_time) "
            + "VALUES (#{id}, #{childId}, #{gameType}, #{questionCode}, #{grammarCode}, #{submitted}, "
            + "#{correct}, #{judgeSource}, #{errorCause}, #{errorCode}, #{durationMs}, CURDATE(), #{delFlag}, "
            + "#{createBy}, #{createTime}, #{updateBy}, #{updateTime})")
    int insertRecord(StudyGrammarGameRecord record);

    /**
     * 某孩子某玩法已通过的题目业务ID（闯关进度）。
     *
     * @param childId  孩子ID
     * @param gameType 玩法类型
     * @return 题目业务ID 列表
     */
    @Select("SELECT DISTINCT question_code FROM study_grammar_game_record "
            + "WHERE child_id = #{childId} AND game_type = #{gameType} AND correct = 1 AND del_flag = 0 "
            + "ORDER BY question_code")
    List<String> selectPassedQuestionCodes(@Param("childId") String childId,
                                            @Param("gameType") String gameType);

    /**
     * 某孩子某玩法的作答次数。
     *
     * @param childId  孩子ID
     * @param gameType 玩法类型
     * @return 作答次数
     */
    @Select("SELECT COUNT(*) FROM study_grammar_game_record "
            + "WHERE child_id = #{childId} AND game_type = #{gameType} AND del_flag = 0")
    int countAnswered(@Param("childId") String childId, @Param("gameType") String gameType);

    /**
     * 某孩子某玩法最近一次作答时间。
     *
     * @param childId  孩子ID
     * @param gameType 玩法类型
     * @return 最近作答时间；没有记录返回 null
     */
    @Select("SELECT MAX(create_time) FROM study_grammar_game_record "
            + "WHERE child_id = #{childId} AND game_type = #{gameType} AND del_flag = 0")
    Date selectLastPlayedAt(@Param("childId") String childId, @Param("gameType") String gameType);

}
