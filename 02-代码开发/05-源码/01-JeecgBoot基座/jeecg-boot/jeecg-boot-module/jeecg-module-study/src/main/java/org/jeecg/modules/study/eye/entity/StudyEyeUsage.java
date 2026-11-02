package org.jeecg.modules.study.eye.entity;

import java.io.Serializable;
import java.util.Date;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecg.common.system.base.entity.JeecgEntity;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 用眼记录（一次学习会话一行）。
 *
 * <p><b>时长口径（R-03）</b>：{@code active_minutes} 是<b>服务端心跳累计</b>出来的有效用眼分钟数，
 * 客户端没有任何上报时长的入口。</p>
 *
 * <p><b>本域对 {@code update_time} 的约定</b>：护眼域把该列用作
 * <b>{@code lastHeartbeat}（服务端最近一次心跳时间）</b>，每次心跳 / 结束会话都会显式推进它；
 * 它同时充当会话的心跳基线与幂等基线。休息确认等动作<b>不</b>推进该列，
 * 以免打断心跳基线。</p>
 *
 * @author jeecg-study
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "用眼记录")
@TableName("study_eye_usage")
public class StudyEyeUsage extends JeecgEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 所属孩子 ID（多用户隔离维度） */
    @Schema(description = "所属孩子 ID")
    @TableField(value = "child_id")
    private String childId;

    /** 上下文 ID；未配置上下文时为空串（上下文未配置不是错误） */
    @Schema(description = "上下文 ID")
    @TableField(value = "context_id")
    private String contextId;

    /** 学习会话 ID（全局唯一） */
    @Schema(description = "学习会话 ID")
    @TableField(value = "session_id")
    private String sessionId;

    /** 会话开始时间 */
    @Schema(description = "会话开始时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "start_at")
    private Date startAt;

    /** 会话结束时间；未结束时为空 */
    @Schema(description = "会话结束时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "end_at")
    private Date endAt;

    /** 累计有效用眼分钟（服务端心跳累计） */
    @Schema(description = "累计有效用眼分钟")
    @TableField(value = "active_minutes")
    private Integer activeMinutes;

    /** 计入有效时长的心跳次数 */
    @Schema(description = "计入有效时长的心跳次数")
    @TableField(value = "heartbeat_count")
    private Integer heartbeatCount;

    /** 休息提醒触发次数 */
    @Schema(description = "休息提醒触发次数")
    @TableField(value = "rest_count")
    private Integer restCount;

    /** 休息确认次数 */
    @Schema(description = "休息确认次数")
    @TableField(value = "rest_confirmed")
    private Integer restConfirmed;

    /** 蓝光过滤状态 0关 1开 */
    @Schema(description = "蓝光过滤状态 0关 1开")
    @TableField(value = "blue_light_on")
    private Integer blueLightOn;

    /** 用眼日期（归属日） */
    @Schema(description = "用眼日期")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @TableField(value = "usage_date")
    private Date usageDate;

    /** 删除标记 0正常 1删除 */
    @Schema(description = "删除标记 0正常 1删除")
    @TableLogic
    @TableField(value = "del_flag")
    private Integer delFlag;

    /**
     * 服务端最近一次心跳时间（{@code lastHeartbeat} 基线）。
     *
     * <p>会话刚开始、还没收到过心跳时退化为会话开始时间。</p>
     *
     * @return 最近一次心跳时间
     */
    public Date lastHeartbeatAt() {
        return getUpdateTime() != null ? getUpdateTime() : getStartAt();
    }
}
