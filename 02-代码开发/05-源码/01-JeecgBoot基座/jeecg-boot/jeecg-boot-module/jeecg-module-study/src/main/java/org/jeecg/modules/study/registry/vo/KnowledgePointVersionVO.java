package org.jeecg.modules.study.registry.vo;

import java.io.Serializable;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 考纲版本统计（版本列表）。
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "考纲版本统计")
public class KnowledgePointVersionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 版本号 v1 / v2 */
    @Schema(description = "版本号 v1 / v2")
    private String version;

    /** 该版本的节点数 */
    @Schema(description = "该版本的节点数")
    private Integer nodeCount;

    /** 生效日期 */
    @Schema(description = "生效日期")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    private Date effectiveFrom;

    /** 失效日期 */
    @Schema(description = "失效日期")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    private Date invalidAt;

}
