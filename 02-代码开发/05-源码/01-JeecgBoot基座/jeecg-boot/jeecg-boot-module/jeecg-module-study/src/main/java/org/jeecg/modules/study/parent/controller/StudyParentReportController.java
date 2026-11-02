package org.jeecg.modules.study.parent.controller;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.study.parent.service.StudyParentReportService;
import org.jeecg.modules.study.parent.vo.ParentErrorReportVO;
import org.jeecg.modules.study.parent.vo.ParentOverviewVO;
import org.jeecg.modules.study.parent.vo.ParentWeeklyVO;

/**
 * 家长端只读报告接口（F-PAR-01）。
 *
 * <p><b>只读边界</b>：本控制器的接口<b>全部是 GET</b>，没有任何新增 / 修改 / 删除入口；
 * 家长端唯一的写操作是「一句话反馈」与「提醒偏好」，不落在本控制器里。</p>
 *
 * <p><b>角色边界</b>：每个方法都带 {@code @RequiresRoles("study_parent")} 与
 * {@code @RequiresPermissions("study:parent:report:view")} ——
 * 孩子角色不持有家长端权限码，因此服务端就会拦下（TC-07-08）。</p>
 *
 * <p><b>数据范围</b>：孩子由 {@code childCode} 解析，服务端再与关系表求交集，
 * 越权一律 4010。</p>
 *
 * @author jeecg-study
 */
@Tag(name = "jeecg-study · 家长端报告")
@RestController
@RequestMapping("/api/v1/study/parent/report")
@RequiredArgsConstructor
public class StudyParentReportController {

    private final StudyParentReportService reportService;

    @Operation(summary = "家长端总览（用眼 / 连续天数 / 掌握度 / 错因 / 薄弱考点 / 写作趋势）")
    @RequiresRoles("study_parent")
    @RequiresPermissions("study:parent:report:view")
    @GetMapping("/overview")
    public Result<ParentOverviewVO> overview(@RequestParam(value = "childCode", required = false) String childCode) {
        return Result.OK(reportService.overview(childCode));
    }

    @Operation(summary = "家长端周报（近 7 天逐日）")
    @RequiresRoles("study_parent")
    @RequiresPermissions("study:parent:report:view")
    @GetMapping("/weekly")
    public Result<ParentWeeklyVO> weekly(@RequestParam(value = "childCode", required = false) String childCode) {
        return Result.OK(reportService.weekly(childCode));
    }

    @Operation(summary = "家长端错因分布与薄弱考点")
    @RequiresRoles("study_parent")
    @RequiresPermissions("study:parent:report:view")
    @GetMapping("/errors")
    public Result<ParentErrorReportVO> errors(@RequestParam(value = "childCode", required = false) String childCode) {
        return Result.OK(reportService.errors(childCode));
    }
}
