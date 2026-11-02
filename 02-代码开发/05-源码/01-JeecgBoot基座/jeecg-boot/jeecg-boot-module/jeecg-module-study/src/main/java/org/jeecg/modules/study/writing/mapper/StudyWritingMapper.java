package org.jeecg.modules.study.writing.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.jeecg.modules.study.writing.entity.StudyWriting;

/**
 * 佳作拆解库 Mapper（<b>内容表</b>）。
 *
 * <p>本 Mapper 的所有查询都强制 {@code child_id IS NULL} ——
 * 拆解库是全局共享的内容，孩子练习数据与家长反馈一律不从这里读。</p>
 *
 * @author jeecg-study
 */
@Mapper
public interface StudyWritingMapper extends BaseMapper<StudyWriting> {

    /**
     * 查拆解库内容（可按题材 / 学段过滤）。
     *
     * @param layerCodes 层编码集合
     * @param genre      题材；为空不过滤
     * @param stageCode  学段；为空不过滤
     * @return 拆解库行列表
     */
    @Select("<script>SELECT * FROM study_writing WHERE child_id IS NULL AND del_flag = 0 AND status = 1 "
            + "AND layer IN <foreach item='code' collection='layerCodes' open='(' separator=',' close=')'>#{code}</foreach> "
            + "<if test='genre != null and genre != \"\"'>AND genre = #{genre}</if> "
            + "<if test='stageCode != null and stageCode != \"\"'>AND stage_code = #{stageCode}</if> "
            + "ORDER BY reference_code, layer, writing_code</script>")
    List<StudyWriting> selectLibrary(@Param("layerCodes") List<String> layerCodes,
                                     @Param("genre") String genre,
                                     @Param("stageCode") String stageCode);

    /**
     * 按业务ID 查拆解库的一层。
     *
     * @param writingCode 业务ID
     * @return 拆解库行；不存在返回 null
     */
    @Select("SELECT * FROM study_writing WHERE child_id IS NULL AND del_flag = 0 "
            + "AND writing_code = #{writingCode} LIMIT 1")
    StudyWriting selectLibraryByCode(@Param("writingCode") String writingCode);

    /**
     * 按拆解集编码查整组六层。
     *
     * @param referenceCode 拆解集编码
     * @return 六层行列表
     */
    @Select("SELECT * FROM study_writing WHERE child_id IS NULL AND del_flag = 0 "
            + "AND reference_code = #{referenceCode} ORDER BY layer, writing_code")
    List<StudyWriting> selectLibraryByGroup(@Param("referenceCode") String referenceCode);
}
