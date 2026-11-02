package org.jeecg.modules.study.practice.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 全局上下文只读 Mapper。
 *
 * <p>与错题域同名 Mapper 各自独立：两个域分目录独立编译，不互相引用实现类。</p>
 *
 * @author jeecg-study
 */
@Mapper
public interface StudyPracticeContextMapper {

    /**
     * 取某孩子当前生效的上下文 ID。
     *
     * @param childId 孩子 ID
     * @return 上下文 ID；未配置返回 null
     */
    @Select("SELECT id FROM study_context WHERE child_id = #{childId} AND del_flag = 0"
            + " ORDER BY is_current DESC, create_time DESC, id DESC LIMIT 1")
    String selectCurrentContextId(@Param("childId") String childId);
}
