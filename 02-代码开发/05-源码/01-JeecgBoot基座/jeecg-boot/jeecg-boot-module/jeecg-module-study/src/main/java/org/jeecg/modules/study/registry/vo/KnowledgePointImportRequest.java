package org.jeecg.modules.study.registry.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 导入坐标树入参（幂等）。
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "导入坐标树入参（幂等）")
public class KnowledgePointImportRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 目标版本，如 v1 / v2；不传默认 v1 */
    @Schema(description = "目标版本，如 v1 / v2")
    private String version;

    /** 学科编码；不传则取节点上的学科编码 */
    @Schema(description = "学科编码")
    private String subjectCode;

    /** 节点列表（扁平，父子靠 refKey / parentRefKey 关联） */
    @Schema(description = "节点列表")
    private List<KnowledgePointImportNode> nodes;

}
