package org.jeecg.modules.study.math.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.jeecg.modules.study.math.entity.StudyQuestionVariant;

/**
 * 母题变式 Mapper（行为表，按 child_id 隔离）。
 *
 * <p>变式是「某个孩子的这一关」，因此所有查询都必须带 {@code child_id} 条件；
 * 全局公共变式（{@code child_id IS NULL}）同样可见。</p>
 *
 * @author jeecg-study
 */
@Mapper
public interface StudyQuestionVariantMapper extends BaseMapper<StudyQuestionVariant> {

    /**
     * 查某孩子在某母题下的全部变式（按层级升序）。
     *
     * @param motherCode 母题业务ID
     * @param childId    孩子 ID
     * @return 变式列表
     */
    @Select("SELECT * FROM study_question_variant WHERE mother_code = #{motherCode}"
            + " AND (child_id = #{childId} OR child_id IS NULL) AND status = 1 AND del_flag = 0"
            + " ORDER BY layer ASC, create_time ASC")
    List<StudyQuestionVariant> selectByMotherAndChild(@Param("motherCode") String motherCode,
                                                      @Param("childId") String childId);

    /**
     * 查某孩子在某母题某层级的变式 —— <b>生成幂等键</b>（孩子 + 母题 + 层级）。
     *
     * @param motherCode 母题业务ID
     * @param childId    孩子 ID
     * @param layer      层级
     * @return 变式；不存在返回 null
     */
    @Select("SELECT * FROM study_question_variant WHERE mother_code = #{motherCode} AND child_id = #{childId}"
            + " AND layer = #{layer} AND del_flag = 0 ORDER BY create_time ASC, id ASC LIMIT 1")
    StudyQuestionVariant selectByChildAndLayer(@Param("motherCode") String motherCode,
                                               @Param("childId") String childId,
                                               @Param("layer") Integer layer);

    /**
     * 按变式业务ID查变式（<b>限定孩子</b>，防止跨孩子取变式）。
     *
     * @param variantCode 变式业务ID
     * @param childId     孩子 ID
     * @return 变式；不存在返回 null
     */
    @Select("SELECT * FROM study_question_variant WHERE variant_code = #{variantCode}"
            + " AND (child_id = #{childId} OR child_id IS NULL) AND del_flag = 0 LIMIT 1")
    StudyQuestionVariant selectByVariantCode(@Param("variantCode") String variantCode,
                                             @Param("childId") String childId);

    /**
     * 按变式业务ID查变式（<b>不限孩子</b>，用于识别「存在但不属于你」→ 越权 4010）。
     *
     * @param variantCode 变式业务ID
     * @return 变式；不存在返回 null
     */
    @Select("SELECT * FROM study_question_variant WHERE variant_code = #{variantCode} AND del_flag = 0 LIMIT 1")
    StudyQuestionVariant selectByVariantCodeAnyChild(@Param("variantCode") String variantCode);
}
