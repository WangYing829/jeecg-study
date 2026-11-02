package org.jeecg.modules.study.word.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 单元入参（进入单元 / 完成单元共用）。
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "单元入参")
public class WordUnitRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 孩子业务ID；孩子端可不传（取会话身份），家长端必传 */
    @Schema(description = "孩子业务ID")
    private String childCode;

    /** 教材版本ID */
    @Schema(description = "教材版本ID")
    private String textbookVersionId;

    /** 单元编码 */
    @Schema(description = "单元编码")
    private String unitCode;

    /** 是否开启纯净冲刺模式（关闭游戏化元素） */
    @Schema(description = "是否开启纯净冲刺模式")
    private Boolean pureMode;
}
