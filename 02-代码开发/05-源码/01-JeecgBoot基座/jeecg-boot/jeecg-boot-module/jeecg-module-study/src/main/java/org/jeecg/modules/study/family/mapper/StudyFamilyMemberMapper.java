package org.jeecg.modules.study.family.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.jeecg.modules.study.family.entity.StudyFamilyMember;

/**
 * 家庭成员 Mapper。
 *
 * @author jeecg-study
 */
@Mapper
public interface StudyFamilyMemberMapper extends BaseMapper<StudyFamilyMember> {

    /**
     * 列出某家庭的有效成员。
     *
     * @param familyId 家庭 ID
     * @return 成员列表
     */
    @Select("SELECT * FROM study_family_member WHERE family_id = #{familyId} AND del_flag = 0 ORDER BY create_time")
    List<StudyFamilyMember> selectActiveByFamilyId(@Param("familyId") String familyId);

    /**
     * 列出某用户作为有效成员所属的家庭 ID。
     *
     * @param userId 用户 ID
     * @return 家庭 ID 列表
     */
    @Select("SELECT family_id FROM study_family_member WHERE user_id = #{userId} AND status = 1 AND del_flag = 0")
    List<String> selectActiveFamilyIdsByUserId(@Param("userId") String userId);

    /**
     * 查某用户在某家庭的成员关系。
     *
     * @param familyId 家庭 ID
     * @param userId   用户 ID
     * @return 成员；不存在返回 null
     */
    @Select("SELECT * FROM study_family_member WHERE family_id = #{familyId} AND user_id = #{userId} AND del_flag = 0 LIMIT 1")
    StudyFamilyMember selectByFamilyAndUser(@Param("familyId") String familyId, @Param("userId") String userId);
}
