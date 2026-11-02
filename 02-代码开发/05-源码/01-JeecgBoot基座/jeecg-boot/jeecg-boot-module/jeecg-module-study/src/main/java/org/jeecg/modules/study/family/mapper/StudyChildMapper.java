package org.jeecg.modules.study.family.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.jeecg.modules.study.family.entity.StudyChild;

/**
 * 孩子档案 Mapper。
 *
 * @author jeecg-study
 */
@Mapper
public interface StudyChildMapper extends BaseMapper<StudyChild> {

    /**
     * 按业务ID 查孩子。
     *
     * @param childCode 业务ID（CHD-000001）
     * @return 孩子；不存在返回 null
     */
    @Select("SELECT * FROM study_child WHERE child_code = #{childCode} AND del_flag = 0 LIMIT 1")
    StudyChild selectByChildCode(@Param("childCode") String childCode);

    /**
     * 按登录账号查孩子。
     *
     * @param userId sys_user.id
     * @return 孩子；不存在返回 null
     */
    @Select("SELECT * FROM study_child WHERE user_id = #{userId} AND del_flag = 0 LIMIT 1")
    StudyChild selectByUserId(@Param("userId") String userId);

    /**
     * 列出某家庭的有效孩子。
     *
     * @param familyId 家庭 ID
     * @return 孩子列表
     */
    @Select("SELECT * FROM study_child WHERE family_id = #{familyId} AND del_flag = 0 ORDER BY create_time")
    List<StudyChild> selectByFamilyId(@Param("familyId") String familyId);

    /**
     * 列出多个孩子的业务ID（用于数据范围拼 where）。
     *
     * @param childIds 孩子 ID 集合
     * @return 业务ID 列表
     */
    @Select("<script>SELECT child_code FROM study_child WHERE del_flag = 0 AND id IN "
            + "<foreach collection='childIds' item='it' open='(' separator=',' close=')'>#{it}</foreach></script>")
    List<String> selectChildCodesByIds(@Param("childIds") List<String> childIds);
}
