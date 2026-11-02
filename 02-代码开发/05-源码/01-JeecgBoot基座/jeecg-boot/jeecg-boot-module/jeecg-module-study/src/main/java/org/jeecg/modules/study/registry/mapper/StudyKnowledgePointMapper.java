package org.jeecg.modules.study.registry.mapper;

import java.util.Date;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.jeecg.modules.study.registry.entity.StudyKnowledgePoint;
import org.jeecg.modules.study.registry.vo.KnowledgePointVersionVO;

/**
 * 坐标树节点 Mapper。
 *
 * <p>注意：{@code @TableLogic} 只对 MyBatis-Plus 的通用方法生效，
 * 本 Mapper 的自定义 {@code @Select} 一律手写 {@code del_flag = 0} 条件。</p>
 *
 * @author jeecg-study
 */
@Mapper
public interface StudyKnowledgePointMapper extends BaseMapper<StudyKnowledgePoint> {

    /**
     * 按业务ID查节点。<b>包含已失效节点</b> —— 历史错题仍要解析到旧坐标（TC-01-13）。
     *
     * @param pointCode 业务ID
     * @return 节点；不存在返回 null
     */
    @Select("SELECT * FROM study_knowledge_point WHERE point_code = #{pointCode} AND del_flag = 0 LIMIT 1")
    StudyKnowledgePoint selectByPointCode(@Param("pointCode") String pointCode);

    /**
     * 列出全部现行节点。
     *
     * @return 节点列表
     */
    @Select("SELECT * FROM study_knowledge_point WHERE status = 1 AND del_flag = 0 "
            + "ORDER BY level, sort_no, create_time")
    List<StudyKnowledgePoint> selectValidAll();

    /**
     * 列出某学科的全部节点（<b>含已失效</b>），用于版本比对与历史追溯。
     *
     * @param subjectCode 学科编码
     * @return 节点列表
     */
    @Select("SELECT * FROM study_knowledge_point WHERE subject_code = #{subjectCode} AND del_flag = 0 "
            + "ORDER BY level, sort_no, create_time")
    List<StudyKnowledgePoint> selectBySubjectIncludingInvalid(@Param("subjectCode") String subjectCode);

    /**
     * 版本统计：考纲版本清单。
     *
     * @return 版本统计列表
     */
    @Select("SELECT version, COUNT(*) AS node_count, MIN(effective_from) AS effective_from, "
            + "MAX(invalid_at) AS invalid_at FROM study_knowledge_point WHERE del_flag = 0 "
            + "GROUP BY version ORDER BY MIN(effective_from), version")
    List<KnowledgePointVersionVO> selectVersionStats();

    /**
     * 失效一批现行节点（生成新版本时调用）。
     *
     * <p>只置失效标记与失效日期，<b>不删除记录</b>（TC-01-12 / TC-08-23）。</p>
     *
     * @param ids       待失效的节点 ID 集合
     * @param invalidAt 失效日期
     * @return 影响行数
     */
    @Update("<script>UPDATE study_knowledge_point SET status = 0, invalid_at = #{invalidAt}, update_time = NOW() "
            + "WHERE status = 1 AND del_flag = 0 AND id IN "
            + "<foreach collection='ids' item='it' open='(' separator=',' close=')'>#{it}</foreach></script>")
    int invalidateByIds(@Param("ids") List<String> ids, @Param("invalidAt") Date invalidAt);

    /**
     * 更新考点四属性（显式写全部四列，允许置空）。
     *
     * @param id                 节点 ID
     * @param questionType       题型
     * @param scoreWeight        分值权重
     * @param errorTypes         常见错因（逗号分隔）
     * @param motherQuestionCode 母题业务ID
     * @return 影响行数
     */
    @Update("UPDATE study_knowledge_point SET question_type = #{questionType}, score_weight = #{scoreWeight}, "
            + "error_types = #{errorTypes}, mother_question_code = #{motherQuestionCode}, update_time = NOW() "
            + "WHERE id = #{id} AND del_flag = 0")
    int updateAttributes(@Param("id") String id,
                         @Param("questionType") String questionType,
                         @Param("scoreWeight") java.math.BigDecimal scoreWeight,
                         @Param("errorTypes") String errorTypes,
                         @Param("motherQuestionCode") String motherQuestionCode);
}
