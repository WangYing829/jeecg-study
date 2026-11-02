package org.jeecg.modules.study.parent.vo;

import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 家长端错因分布视图（只读）。
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "家长端错因分布视图")
public class ParentErrorReportVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 孩子业务ID */
    @Schema(description = "孩子业务ID")
    private String childCode;

    /** 错题总数 */
    @Schema(description = "错题总数")
    private Integer total;

    /** 错因分布 */
    @Schema(description = "错因分布")
    private List<ParentErrorCauseVO> causes;

    /** 薄弱考点 */
    @Schema(description = "薄弱考点")
    private List<ParentWeakPointVO> weakPoints;
}
