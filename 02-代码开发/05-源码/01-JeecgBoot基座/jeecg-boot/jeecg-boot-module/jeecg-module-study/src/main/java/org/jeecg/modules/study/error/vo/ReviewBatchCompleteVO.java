package org.jeecg.modules.study.error.vo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 复习队列批量完成结果。
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "复习队列批量完成结果")
public class ReviewBatchCompleteVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 提交总项数 */
    @Schema(description = "提交总项数")
    private Integer total = 0;

    /** 成功项数 */
    @Schema(description = "成功项数")
    private Integer succeeded = 0;

    /** 失败项数 */
    @Schema(description = "失败项数")
    private Integer failed = 0;

    /** 逐项结果 */
    @Schema(description = "逐项结果")
    private List<ReviewBatchItemVO> items = new ArrayList<>();
}
