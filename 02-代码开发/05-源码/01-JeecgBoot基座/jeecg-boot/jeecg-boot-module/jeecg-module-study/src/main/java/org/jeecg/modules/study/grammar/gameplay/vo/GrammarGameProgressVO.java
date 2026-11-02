package org.jeecg.modules.study.grammar.gameplay.vo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 闯关进度总览（F-GRM-04）。
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "闯关进度总览")
public class GrammarGameProgressVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 各玩法的进度 */
    @Schema(description = "各玩法的进度")
    private List<GrammarGameTypeProgressVO> items = new ArrayList<>();

}
