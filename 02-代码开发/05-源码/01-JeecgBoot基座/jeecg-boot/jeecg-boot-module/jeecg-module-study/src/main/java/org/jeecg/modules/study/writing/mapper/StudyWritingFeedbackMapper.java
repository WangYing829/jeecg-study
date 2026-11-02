package org.jeecg.modules.study.writing.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.jeecg.modules.study.writing.entity.StudyWriting;

/**
 * 孩子的写作数据 Mapper（<b>行为表</b>：提纲 / 初稿 / 家长反馈）。
 *
 * <p><b>每一条查询都必须带 {@code child_id} 条件</b>：这些行是某个孩子的私有数据，
 * 越权一律 4010（{@code StudyForbiddenException}），不用 404。</p>
 *
 * @author jeecg-study
 */
@Mapper
public interface StudyWritingFeedbackMapper extends BaseMapper<StudyWriting> {

    /**
     * 查孩子某一层的数据。
     *
     * @param childId 孩子 ID
     * @param layer   层
     * @return 行列表
     */
    @Select("SELECT * FROM study_writing WHERE child_id = #{childId} AND layer = #{layer} "
            + "AND del_flag = 0 ORDER BY create_time")
    List<StudyWriting> selectByChildAndLayer(@Param("childId") String childId,
                                              @Param("layer") String layer);

    /**
     * 查孩子的提纲行（同一道题 + 同一个题目只有一行）。
     *
     * @param childId      孩子 ID
     * @param referenceCode 拆解集编码
     * @param topic        题目
     * @return 提纲行；不存在返回 null
     */
    @Select("SELECT * FROM study_writing WHERE child_id = #{childId} AND layer = 'OUTLINE' "
            + "AND reference_code = #{referenceCode} AND title = #{topic} AND del_flag = 0"
            + " ORDER BY id ASC LIMIT 1")
    StudyWriting selectOutline(@Param("childId") String childId,
                                @Param("referenceCode") String referenceCode,
                                @Param("topic") String topic);

    /**
     * 在<b>给定孩子集合</b>内按业务ID 查初稿行。
     *
     * <p>家长端用这个查询：孩子集合来自关系表求交集，
     * 因此 SQL 里必然带 {@code child_id IN (...)} 条件。</p>
     *
     * @param writingCode 初稿业务ID
     * @param childIds    可见孩子 ID 集合
     * @return 初稿行；不存在或无权访问返回 null
     */
    @Select("<script>SELECT * FROM study_writing WHERE layer = 'DRAFT' AND del_flag = 0 "
            + "AND writing_code = #{writingCode} "
            + "AND child_id IN <foreach item='cid' collection='childIds' open='(' separator=',' close=')'>#{cid}</foreach> "
            + "LIMIT 1</script>")
    StudyWriting selectDraftByCode(@Param("writingCode") String writingCode,
                                   @Param("childIds") List<String> childIds);

    /**
     * 按业务ID 查孩子的初稿行（孩子端自己用，已带 child_id 条件）。
     *
     * @param childId    孩子 ID
     * @param writingCode 初稿业务ID
     * @return 初稿行；不存在返回 null
     */
    @Select("SELECT * FROM study_writing WHERE child_id = #{childId} AND layer = 'DRAFT' "
            + "AND writing_code = #{writingCode} AND del_flag = 0 LIMIT 1")
    StudyWriting selectDraftByCodeForChild(@Param("childId") String childId,
                                            @Param("writingCode") String writingCode);

    /**
     * 查一批孩子的家长反馈行。
     *
     * @param childIds 可见孩子 ID 集合
     * @return 反馈行列表
     */
    @Select("<script>SELECT * FROM study_writing WHERE layer = 'FEEDBACK' AND del_flag = 0 "
            + "AND child_id IN <foreach item='cid' collection='childIds' open='(' separator=',' close=')'>#{cid}</foreach> "
            + "ORDER BY create_time DESC</script>")
    List<StudyWriting> selectFeedbacks(@Param("childIds") List<String> childIds);

    /**
     * 查一批孩子的初稿行（家长端只读清单）。
     *
     * @param childIds 可见孩子 ID 集合
     * @return 初稿行列表（按提交时间倒序）
     */
    @Select("<script>SELECT * FROM study_writing WHERE layer = 'DRAFT' AND del_flag = 0 "
            + "AND child_id IN <foreach item='cid' collection='childIds' open='(' separator=',' close=')'>#{cid}</foreach> "
            + "ORDER BY create_time DESC</script>")
    List<StudyWriting> selectDraftsByChildIds(@Param("childIds") List<String> childIds);

    /**
     * 孩子主键 ID → 孩子业务ID。
     *
     * @param childIds 孩子主键 ID 集合
     * @return 行列表，每行含 id 与 child_code
     */
    @Select("<script>SELECT id, child_code FROM study_child WHERE del_flag = 0 "
            + "AND id IN <foreach item='cid' collection='childIds' open='(' separator=',' close=')'>#{cid}</foreach>"
            + "</script>")
    List<java.util.Map<String, Object>> selectChildCodesByIds(@Param("childIds") List<String> childIds);

    /**
     * 按业务ID 查孩子的反馈行。
     *
     * @param childId     孩子 ID
     * @param writingCode 反馈业务ID
     * @return 反馈行；不存在返回 null
     */
    @Select("SELECT * FROM study_writing WHERE child_id = #{childId} AND layer = 'FEEDBACK' "
            + "AND writing_code = #{writingCode} AND del_flag = 0 LIMIT 1")
    StudyWriting selectFeedbackByCode(@Param("childId") String childId,
                                      @Param("writingCode") String writingCode);

    /**
     * 孩子业务ID → 孩子主键 ID。
     *
     * <p>家长端只读接口按 {@code childCode} 取数时用它换算成 {@code child_id}，
     * 换算后仍要与关系表求交集。</p>
     *
     * @param childCode 孩子业务ID
     * @return 孩子主键 ID；不存在返回 null
     */
    @Select("SELECT id FROM study_child WHERE child_code = #{childCode} AND del_flag = 0 LIMIT 1")
    String selectChildIdByChildCode(@Param("childCode") String childCode);
}
