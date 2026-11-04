package org.jeecg.modules.study.parent.controller;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.study.parent.service.StudyNotificationPreferenceService;
import org.jeecg.modules.study.parent.vo.NotificationPreferenceRequest;
import org.jeecg.modules.study.parent.vo.NotificationPreferenceVO;

/**
 * 家长端提醒推送偏好（F-PAR-04）。
 *
 * <p><b>家长端资源域里唯一的写入口</b>：《接口设计说明书》§8.1 明确
 * 「家长端报告接口全部为 GET；家长端资源域唯一的 POST 是提醒偏好」。</p>
 *
 * <p><b>数据范围</b>：偏好属于家长账号（取自会话）；
 * 指定孩子时必须与关系表求交集，越权一律 4010。</p>
 *
 * @author jeecg-study
 */
@Tag(name = "jeecg-study · 家长端提醒偏好")
@RestController
@RequestMapping("/api/v1/study/parent")
@RequiredArgsConstructor
public class StudyParentPreferenceController {

    private final StudyNotificationPreferenceService preferenceService;

    @Operation(summary = "查提醒推送偏好（复习提醒 / 护眼提醒的推送时间与渠道）")
    @RequiresPermissions("study:parent:pref")
    @GetMapping("/notification-preference")
    public Result<NotificationPreferenceVO> preference(
            @RequestParam(value = "childCode", required = false) String childCode) {
        return Result.OK(preferenceService.preference(childCode));
    }

    @Operation(summary = "更新提醒推送偏好（不传字段 = 不修改）")
    @RequiresPermissions("study:parent:pref")
    @PutMapping("/notification-preference")
    public Result<NotificationPreferenceVO> update(@RequestBody NotificationPreferenceRequest request) {
        return Result.OK(preferenceService.update(request));
    }
}
