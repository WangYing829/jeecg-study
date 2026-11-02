package org.jeecg.modules.study.writing.vo;

import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 写作情境列表视图。
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "写作情境列表")
public class WritingScenarioListVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 情境清单 */
    @Schema(description = "情境清单")
    private List<WritingScenarioVO> items;

    /** 条数 */
    @Schema(description = "条数")
    private Integer total;
}
