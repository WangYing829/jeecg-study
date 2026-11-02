package org.jeecg.modules.study.writing.vo;

import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 佳作拆解库列表视图。
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "佳作拆解库列表")
public class WritingDissectionListVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 拆解清单 */
    @Schema(description = "拆解清单")
    private List<WritingDissectionVO> items;

    /** 条数 */
    @Schema(description = "条数")
    private Integer total;
}
