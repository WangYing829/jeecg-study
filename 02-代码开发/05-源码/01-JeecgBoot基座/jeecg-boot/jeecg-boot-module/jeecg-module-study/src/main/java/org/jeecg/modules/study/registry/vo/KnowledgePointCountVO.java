package org.jeecg.modules.study.registry.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 考点 → 内容数量的只读投影。
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "考点 → 内容数量的只读投影")
public class KnowledgePointCountVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 考点业务ID */
    @Schema(description = "考点业务ID")
    private String knowledgePointCode;

    /** 数量 */
    @Schema(description = "数量")
    private Integer total;

}
