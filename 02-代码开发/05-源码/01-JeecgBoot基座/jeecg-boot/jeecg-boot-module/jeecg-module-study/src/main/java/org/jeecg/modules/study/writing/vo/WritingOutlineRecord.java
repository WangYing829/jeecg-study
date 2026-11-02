package org.jeecg.modules.study.writing.vo;

import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 提纲行里存的<b>落库快照</b>（{@code study_writing.content} 的 JSON 结构）。
 *
 * <p>用途：提纲提交要幂等重放 —— 重复提交同一份提纲时，
 * 直接把上次落库的快照读回来原样返回，不再重复判定、也不再重复写库。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "提纲落库快照")
public class WritingOutlineRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 提纲是否通过 */
    @Schema(description = "提纲是否通过")
    private Boolean passed;

    /** 下一步 */
    @Schema(description = "下一步")
    private String nextStep;

    /** 开头写什么 */
    @Schema(description = "开头写什么")
    private String opening;

    /** 正文分段写什么 */
    @Schema(description = "正文分段写什么")
    private List<String> body;

    /** 结尾怎么回扣 */
    @Schema(description = "结尾怎么回扣")
    private String ending;

    /** 提纲反馈 */
    @Schema(description = "提纲反馈")
    private List<WritingOutlineFeedbackVO> feedback;
}
