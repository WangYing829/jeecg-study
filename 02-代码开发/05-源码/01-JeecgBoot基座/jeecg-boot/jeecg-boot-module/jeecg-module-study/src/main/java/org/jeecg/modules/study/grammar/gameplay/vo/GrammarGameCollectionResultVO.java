package org.jeecg.modules.study.grammar.gameplay.vo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 我的错句收藏馆（F-GRM-04 第三个玩法）。
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "我的错句收藏馆")
public class GrammarGameCollectionResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 错句列表 */
    @Schema(description = "错句列表")
    private List<GrammarGameCollectionItemVO> items = new ArrayList<>();

    /** 错句总数 */
    @Schema(description = "错句总数")
    private Integer total;

}
