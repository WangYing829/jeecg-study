package org.jeecg.modules.study.family.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.jeecg.modules.study.family.entity.StudyFamily;

/**
 * 家庭 Mapper。
 *
 * @author jeecg-study
 */
@Mapper
public interface StudyFamilyMapper extends BaseMapper<StudyFamily> {

    /**
     * 按家庭码 HMAC 哈希定位家庭（家庭码是确定性哈希，可以走唯一索引）。
     *
     * @param familyCodeHash 家庭码哈希
     * @return 家庭；不存在返回 null
     */
    @Select("SELECT * FROM study_family WHERE family_code = #{familyCodeHash} AND del_flag = 0 LIMIT 1")
    StudyFamily selectByCodeHash(@Param("familyCodeHash") String familyCodeHash);
}
