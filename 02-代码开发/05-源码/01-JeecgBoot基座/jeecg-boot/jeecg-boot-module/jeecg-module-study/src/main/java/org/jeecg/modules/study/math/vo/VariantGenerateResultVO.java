package org.jeecg.modules.study.math.vo;

import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 三层变式生成结果。
 *
 * <p><b>不含答案</b>：变式的答案只在服务端判定时使用（R-01）。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "三层变式生成结果")
public class VariantGenerateResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 母题业务ID */
    @Schema(description = "母题业务ID")
    private String motherCode;

    /** 三层变式 */
    @Schema(description = "三层变式")
    private List<VariantVO> variants;

    /** 过关所需变式全对数 */
    @Schema(description = "过关所需变式全对数")
    private Integer passVariantCount;

    /** 是否命中幂等（本次没有新建变式） */
    @Schema(description = "是否命中幂等")
    private boolean duplicated;
}
