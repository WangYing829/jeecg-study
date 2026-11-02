package org.jeecg.modules.study.grammar.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import java.util.List;

/**
 * 错句收藏馆
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "错句收藏馆")
public class GrammarCollectionResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 错句条目 */
    @Schema(description = "错句条目")
    private List<GrammarCollectionVO> items;

    /** 条数 */
    @Schema(description = "条数")
    private Integer total;

}
