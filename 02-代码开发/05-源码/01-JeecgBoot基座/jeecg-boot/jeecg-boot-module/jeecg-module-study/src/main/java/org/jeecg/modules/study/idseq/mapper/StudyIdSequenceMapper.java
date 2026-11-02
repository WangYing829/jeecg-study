package org.jeecg.modules.study.idseq.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.jeecg.modules.study.idseq.entity.StudyIdSequence;

/**
 * 业务ID 序列 Mapper。
 *
 * @author jeecg-study
 */
@Mapper
public interface StudyIdSequenceMapper extends BaseMapper<StudyIdSequence> {

    /**
     * 悲观锁读序列行（FOR UPDATE），保证并发下序号不重复。
     *
     * @param prefix 序列前缀
     * @return 序列行；不存在返回 null
     */
    @Select("SELECT * FROM study_id_sequence WHERE prefix = #{prefix} AND del_flag = 0 FOR UPDATE")
    StudyIdSequence selectForUpdate(@Param("prefix") String prefix);
}
