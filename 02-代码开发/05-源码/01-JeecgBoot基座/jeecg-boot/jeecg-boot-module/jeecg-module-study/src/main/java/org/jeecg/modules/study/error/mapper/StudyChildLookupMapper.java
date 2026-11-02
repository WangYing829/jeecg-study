package org.jeecg.modules.study.error.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 孩子档案只读 Mapper（错题域只按 childCode 定位孩子，数据范围仍由 scope 服务判定）。
 *
 * @author jeecg-study
 */
@Mapper
public interface StudyChildLookupMapper {

    /**
     * 按孩子业务ID查孩子主键。
     *
     * @param childCode 孩子业务ID（CHD-000001）
     * @return 孩子主键；不存在返回 null
     */
    @Select("SELECT id FROM study_child WHERE child_code = #{childCode} AND del_flag = 0 LIMIT 1")
    String selectIdByChildCode(@Param("childCode") String childCode);
}
