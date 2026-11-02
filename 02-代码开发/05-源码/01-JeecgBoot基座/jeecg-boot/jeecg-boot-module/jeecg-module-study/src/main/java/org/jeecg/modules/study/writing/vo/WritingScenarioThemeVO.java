package org.jeecg.modules.study.writing.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 写作情境主题视图（一个主题下有多个不同处境的情境）。
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "写作情境主题")
public class WritingScenarioThemeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主题编码 */
    @Schema(description = "主题编码")
    private String themeCode;

    /** 主题名称 */
    @Schema(description = "主题名称")
    private String themeName;

    /** 该主题下的情境条数 */
    @Schema(description = "情境条数")
    private Integer scenarioCount;
}
