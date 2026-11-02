package org.jeecg.modules.study.practice.vo;

import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 解析。
 *
 * <p><b>红线 R01-2</b>：未作答直接请求解析会被拒绝（4003），本对象只会在作答之后出现。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "解析")
public class ExplanationVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 内容业务ID */
    @Schema(description = "内容业务ID")
    private String contentId;

    /** 解析形态 */
    @Schema(description = "解析形态")
    private String type;

    /** 解析分段 */
    @Schema(description = "解析分段")
    private List<ExplanationSectionVO> sections;

    /** 常见错误路径——**由规则从错因推导**，不是模型自由发挥 */
    @Schema(description = "常见错误路径")
    private String wrongPath;

    /** 相关题目业务ID */
    @Schema(description = "相关题目业务ID")
    private List<String> relatedQuestionIds;
}
