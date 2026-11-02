package org.jeecg.modules.study.writing.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.jeecg.modules.study.writing.entity.StudyWritingScenario;
import org.jeecg.modules.study.writing.vo.WritingScenarioThemeVO;

/**
 * 写作情境库 Mapper（<b>内容表</b>）。
 *
 * <p>情境库是全局共享的内容，<b>没有 child_id 列</b>，因此本 Mapper
 * 的查询也不带任何孩子维度；孩子的作答走 {@code study_writing}。</p>
 *
 * <p><b>排序确定性</b>：所有查询都带 {@code ORDER BY}，且末位排序键
 * 是唯一键 {@code scenario_code}，并列时顺序可判定（见
 * {@code StudySqlOrderDeterminismTest} 的 SQL-A / SQL-B / SQL-C）。</p>
 *
 * @author jeecg-study
 */
@Mapper
public interface StudyWritingScenarioMapper extends BaseMapper<StudyWritingScenario> {

    /**
     * 主题清单（同一主题下多个情境，按主题聚合）。
     *
     * @param stageCode 学段；为空不过滤
     * @return 主题列表
     */
    @Select("<script>SELECT theme_code AS themeCode, theme_name AS themeName, COUNT(*) AS scenarioCount "
            + "FROM study_writing_scenario WHERE del_flag = 0 AND status = 1 "
            + "<if test='stageCode != null and stageCode != \"\"'>AND stage_code = #{stageCode}</if> "
            + "GROUP BY theme_code, theme_name ORDER BY theme_code</script>")
    List<WritingScenarioThemeVO> selectThemes(@Param("stageCode") String stageCode);

    /**
     * 情境列表（可按主题 / 学段过滤）。
     *
     * @param themeCode 主题编码；为空不过滤
     * @param stageCode 学段；为空不过滤
     * @return 情境行列表
     */
    @Select("<script>SELECT * FROM study_writing_scenario WHERE del_flag = 0 AND status = 1 "
            + "<if test='themeCode != null and themeCode != \"\"'>AND theme_code = #{themeCode}</if> "
            + "<if test='stageCode != null and stageCode != \"\"'>AND stage_code = #{stageCode}</if> "
            + "ORDER BY theme_code, order_weight, scenario_code</script>")
    List<StudyWritingScenario> selectScenarios(@Param("themeCode") String themeCode,
                                                @Param("stageCode") String stageCode);

    /**
     * 按业务ID 查一个情境（唯一键，结果至多一行）。
     *
     * @param scenarioCode 情境业务ID
     * @return 情境行；不存在返回 null
     */
    @Select("SELECT * FROM study_writing_scenario WHERE del_flag = 0 AND status = 1 "
            + "AND scenario_code = #{scenarioCode}")
    StudyWritingScenario selectByCode(@Param("scenarioCode") String scenarioCode);
}
