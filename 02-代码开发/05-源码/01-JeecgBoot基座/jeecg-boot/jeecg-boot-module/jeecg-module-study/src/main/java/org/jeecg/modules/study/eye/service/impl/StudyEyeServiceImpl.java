package org.jeecg.modules.study.eye.service.impl;

import java.util.Date;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;

import lombok.RequiredArgsConstructor;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.study.common.config.StudyProperties;
import org.jeecg.modules.study.common.exception.StudyForbiddenException;
import org.jeecg.modules.study.eye.constant.EyeRestPolicy;
import org.jeecg.modules.study.eye.entity.StudyEyeUsage;
import org.jeecg.modules.study.eye.mapper.StudyEyeUsageMapper;
import org.jeecg.modules.study.eye.service.StudyEyeService;
import org.jeecg.modules.study.eye.vo.EyeHeartbeatRequest;
import org.jeecg.modules.study.eye.vo.EyeSessionVO;
import org.jeecg.modules.study.scope.service.ChildScopeService;

/**
 * 护眼会话服务实现。
 *
 * <p><b>时长怎么算</b>：客户端每 30 秒上报一次心跳，服务端只累计
 * <b>有效心跳次数</b>，有效时长 = 有效心跳次数 × 名义间隔。
 * 「有效」的定义是：本次心跳标记为有效，且距 lastHeartbeat 的间隔在
 * {@code [1 秒, 90 秒]} 内 —— 间隔过大说明页面已被切走（切后台 / 锁屏），
 * 这段离开时长不计入。</p>
 *
 * <p><b>休息提醒怎么触发</b>：累计分钟数到达提醒间隔（上下文里配的
 * {@code eye_interval_min}，没配就用 {@code StudyProperties.getEyeIntervalMin()}）
 * 时触发一次提醒，{@code rest_count} 加一；提醒只能由显式的「休息确认」清除，
 * 因此 {@code rest_count > rest_confirmed} 即表示遮罩仍在。
 * 本类<b>没有</b>任何定时任务，遮罩不会自动消失。</p>
 *
 * @author jeecg-study
 */
@Service
@RequiredArgsConstructor
public class StudyEyeServiceImpl implements StudyEyeService {

    /** 心跳写入的最大重试次数（CAS 冲突时重读重试） */
    private static final int MAX_HEARTBEAT_RETRY = 3;

    private final StudyEyeUsageMapper eyeMapper;
    private final ChildScopeService scopeService;
    private final StudyProperties studyProperties;

    @Override
    public EyeSessionVO startSession() {
        String childId = requireOwnChildId();
        Date now = new Date();

        StudyEyeUsage usage = new StudyEyeUsage();
        usage.setId(IdWorker.get32UUID());
        usage.setChildId(childId);
        usage.setContextId(currentContextId(childId));
        usage.setSessionId(IdWorker.get32UUID());
        usage.setStartAt(now);
        usage.setEndAt(null);
        usage.setActiveMinutes(0);
        usage.setHeartbeatCount(0);
        usage.setRestCount(0);
        usage.setRestConfirmed(0);
        // 蓝光过滤沿用上一次的设置 —— 刷新页面后状态保持
        Integer latestBlueLight = eyeMapper.selectLatestBlueLightOn(childId);
        usage.setBlueLightOn(latestBlueLight == null ? 0 : latestBlueLight);
        usage.setDelFlag(0);
        usage.setCreateBy(scopeService.currentUserId());
        usage.setCreateTime(now);
        usage.setUpdateBy(scopeService.currentUserId());
        // 会话刚开始，lastHeartbeat 基线 = 会话开始时间
        usage.setUpdateTime(now);
        // 归属日由数据库的 CURDATE() 落库
        eyeMapper.insertSession(usage);
        return toVO(usage);
    }

