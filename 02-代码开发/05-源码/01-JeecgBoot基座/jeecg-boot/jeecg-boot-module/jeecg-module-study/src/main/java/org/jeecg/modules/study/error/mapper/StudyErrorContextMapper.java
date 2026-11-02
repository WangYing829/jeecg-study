package org.jeecg.modules.study.error.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 全局上下文只读 Mapper。
 *
 * <p>上下文按孩子隔离（ADR-018），因此错题与排期都挂在孩子当前上下文下。</p>
 *
 * @author jeecg-study
 */
@Mapper
public interface StudyErrorContextMapper {

    /**
     * 取某孩子当前生效的上下文 ID。
     *
     * @param childId 孩子 ID
     * @return 上下文 ID；该孩子还没配置上下文返回 null
     */
    @Select("SELECT id FROM study_context WHERE child_id = #{childId} AND del_flag = 0"
            + " ORDER BY is_current DESC, create_time DESC, id DESC LIMIT 1")
    String selectCurrentContextId(@Param("childId") String childId);
}
