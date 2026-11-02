package org.jeecg.modules.study.grammar.vo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 语境例句查询结果（F-GRM-05）。
 *
 * <p><b>同一批例句</b>：{@code items} 就是背单词域那个词条关联的全部例句，
 * 数量与编码都来自 {@code study_word_context}，
 * 因此「一次建库、两处受益」不是口号而是可断言的事实。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "语境例句查询结果")
public class GrammarContextResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 词条业务ID（按词查时有值） */
    @Schema(description = "词条业务ID")
    private String wordCode;

    /** 拼写（按词查时有值） */
    @Schema(description = "拼写")
    private String spelling;

    /** 释义（按词查时有值） */
    @Schema(description = "释义")
    private String meaning;

    /** 语法点业务ID（按语法点查时有值） */
    @Schema(description = "语法点业务ID")
    private String grammarCode;

    /** 背单词域那一条例句（与语境库主例句同一句） */
    @Schema(description = "背单词域那一条例句")
    private String wordExampleSentence;

    /** 例句总数 */
    @Schema(description = "例句总数")
    private Integer total;

    /** 例句列表 */
    @Schema(description = "例句列表")
    private List<GrammarContextItemVO> items = new ArrayList<>();

}