    @Override
    public EyeSessionVO heartbeat(String sessionId, EyeHeartbeatRequest request) {
        StudyEyeUsage usage = requireSession(sessionId);
        Date now = new Date();
        boolean active = request == null || request.getActive() == null || Boolean.TRUE.equals(request.getActive());
        Boolean blueLight = request == null ? null : request.getBlueLight();

        for (int attempt = 0; attempt < MAX_HEARTBEAT_RETRY; attempt++) {
            Date lastHeartbeat = usage.lastHeartbeatAt();
            long gapSeconds = (now.getTime() - lastHeartbeat.getTime()) / 1000L;
            // 页面被切走（间隔过大）的那一段不计入有效用眼时长
            boolean counted = active && gapSeconds > 0 && gapSeconds <= EyeRestPolicy.MAX_HEARTBEAT_GAP_SECONDS;

            int heartbeatCount = value(usage.getHeartbeatCount()) + (counted ? 1 : 0);
            int activeMinutes = heartbeatCount * EyeRestPolicy.NOMINAL_HEARTBEAT_SECONDS / 60;
            int restCount = value(usage.getRestCount());
            int restConfirmed = value(usage.getRestConfirmed());
            int intervalMin = resolveIntervalMin(usage.getChildId());

            // 到达阈值触发一次提醒；已有提醒未被确认时不再重复计数
            if (restCount == restConfirmed && activeMinutes >= (restCount + 1) * intervalMin) {
                restCount = restCount + 1;
            }
            int blueLightOn = blueLight == null
                    ? value(usage.getBlueLightOn())
                    : (Boolean.TRUE.equals(blueLight) ? 1 : 0);

            int updated = eyeMapper.updateHeartbeat(usage.getId(), value(usage.getHeartbeatCount()),
                    heartbeatCount, activeMinutes, restCount, restConfirmed, blueLightOn, now);
            if (updated > 0) {
                usage.setHeartbeatCount(heartbeatCount);
                usage.setActiveMinutes(activeMinutes);
                usage.setRestCount(restCount);
                usage.setRestConfirmed(restConfirmed);
                usage.setBlueLightOn(blueLightOn);
                usage.setUpdateTime(now);
                return toVO(usage);
            }
            // 并发写冲突：重读后重试，绝不把一次心跳算两遍
            StudyEyeUsage latest = eyeMapper.selectBySessionId(sessionId.trim());
            if (latest == null) {
                throw new JeecgBootException("护眼会话不存在", 404);
            }
            usage = latest;
        }
        throw new JeecgBootException("护眼会话写入冲突，请稍后重试", 500);
    }

    @Override
    public EyeSessionVO confirmRest(String sessionId) {
        StudyEyeUsage usage = requireSession(sessionId);
        int restCount = value(usage.getRestCount());
        int restConfirmed = value(usage.getRestConfirmed());
        // 只有「已触发但未确认」的提醒才需要确认，避免确认次数被刷高
        if (restCount > restConfirmed) {
            restConfirmed = restConfirmed + 1;
            eyeMapper.updateRestConfirmed(usage.getId(), restConfirmed);
            usage.setRestConfirmed(restConfirmed);
        }
        return toVO(usage);
    }

    @Override
    public EyeSessionVO endSession(String sessionId) {
        StudyEyeUsage usage = requireSession(sessionId);
        Date now = new Date();
        eyeMapper.closeSession(usage.getId(), now, now);
        usage.setEndAt(now);
        usage.setUpdateTime(now);
        return toVO(usage);
    }

    @Override
    public EyeSessionVO sessionState(String sessionId) {
        return toVO(requireSession(sessionId));
    }

    // ------------------------------------------------------------------ 内部

    /**
     * 取会话并校验访问权限：越权一律 4010（不用 404，避免资源探测）。
     */
    private StudyEyeUsage requireSession(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new JeecgBootException("护眼会话标识不能为空", 400);
        }
        StudyEyeUsage usage = eyeMapper.selectBySessionId(sessionId.trim());
        if (usage == null) {
            throw new JeecgBootException("护眼会话不存在", 404);
        }
        scopeService.assertChildAccess(usage.getChildId());
        return usage;
    }

    /**
     * 当前身份对应的孩子 ID；不是孩子身份时按越权处理。
     */
    private String requireOwnChildId() {
        String childId = scopeService.ownChildId();
        if (childId == null) {
            throw new StudyForbiddenException("当前身份没有孩子档案，无法开始用眼会话");
        }
        return childId;
    }

    /**
     * 当前上下文 ID；未配置上下文时返回空串（上下文未配置不是错误）。
     */
    private String currentContextId(String childId) {
        String contextId = eyeMapper.selectCurrentContextId(childId);
        return contextId == null ? "" : contextId;
    }

    /**
     * 护眼提醒间隔：优先取上下文里配的 {@code eye_interval_min}，
     * 没配则用全局配置 {@code StudyProperties.getEyeIntervalMin()}。
     */
    private int resolveIntervalMin(String childId) {
        Integer configured = eyeMapper.selectCurrentEyeIntervalMin(childId);
        if (configured != null && configured > 0) {
            return configured;
        }
        int fallback = studyProperties.getEyeIntervalMin();
        return fallback > 0 ? fallback : EyeRestPolicy.MIN_INTERVAL_MINUTES;
    }

    private EyeSessionVO toVO(StudyEyeUsage usage) {
        EyeSessionVO vo = new EyeSessionVO();
        vo.setSessionId(usage.getSessionId());
        // 提醒已触发但还没确认 → 遮罩仍在（服务端不会自动清除）
        vo.setRestRequired(value(usage.getRestCount()) > value(usage.getRestConfirmed()));
        vo.setRestPrompt(EyeRestPolicy.REST_PROMPT);
        vo.setBlueLightOn(value(usage.getBlueLightOn()) == 1);
        // 提醒间隔随会话下发：孩子端护眼设置页要显示「多久提醒一次」，
        // 而孩子身份读不了家长端的上下文接口（study:parent:context:query）。
        vo.setEyeIntervalMin(resolveIntervalMin(usage.getChildId()));
        vo.setLastHeartbeatAt(usage.lastHeartbeatAt());
        return vo;
    }

    private int value(Integer number) {
        return number == null ? 0 : number;
    }
}
