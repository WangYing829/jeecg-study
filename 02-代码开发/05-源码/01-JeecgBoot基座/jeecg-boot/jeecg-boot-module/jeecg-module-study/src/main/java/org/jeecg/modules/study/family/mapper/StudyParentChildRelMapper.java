package org.jeecg.modules.study.family.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.jeecg.modules.study.family.entity.StudyParentChildRel;

/**
 * 家长↔孩子 关系 Mapper。
 *
 * @author jeecg-study
 */
@Mapper
public interface StudyParentChildRelMapper extends BaseMapper<StudyParentChildRel> {

    /**
     * 列出某家长生效关联的孩子 ID。
     *
     * @param parentUserId 家长 sys_user.id
     * @return 孩子 ID 列表
     */
    @Select("SELECT child_id FROM study_parent_child_rel WHERE parent_user_id = #{parentUserId} AND status = 1 AND del_flag = 0")
    List<String> selectActiveChildIds(@Param("parentUserId") String parentUserId);

    /**
     * 列出某孩子生效关联的家长用户 ID。
     *
     * @param childId 孩子 ID
     * @return 家长用户 ID 列表
     */
    @Select("SELECT parent_user_id FROM study_parent_child_rel WHERE child_id = #{childId} AND status = 1 AND del_flag = 0")
    List<String> selectActiveParentUserIds(@Param("childId") String childId);

    /**
     * 查某家长与某孩子的关系行（不论状态，用于判断重复关联）。
     *
     * @param parentUserId 家长 sys_user.id
     * @param childId      孩子 ID
     * @return 关系行；不存在返回 null
     */
    @Select("SELECT * FROM study_parent_child_rel WHERE parent_user_id = #{parentUserId} AND child_id = #{childId} AND del_flag = 0 LIMIT 1")
    StudyParentChildRel selectByParentAndChild(@Param("parentUserId") String parentUserId, @Param("childId") String childId);
}
