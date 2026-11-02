package org.jeecg.modules.study.math.mapper;

import java.math.BigDecimal;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 考点只读 Mapper（数学域只取「分值权重」，不写坐标树）。
 *
 * <p>坐标树归考点域维护，数学域<b>只读</b>：闯关顺序要按考纲分值权重排（F-MTH-05）。</p>
 *
 * @author jeecg-study
 */
@Mapper
public interface StudyMathKnowledgePointMapper {

    /**
     * 取考点的分值权重。
     *
     * @param pointCode 考点业务ID
     * @return 分值权重；未挂考点或没有权重返回 null
     */
    @Select("SELECT score_weight FROM study_knowledge_point WHERE point_code = #{pointCode}"
            + " AND del_flag = 0 LIMIT 1")
    BigDecimal selectOutlineWeight(@Param("pointCode") String pointCode);
}
