package org.jeecg.modules.study.practice.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 讲解内容。
 *
 * <p><b>AI 只做解释层</b>（R-02）：讲解文本来自题目解析与规则推导，
 * 判定本身永远来自确定性规则。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "讲解内容")
public class ExplainVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 讲解形态：STEP_BY_STEP / DEGRADED */
    @Schema(description = "讲解形态")
    private String type;

    /** 讲解正文 */
    @Schema(description = "讲解正文")
    private String text;
}
