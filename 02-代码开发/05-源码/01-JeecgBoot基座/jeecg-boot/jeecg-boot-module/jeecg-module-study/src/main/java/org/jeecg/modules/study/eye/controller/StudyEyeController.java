package org.jeecg.modules.study.eye.controller;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.study.eye.service.StudyEyeService;
import org.jeecg.modules.study.eye.vo.EyeHeartbeatRequest;
import org.jeecg.modules.study.eye.vo.EyeSessionVO;

/**
 * 护眼心跳接口（独立轻量通道，F-EYE-01 … F-EYE-04）。
 *
 * <p>本通道只服务孩子端（学习端）：用眼时长由服务端按 lastHeartbeat 基线累计，
 * 客户端只上报「是否还在」与蓝光开关；响应里只有状态，<b>没有用眼时长数字</b>。</p>
 *
 * <p>权限码统一挂在方法上（{@code study:kid:eye:usage}）：孩子角色持有、
 * 家长角色不持有，因此家长无法借用该通道。</p>
 *
 * @author jeecg-study
 */
@Tag(name = "jeecg-study · 护眼心跳")
@RestController
@RequestMapping("/api/v1/study/eye")
@RequiredArgsConstructor
public class StudyEyeController {

    private final StudyEyeService eyeService;

    @Operation(summary = "开始一次用眼会话")
    @RequiresPermissions("study:kid:eye:usage")
    @PostMapping("/session/start")
    public Result<EyeSessionVO> start() {
        return Result.OK(eyeService.startSession());
    }

    @Operation(summary = "上报心跳（服务端累计用眼时长）")
    @RequiresPermissions("study:kid:eye:usage")
    @PostMapping("/session/{sessionId}/heartbeat")
    public Result<EyeSessionVO> heartbeat(@PathVariable("sessionId") String sessionId,
                                          @RequestBody(required = false) EyeHeartbeatRequest request) {
        return Result.OK(eyeService.heartbeat(sessionId, request));
    }

    @Operation(summary = "确认休息（清除阻断遮罩的唯一入口）")
    @RequiresPermissions("study:kid:eye:usage")
    @PostMapping("/session/{sessionId}/rest")
    public Result<EyeSessionVO> confirmRest(@PathVariable("sessionId") String sessionId) {
        return Result.OK(eyeService.confirmRest(sessionId));
    }

    @Operation(summary = "结束用眼会话")
    @RequiresPermissions("study:kid:eye:usage")
    @PostMapping("/session/{sessionId}/end")
    public Result<EyeSessionVO> end(@PathVariable("sessionId") String sessionId) {
        return Result.OK(eyeService.endSession(sessionId));
    }

    @Operation(summary = "查询用眼会话状态（刷新后恢复蓝光开关状态）")
    @RequiresPermissions("study:kid:eye:usage")
    @GetMapping("/session/{sessionId}")
    public Result<EyeSessionVO> state(@PathVariable("sessionId") String sessionId) {
        return Result.OK(eyeService.sessionState(sessionId));
    }
}
