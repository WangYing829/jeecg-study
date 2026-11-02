package org.jeecg.modules.study.eye.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 心跳上报入参。
 *
 * <p><b>这里没有、也不允许有「上报时长」字段</b>：客户端只说明「我还在 / 我已离开」，
 * 有效用眼时长一律由服务端按 lastHeartbeat 基线自行累计（R-03）。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "心跳上报入参")
public class EyeHeartbeatRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 本次心跳是否处于前台有效学习状态；不传按有效处理 */
    @Schema(description = "本次心跳是否处于前台有效学习状态")
    private Boolean active;

    /** 蓝光过滤开关状态；不传表示保持当前状态 */
    @Schema(description = "蓝光过滤开关状态")
    private Boolean blueLight;
}
