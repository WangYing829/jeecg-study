package org.jeecg.modules.study.error.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import org.jeecg.modules.study.error.vo.ErrorVariantVO;

/**
 * 母题变式 Mapper（只读查询，变式由数学母题域生成）。
 *
 * <p>变式按 {@code mother_code + child_id} 取：变式由某个孩子的错题生成，
 * 但全局母题的公共变式（child_id 为空）同样可用。</p>
 *
 * @author jeecg-study
 */
@Mapper
public interface StudyErrorQuestionVariantMapper {

    /**
     * 查某母题下可用的变式（含该孩子的私有变式与全局公共变式）。
     *
     * @param motherCode 母题业务ID
     * @param childId    孩子 ID
     * @return 变式清单
     */
    @Select("SELECT variant_code AS variantCode, question_code AS questionCode, mother_code AS motherCode,"
            + " layer AS layer, stem AS stem FROM study_question_variant"
            + " WHERE mother_code = #{motherCode} AND status = 1 AND del_flag = 0"
            + " AND (child_id = #{childId} OR child_id IS NULL)"
            + " ORDER BY layer ASC, create_time ASC")
    List<ErrorVariantVO> selectByMotherCode(@Param("motherCode") String motherCode,
                                            @Param("childId") String childId);
}
