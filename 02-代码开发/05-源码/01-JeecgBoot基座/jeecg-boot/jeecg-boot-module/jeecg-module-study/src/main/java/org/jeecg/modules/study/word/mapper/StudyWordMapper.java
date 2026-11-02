package org.jeecg.modules.study.word.mapper;

import java.util.Date;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.jeecg.modules.study.word.entity.StudyWord;
import org.jeecg.modules.study.word.vo.WordUnitVO;

/**
 * 单词 Mapper。
 *
 * <p>全部自定义 SQL 用注解写，<b>不写 XML</b>。</p>
 *
 * <p><b>跨域表访问说明</b>：本 Mapper 只额外读写 {@code study_error_pool} 的
 * {@code upgrade_flag} 一列（F-WRD-05 课内→课外跨线升级）。
 * 错题池的其余读写一律走 {@code StudyErrorPoolService} 公共接口，
 * 不修改 error 域的任何文件。</p>
 *
 * @author jeecg-study
 */
@Mapper
public interface StudyWordMapper extends BaseMapper<StudyWord> {

    /**
     * 按业务ID 查单词。
     *
     * @param wordCode 业务ID（WRD-000001）
     * @return 单词；不存在返回 null
     */
    @Select("SELECT * FROM study_word WHERE word_code = #{wordCode} AND del_flag = 0 LIMIT 1")
    StudyWord selectByWordCode(@Param("wordCode") String wordCode);

    /**
     * 课内单元词表：按教材版本 + 单元精确映射，顺序按 {@code unit_order}（TC-03-01）。
     *
     * @param textbookVersionId 教材版本ID
     * @param unitCode          单元编码
     * @param limit             条数上限
     * @param offset            偏移
     * @return 词表
     */
    @Select("SELECT * FROM study_word WHERE textbook_version_id = #{textbookVersionId} "
            + "AND unit_code = #{unitCode} AND line_type = 'IN' AND del_flag = 0 AND status = 1 "
            + "AND known_flag = 0 ORDER BY unit_order, id LIMIT #{limit} OFFSET #{offset}")
    List<StudyWord> selectByUnit(@Param("textbookVersionId") String textbookVersionId,
                                 @Param("unitCode") String unitCode,
                                 @Param("limit") int limit,
                                 @Param("offset") int offset);

    /**
     * 课内单元词表条数（过滤前，含熟词）。
     *
     * @param textbookVersionId 教材版本ID
     * @param unitCode          单元编码
     * @return 条数
     */
    @Select("SELECT COUNT(*) FROM study_word WHERE textbook_version_id = #{textbookVersionId} "
            + "AND unit_code = #{unitCode} AND line_type = 'IN' AND del_flag = 0 AND status = 1")
    int countByUnit(@Param("textbookVersionId") String textbookVersionId,
                    @Param("unitCode") String unitCode);

    /**
     * 某教材版本下的课内单元清单（按单元内最小顺序排序）。
     *
     * <p>排序键 {@code MIN(unit_order)} 可能并列（同一最小序号出现在多个单元），
     * 因此追加 {@code unit_code} 作为唯一兜底键，保证结果顺序确定、与执行计划无关。</p>
     *
     * @param textbookVersionId 教材版本ID
     * @return 单元清单
     */
    @Select("SELECT unit_code AS unitCode, MIN(unit_order) AS unitOrder, COUNT(*) AS wordCount "
            + "FROM study_word WHERE textbook_version_id = #{textbookVersionId} "
            + "AND unit_code IS NOT NULL AND line_type = 'IN' AND del_flag = 0 AND status = 1 "
            + "GROUP BY unit_code ORDER BY MIN(unit_order), unit_code")
    List<WordUnitVO> selectUnitStats(@Param("textbookVersionId") String textbookVersionId);

    /**
     * 某线别的词表（课外线按词频降序）。
     *
     * @param lineType 线别 IN / OUT
     * @param limit    条数上限
     * @param offset   偏移
     * @return 词表
     */
    @Select("SELECT * FROM study_word WHERE line_type = #{lineType} AND del_flag = 0 AND status = 1 "
            + "AND known_flag = 0 ORDER BY frequency DESC, id LIMIT #{limit} OFFSET #{offset}")
    List<StudyWord> selectByLine(@Param("lineType") String lineType,
                                  @Param("limit") int limit,
                                  @Param("offset") int offset);

    /**
     * 某线别的词条总数（过滤前，含熟词）。
     *
     * @param lineType 线别 IN / OUT
     * @return 条数
     */
    @Select("SELECT COUNT(*) FROM study_word WHERE line_type = #{lineType} AND del_flag = 0 AND status = 1")
    int countByLine(@Param("lineType") String lineType);

    /**
     * 熟词过滤条数（{@code known_flag = 1}）。
     *
     * @param lineType          线别 IN / OUT
     * @param textbookVersionId 教材版本ID，可空
     * @param unitCode          单元编码，可空
     * @return 被过滤的熟词条数
     */
    @Select("<script>SELECT COUNT(*) FROM study_word WHERE line_type = #{lineType} "
            + "AND del_flag = 0 AND status = 1 AND known_flag = 1 "
            + "<if test='textbookVersionId != null and textbookVersionId != \"\"'>"
            + "AND textbook_version_id = #{textbookVersionId} </if>"
            + "<if test='unitCode != null and unitCode != \"\"'>"
            + "AND unit_code = #{unitCode} </if>"
            + "</script>")
    int countKnownWords(@Param("lineType") String lineType,
                        @Param("textbookVersionId") String textbookVersionId,
                        @Param("unitCode") String unitCode);

