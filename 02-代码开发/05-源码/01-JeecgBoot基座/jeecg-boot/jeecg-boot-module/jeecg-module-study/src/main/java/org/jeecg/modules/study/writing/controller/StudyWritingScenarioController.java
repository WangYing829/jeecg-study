package org.jeecg.modules.study.writing.controller;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.study.writing.service.StudyWritingScenarioService;
import org.jeecg.modules.study.writing.vo.WritingScenarioListVO;
import org.jeecg.modules.study.writing.vo.WritingScenarioThemeListVO;
import org.jeecg.modules.study.writing.vo.WritingScenarioVO;

/**
 * 语文写作情境库接口（F-WRT-03《素材改情境库》）。
 *
 * <p>三个端点：主题清单 → 情境清单 → 情境详情（训练入口）。</p>
 *
 * <p><b>产品红线</b>：<b>给情境不给金句</b> —— 响应里只有处境、年龄段、
 * 细节线索、提示题与限制条件，<b>没有任何可以直接抄走的句子</b>，
 * 也没有分数、名次类字段（R-04）。</p>
 *
 * <p><b>数据范围</b>：情境库是<b>全局共享的内容表</b>（表里没有 child_id），
 * 因此本控制器<b>不接受也不需要</b>前端传来的 childId；
 * 孩子写的内容落在带 child_id 的 {@code study_writing} 上。</p>
 *
 * <p><b>权限码</b>：读用 {@code study:writing:view}，
 * 取训练入口（带提示题与限制条件）用 {@code study:writing:train}。</p>
 *
 * @author jeecg-study
 */
@Tag(name = "jeecg-study · 语文写作情境库")
@RestController
@RequestMapping("/api/v1/study/writing/scenario")
@RequiredArgsConstructor
public class StudyWritingScenarioController {

    private final StudyWritingScenarioService scenarioService;

    @Operation(summary = "情境主题清单（同一主题下有多个不同处境）")
    @RequiresPermissions("study:writing:view")
    @GetMapping("/themes")
    public Result<WritingScenarioThemeListVO> themes(
            @RequestParam(value = "stageCode", required = false) String stageCode) {
        return Result.OK(scenarioService.listThemes(stageCode));
    }

    @Operation(summary = "情境清单（可按主题 / 学段过滤）")
    @RequiresPermissions("study:writing:view")
    @GetMapping("/list")
    public Result<WritingScenarioListVO> list(
            @RequestParam(value = "themeCode", required = false) String themeCode,
            @RequestParam(value = "stageCode", required = false) String stageCode) {
        return Result.OK(scenarioService.listScenarios(themeCode, stageCode));
    }

    @Operation(summary = "情境详情（训练入口：提示题 + 细节线索 + 限制条件）")
    @RequiresPermissions("study:writing:train")
    @GetMapping("/{code}")
    public Result<WritingScenarioVO> detail(@PathVariable("code") String code) {
        return Result.OK(scenarioService.getScenario(code));
    }
}
