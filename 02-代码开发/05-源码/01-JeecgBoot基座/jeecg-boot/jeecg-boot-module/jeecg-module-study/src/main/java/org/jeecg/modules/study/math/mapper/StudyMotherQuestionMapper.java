package org.jeecg.modules.study.math.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.jeecg.modules.study.math.entity.StudyMotherQuestion;

/**
 * 数学母题 Mapper（内容表，全局共享，无 child_id）。
 *
 * <p>注意：{@code @TableLogic} 只对 MyBatis-Plus 的通用方法生效，
 * 本 Mapper 的自定义 {@code @Select} 一律手写 {@code del_flag = 0} 条件。</p>
 *
 * @author jeecg-study
 */
@Mapper
public interface StudyMotherQuestionMapper extends BaseMapper<StudyMotherQuestion> {

    /**
     * 按业务ID查母题。
     *
     * @param motherCode 业务ID
     * @return 母题；不存在返回 null
     */
    @Select("SELECT * FROM study_mother_question WHERE mother_code = #{motherCode} AND del_flag = 0 LIMIT 1")
    StudyMotherQuestion selectByMotherCode(@Param("motherCode") String motherCode);

    /**
     * 按「学科 + 考点 + 题型名」查母题 —— 母题保存的<b>幂等键</b>。
     *
     * <p>同一道母题重复保存只更新不新建，避免同一个关卡被重复落库。</p>
     *
     * @param subjectCode        学科编码
     * @param knowledgePointCode 考点业务ID
     * @param title              题型名
     * @return 母题；不存在返回 null
     */
    @Select("SELECT * FROM study_mother_question WHERE subject_code = #{subjectCode}"
            + " AND IFNULL(knowledge_point_code, '') = IFNULL(#{knowledgePointCode}, '')"
            + " AND title = #{title} AND del_flag = 0 ORDER BY id ASC LIMIT 1")
    StudyMotherQuestion selectByIdentity(@Param("subjectCode") String subjectCode,
                                         @Param("knowledgePointCode") String knowledgePointCode,
                                         @Param("title") String title);

    /**
     * 列出某学科的全部启用母题（闯关首页的候选集）。
     *
     * @param subjectCode 学科编码
     * @return 母题列表
     */
    @Select("SELECT * FROM study_mother_question WHERE subject_code = #{subjectCode}"
            + " AND status = 1 AND del_flag = 0 ORDER BY order_no ASC, mother_code ASC")
    List<StudyMotherQuestion> selectActiveBySubject(@Param("subjectCode") String subjectCode);

    /**
     * 统计某母题已生成的变式数（内容供给量，全局口径）。
     *
     * @param motherCode 母题业务ID
     * @return 变式条数
     */
    @Select("SELECT COUNT(*) FROM study_question_variant WHERE mother_code = #{motherCode} AND del_flag = 0")
    int countVariants(@Param("motherCode") String motherCode);

    /**
     * 回写母题的已生成变式数。
     *
     * @param motherCode   母题业务ID
     * @param variantCount 变式条数
     * @return 影响行数
     */
    @Update("UPDATE study_mother_question SET variant_count = #{variantCount}, update_time = NOW()"
            + " WHERE mother_code = #{motherCode} AND del_flag = 0")
    int updateVariantCount(@Param("motherCode") String motherCode,
                           @Param("variantCount") Integer variantCount);
}