    /**
     * 学过的词数（有过反馈）。
     *
     * @param lineType 线别 IN / OUT
     * @return 条数
     */
    @Select("SELECT COUNT(*) FROM study_word WHERE line_type = #{lineType} AND del_flag = 0 "
            + "AND status = 1 AND feedback_count > 0")
    int countLearnedWords(@Param("lineType") String lineType);

    /**
     * 到期复习词（{@code next_review_at} 为空或已到期，且未判为熟词）。
     *
     * @param lineType 线别 IN / OUT
     * @param today    今天
     * @return 到期词
     */
    @Select("SELECT * FROM study_word WHERE line_type = #{lineType} AND del_flag = 0 AND status = 1 "
            + "AND known_flag = 0 AND (next_review_at IS NULL OR next_review_at <= #{today}) "
            + "ORDER BY next_review_at, frequency DESC, id")
    List<StudyWord> selectDueWords(@Param("lineType") String lineType, @Param("today") Date today);

    /**
     * 按拼写查同一条线上的词（课内词升级到课外线时找「同词的课外行」）。
     *
     * @param spelling 拼写
     * @param lineType 线别
     * @return 单词；不存在返回 null
     */
    @Select("SELECT * FROM study_word WHERE spelling = #{spelling} AND line_type = #{lineType} "
            + "AND del_flag = 0 LIMIT 1")
    StudyWord selectBySpellingAndLine(@Param("spelling") String spelling,
                                      @Param("lineType") String lineType);

    /**
     * 标记熟词（熟词过滤结果）。
     *
     * @param wordCodes 单词业务ID 集合
     * @return 影响行数
     */
    @Update("<script>UPDATE study_word SET known_flag = 1, update_time = NOW() "
            + "WHERE del_flag = 0 AND word_code IN "
            + "<foreach collection='wordCodes' item='it' open='(' separator=',' close=')'>#{it}</foreach>"
            + "</script>")
    int markKnown(@Param("wordCodes") List<String> wordCodes);

    /**
     * 更新遗忘参数与复习日期。
     *
     * @param wordCode        单词业务ID
     * @param memoryStrength  新记忆强度 S
     * @param decayLambda     新衰减系数 λ
     * @param lastFeedback    最近反馈档
     * @param nextReviewAt    下次复习日期
     * @param updateBy        更新人
     * @return 影响行数
     */
    @Update("UPDATE study_word SET memory_strength = #{memoryStrength}, decay_lambda = #{decayLambda}, "
            + "feedback_count = feedback_count + 1, last_feedback = #{lastFeedback}, "
            + "next_review_at = #{nextReviewAt}, update_by = #{updateBy}, update_time = NOW() "
            + "WHERE word_code = #{wordCode} AND del_flag = 0")
    int updateForgettingParams(@Param("wordCode") String wordCode,
                                @Param("memoryStrength") java.math.BigDecimal memoryStrength,
                                @Param("decayLambda") java.math.BigDecimal decayLambda,
                                @Param("lastFeedback") String lastFeedback,
                                @Param("nextReviewAt") Date nextReviewAt,
                                @Param("updateBy") String updateBy);

    /**
     * 置跨线升级标记（课内→课外，F-WRD-05 / TC-03-07）。
     *
     * <p>只改 {@code study_error_pool.upgrade_flag} 一列，
     * 错题池的其余字段仍由 error 域负责。</p>
     *
     * @param errorCode 错题业务ID
     * @return 影响行数
     */
    @Update("UPDATE study_error_pool SET upgrade_flag = 1, update_time = NOW() "
            + "WHERE error_code = #{errorCode} AND del_flag = 0")
    int markErrorUpgraded(@Param("errorCode") String errorCode);

    /**
     * 取孩子的当前上下文ID。
     *
     * <p>统一作答记录表的 {@code context_id} 是必填列；上下文域尚未落地时可能查不到，
     * 由调用方兜底成空串。</p>
     *
     * @param childId 孩子ID
     * @return 上下文ID；查不到返回 null
     */
    @Select("SELECT id FROM study_context WHERE child_id = #{childId} AND del_flag = 0 "
            + "AND is_current = 1 ORDER BY create_time DESC, id DESC LIMIT 1")
    String selectCurrentContextId(@Param("childId") String childId);

    /**
     * 查某孩子已升级（课内错词）的单词业务ID。
     *
     * @param childId 孩子ID
     * @return 单词业务ID 列表
     */
    @Select("SELECT DISTINCT content_id FROM study_error_pool WHERE child_id = #{childId} "
            + "AND source_module = 'WORD' AND upgrade_flag = 1 AND review_status = 1 AND del_flag = 0")
    List<String> selectUpgradedWordCodes(@Param("childId") String childId);
}
