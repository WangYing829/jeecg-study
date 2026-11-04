package org.jeecg.modules.study.error.vo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 复习队列批量完成入参。
 *
 * <p>每一项与「完成一项复习」的入参完全一致（{@link ReviewCompleteCommand}），
 * 服务端逐项推进排期，单项失败不影响其它项。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "复习队列批量完成入参")
public class ReviewBatchCompleteCommand implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 待完成的复习项 */
    @Schema(description = "待完成的复习项")
    private List<ReviewCompleteCommand> items = new ArrayList<>();
}
