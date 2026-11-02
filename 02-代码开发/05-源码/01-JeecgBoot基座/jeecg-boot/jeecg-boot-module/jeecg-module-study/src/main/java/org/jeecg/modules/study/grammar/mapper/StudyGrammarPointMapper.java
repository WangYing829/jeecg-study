package org.jeecg.modules.study.grammar.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.jeecg.modules.study.grammar.entity.StudyGrammarPoint;

/**
 * 英语语法点 Mapper。
 *
 * @author jeecg-study
 */
@Mapper
public interface StudyGrammarPointMapper extends BaseMapper<StudyGrammarPoint> {

    /**
     * 按业务ID 查语法点。
     *
     * @param grammarCode 业务ID
     * @return 语法点；不存在返回 null
     */
    @Select("SELECT * FROM study_grammar_point WHERE grammar_code = #{grammarCode} AND del_flag = 0 LIMIT 1")
    StudyGrammarPoint selectByCode(@Param("grammarCode") String grammarCode);

    /**
     * 按中考题型查语法点。
     *
     * @param examType 题型；为空则不过滤
     * @return 语法点列表
     */
    @Select("<script>SELECT * FROM study_grammar_point WHERE del_flag = 0 AND status = 1 "
            + "<if test='examType != null and examType != \"\"'>AND exam_type = #{examType}</if> "
            + "ORDER BY grammar_code</script>")
    List<StudyGrammarPoint> selectByExamType(@Param("examType") String examType);

    /**
     * 查句子变形器语料（只取带语料的启用语法点）。
     *
     * @return 语法点列表
     */
    @Select("SELECT * FROM study_grammar_point WHERE del_flag = 0 AND status = 1 "
            + "AND transform_variants IS NOT NULL AND transform_variants <> '' ORDER BY grammar_code")
    List<StudyGrammarPoint> selectTransformLibrary();
}
