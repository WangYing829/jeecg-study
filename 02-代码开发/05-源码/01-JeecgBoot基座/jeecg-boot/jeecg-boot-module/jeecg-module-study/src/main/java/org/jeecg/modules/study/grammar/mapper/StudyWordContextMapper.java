package org.jeecg.modules.study.grammar.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 词条 ↔ 例句关联 Mapper（F-GRM-05）。
 *
 * <p>这一张表是「同一批例句」的连接处：查到的例句编码与数量，
 * 就是背单词域与语法域共同看到的那一批。</p>
 *
 * @author jeecg-study
 */
@Mapper
public interface StudyWordContextMapper {

    /**
     * 某词条关联的例句条数（启用且未删除）。
     *
     * @param wordCode 词条业务ID
     * @return 条数
     */
    @Select("SELECT COUNT(*) FROM study_word_context WHERE word_code = #{wordCode} "
            + "AND del_flag = 0 AND status = 1")
    int countByWordCode(@Param("wordCode") String wordCode);

    /**
     * 某词条关联的例句业务ID（按展示顺序）。
     *
     * @param wordCode 词条业务ID
     * @return 例句业务ID 列表
     */
    @Select("SELECT sentence_code FROM study_word_context WHERE word_code = #{wordCode} "
            + "AND del_flag = 0 AND status = 1 "
            + "ORDER BY FIELD(relation_type, 'MAIN', 'EXTEND'), sort_no, id")
    List<String> selectSentenceCodesByWordCode(@Param("wordCode") String wordCode);

}
