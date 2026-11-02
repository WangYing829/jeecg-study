package org.jeecg.modules.study.eye.constant;

/**
 * 护眼域的固定策略常量（R-03 护眼硬指标）。
 *
 * <p><b>时长口径</b>：用眼时长一律由<b>服务端</b>按心跳累计 —— 服务端以
 * {@code lastHeartbeat}（最近一次心跳时间）为基线推进，客户端<b>只上报"我还在"，
 * 不上报时长</b>，因此客户端无法伪造用眼时长。</p>
 *
 * <p><b>心跳节拍</b>：客户端约定每 30 秒上报一次心跳，服务端按
 * 「有效心跳次数 × 名义间隔」累计分钟数；单次心跳间隔超过
 * {@link #MAX_HEARTBEAT_GAP_SECONDS} 说明页面已被切走（切后台 / 锁屏），
 * 该段时长不计入。</p>
 *
 * @author jeecg-study
 */
public final class EyeRestPolicy {

    /** 心跳名义间隔（秒）：客户端约定每 30 秒上报一次 */
    public static final int NOMINAL_HEARTBEAT_SECONDS = 30;

    /**
     * 单次心跳可计入的最大间隔（秒）。
     *
     * <p>超过该间隔说明客户端已离开页面，这一段属于离开时长，<b>不计入</b>有效用眼时长。</p>
     */
    public static final int MAX_HEARTBEAT_GAP_SECONDS = 90;

    /**
     * 休息提醒文案。
     *
     * <p>必须给出<b>可执行的具体动作</b>，不能只写「休息一下」（TC-07-13）。</p>
     */
    public static final String REST_PROMPT = "该让眼睛歇一歇了：请看向 6 米外保持 20 秒，再回来继续做题。";

    /** 护眼提醒间隔取值下限（分钟） */
    public static final int MIN_INTERVAL_MINUTES = 1;

    /** 护眼提醒间隔取值上限（分钟） */
    public static final int MAX_INTERVAL_MINUTES = 240;

    private EyeRestPolicy() {
    }
}
