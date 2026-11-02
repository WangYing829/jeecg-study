package org.jeecg.modules.study.context.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.jeecg.modules.study.context.entity.StudyContext;

/**
 * 全局上下文 Mapper。
 *
 * <p>上下文按 {@code child_id} 隔离，所有查询都强制带 child_id 条件。</p>
 *
 * <p><b>命名说明</b>：本接口刻意不叫 {@code StudyContextMapper} ——
 * MyBatis-Spring 用「类名去首字母小写」当 Mapper 的 Bean 名，
 * 同名接口放在不同包下会在启动时抛 {@code ConflictingBeanDefinitionException}。
 * {@code study_context} 表被多个域引用，故用 {@code StudyGlobalContextMapper} 保证全局唯一。</p>
 *
 * @author jeecg-study
 */
@Mapper
public interface StudyGlobalContextMapper extends BaseMapper<StudyContext> {

    /**
     * 取某孩子当前生效的上下文。
     *
     * @param childId 孩子 ID
     * @return 当前上下文；未配置返回 null
     */
    @Select("SELECT * FROM study_context WHERE child_id = #{childId} AND is_current = 1 AND del_flag = 0 "
            + "ORDER BY effective_from DESC, create_time DESC, id DESC LIMIT 1")
    StudyContext selectCurrentByChildId(@Param("childId") String childId);

    /**
     * 列出某孩子的历史上下文（按生效时间倒序）。
     *
     * @param childId 孩子 ID
     * @return 上下文列表
     */
    @Select("SELECT * FROM study_context WHERE child_id = #{childId} AND del_flag = 0 "
            + "ORDER BY effective_from DESC, create_time DESC")
    List<StudyContext> selectByChildId(@Param("childId") String childId);

    /**
     * 按业务ID查上下文。
     *
     * @param contextCode 业务ID（CTX-000001）
     * @return 上下文；不存在返回 null
     */
    @Select("SELECT * FROM study_context WHERE context_code = #{contextCode} AND del_flag = 0 LIMIT 1")
    StudyContext selectByContextCode(@Param("contextCode") String contextCode);

    /**
     * 把某孩子所有上下文置为非当前（历史保留，不删除）。
     *
     * @param childId 孩子 ID
     * @return 影响行数
     */
    @Update("UPDATE study_context SET is_current = 0, update_time = NOW() "
            + "WHERE child_id = #{childId} AND is_current = 1 AND del_flag = 0")
    int clearCurrentByChildId(@Param("childId") String childId);
}
