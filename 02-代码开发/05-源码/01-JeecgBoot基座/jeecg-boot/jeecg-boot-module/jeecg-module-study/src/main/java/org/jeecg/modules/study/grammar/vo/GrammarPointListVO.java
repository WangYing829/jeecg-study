package org.jeecg.modules.study.grammar.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import java.util.List;

/**
 * 语法点列表
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "语法点列表")
public class GrammarPointListVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 语法点 */
    @Schema(description = "语法点")
    private List<GrammarPointVO> items;

    /** 条数 */
    @Schema(description = "条数")
    private Integer total;

}
