package org.jeecg.modules.study.math.vo;

import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 过关判定入参。
 *
 * <p>变式是否做对<b>由服务端判定</b>：前端只提交答案，不提交「我做对了」，
 * 否则判定就不可信了。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "过关判定入参")
public class MotherPassCommand implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 各条变式的作答 */
    @Schema(description = "各条变式的作答")
    private List<VariantResultCommand> variantResults;

    /** 口述内容（用自己的话讲关键关系） */
    @Schema(description = "口述内容")
    private String narrative;
}
