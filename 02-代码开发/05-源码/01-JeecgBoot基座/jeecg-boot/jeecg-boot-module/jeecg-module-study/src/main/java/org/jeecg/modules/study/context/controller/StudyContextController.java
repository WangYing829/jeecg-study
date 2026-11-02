package org.jeecg.modules.study.context.controller;

import java.util.List;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.study.context.service.StudyContextService;
import org.jeecg.modules.study.context.vo.ContextSaveRequest;
import org.jeecg.modules.study.context.vo.ContextVO;
import org.jeecg.modules.study.context.vo.EyeIntervalRequest;

/**
 * 全局上下文接口（F-BASE-01 / F-EYE-04）。
 *
 * <p>上下文按孩子隔离：家长与孩子看到的都是「自己」的当前上下文；
 * 未配置过时 {@code GET /context/current} 返回 {@code configured=false}，**不报错**（TC-01-01）。</p>
 *
 * @author jeecg-study
 */
@Tag(name = "jeecg-study · 全局上下文")
@RestController
@RequestMapping("/api/v1/study/context")
@RequiredArgsConstructor
public class StudyContextController {

    private final StudyContextService contextService;

    @Operation(summary = "取当前上下文（未配置返回 configured=false）")
    @RequiresPermissions("study:parent:context:query")
    @GetMapping("/current")
    public Result<ContextVO> current(@RequestParam(value = "childCode", required = false) String childCode) {
        return Result.OK(contextService.current(childCode));
    }

    @Operation(summary = "创建 / 切换当前上下文")
    @RequiresPermissions("study:parent:context:edit")
    @PostMapping
    public Result<ContextVO> save(@RequestBody ContextSaveRequest request,
                                  @RequestParam(value = "childCode", required = false) String childCode) {
        String target = childCode != null && !childCode.trim().isEmpty()
                ? childCode : request.getChildCode();
        return Result.OK(contextService.save(request, target));
    }

    @Operation(summary = "历史上下文列表")
    @RequiresPermissions("study:parent:context:query")
    @GetMapping("/list")
    public Result<List<ContextVO>> list(@RequestParam(value = "childCode", required = false) String childCode) {
        return Result.OK(contextService.list(childCode));
    }

    @Operation(summary = "配置护眼提醒间隔（分钟）")
    @RequiresPermissions("study:parent:context:edit")
    @PutMapping("/{id}/eye-interval")
    public Result<ContextVO> updateEyeInterval(@PathVariable("id") String id,
                                                @RequestBody EyeIntervalRequest request) {
        return Result.OK(contextService.updateEyeInterval(id, request.getEyeIntervalMin()));
    }
}
