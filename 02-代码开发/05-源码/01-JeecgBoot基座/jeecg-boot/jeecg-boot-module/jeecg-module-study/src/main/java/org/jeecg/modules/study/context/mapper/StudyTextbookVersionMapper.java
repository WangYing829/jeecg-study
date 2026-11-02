package org.jeecg.modules.study.context.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.jeecg.modules.study.context.entity.StudyTextbookVersion;

/**
 * 教材版本维表 Mapper。
 *
 * @author jeecg-study
 */
@Mapper
public interface StudyTextbookVersionMapper extends BaseMapper<StudyTextbookVersion> {

    /**
     * 按学段取教材版本（按排序号升序）。
     *
     * @param stageCode 学段编码；为空则不过滤
     * @return 教材版本列表
     */
    @Select("<script>SELECT * FROM study_textbook_version WHERE del_flag = 0 "
            + "<if test='stageCode != null and stageCode != \"\"'> AND stage_code = #{stageCode}</if> "
            + "ORDER BY sort_no, version_code</script>")
    List<StudyTextbookVersion> selectByStage(@Param("stageCode") String stageCode);

    /**
     * 按版本编码查教材版本。
     *
     * @param versionCode 版本编码
     * @return 教材版本；不存在返回 null
     */
    @Select("SELECT * FROM study_textbook_version WHERE version_code = #{versionCode} AND del_flag = 0 LIMIT 1")
    StudyTextbookVersion selectByVersionCode(@Param("versionCode") String versionCode);
}
