package org.jeecg.modules.study.eye.vo;

import java.io.Serializable;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 孩子端护眼状态视图。
 *
 * <p><b>刻意不返回用眼时长数字</b>：学习端只显示状态图标，
 * 不出现任何「已用眼 XX 分钟」的数字（R-03 / TC-07-15）。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "孩子端护眼状态视图")
public class EyeSessionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 会话 ID */
    @Schema(description = "会话 ID")
    private String sessionId;

    /** 是否需要休息（服务端下发；为 true 时前端显示阻断遮罩） */
    @Schema(description = "是否需要休息（服务端下发；为 true 时前端显示阻断遮罩）")
    private Boolean restRequired;

    /** 休息提醒文案（含可执行的具体动作） */
    @Schema(description = "休息提醒文案")
    private String restPrompt;

    /** 蓝光过滤是否开启（已落库，刷新后保持） */
    @Schema(description = "蓝光过滤是否开启")
    private Boolean blueLightOn;

    /** 服务端最近一次心跳时间（lastHeartbeat 基线，仅供端上对齐节拍） */
    @Schema(description = "服务端最近一次心跳时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastHeartbeatAt;

    /**
     * 护眼提醒间隔（分钟）。
     *
     * <p>提醒间隔是<b>家长在家长端配的</b>，但护眼设置页在<b>孩子端</b>要把它显示出来。
     * 孩子身份没有 {@code study:parent:context:query} 权限，读不了上下文接口，
     * 所以这里随会话一起下发——服务端本来就是拿这个间隔做阈值判定的，
     * 顺带告诉端上「多久提醒一次」，页面就不用再绕一次家长端接口。</p>
     */
    @Schema(description = "护眼提醒间隔（分钟）")
    private Integer eyeIntervalMin;
}
