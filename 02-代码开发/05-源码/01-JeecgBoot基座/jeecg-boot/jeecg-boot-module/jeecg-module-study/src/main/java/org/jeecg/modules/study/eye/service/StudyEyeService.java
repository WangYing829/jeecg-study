package org.jeecg.modules.study.eye.service;

import org.jeecg.modules.study.eye.vo.EyeHeartbeatRequest;
import org.jeecg.modules.study.eye.vo.EyeSessionVO;

/**
 * 护眼会话服务（R-03 护眼硬指标）。
 *
 * <p><b>四条硬约束</b>：</p>
 * <ol>
 *   <li><b>服务端累计</b>：用眼时长由服务端按心跳次数累计，客户端只上报
 *       「是否还在」与蓝光开关，<b>不采信任何客户端上报的时长</b>；</li>
 *   <li><b>休息阻断</b>：到达阈值后服务端下发 {@code restRequired=true}，
 *       前端据此显示阻断遮罩；</li>
 *   <li><b>不自动消失</b>：遮罩只能由显式的「休息确认」接口清除，
 *       本服务<b>没有</b>任何定时任务会自动清除它；</li>
 *   <li><b>不显示时长</b>：孩子端响应里没有用眼时长数字，只有状态。</li>
 * </ol>
 *
 * @author jeecg-study
 */
public interface StudyEyeService {

    /**
     * 开始一次用眼会话（当前孩子身份）。
     *
     * <p>蓝光过滤开关沿用该孩子上一次的设置，因此刷新页面后状态保持。</p>
     *
     * @return 会话状态
     */
    EyeSessionVO startSession();

    /**
     * 上报一次心跳。
     *
     * <p>服务端以 lastHeartbeat 为基线推进并累计；心跳间隔超过
     * {@code EyeRestPolicy.MAX_HEARTBEAT_GAP_SECONDS}（页面被切走）时该段不计入。</p>
     *
     * @param sessionId 会话 ID
     * @param request   心跳入参
     * @return 会话状态
     */
    EyeSessionVO heartbeat(String sessionId, EyeHeartbeatRequest request);

    /**
     * 确认休息 —— <b>清除阻断遮罩的唯一入口</b>。
     *
     * @param sessionId 会话 ID
     * @return 会话状态
     */
    EyeSessionVO confirmRest(String sessionId);

    /**
     * 结束会话。
     *
     * @param sessionId 会话 ID
     * @return 会话状态
     */
    EyeSessionVO endSession(String sessionId);

    /**
     * 查询会话状态（刷新页面后据此恢复蓝光开关等状态）。
     *
     * @param sessionId 会话 ID
     * @return 会话状态
     */
    EyeSessionVO sessionState(String sessionId);
}
