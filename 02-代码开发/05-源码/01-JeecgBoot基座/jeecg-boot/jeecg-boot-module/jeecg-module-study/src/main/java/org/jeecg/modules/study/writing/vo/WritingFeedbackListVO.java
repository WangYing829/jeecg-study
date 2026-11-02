package org.jeecg.modules.study.writing.vo;

import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 家长反馈清单视图。
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "家长反馈清单")
public class WritingFeedbackListVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 反馈清单（只含当前家长可见的孩子） */
    @Schema(description = "反馈清单")
    private List<WritingFeedbackVO> items;

    /** 条数 */
    @Schema(description = "条数")
    private Integer total;
}
