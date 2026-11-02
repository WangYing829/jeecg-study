package org.jeecg.modules.study.context.controller;

import java.util.List;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.study.context.service.StudyTextbookVersionService;
import org.jeecg.modules.study.context.vo.TextbookVersionVO;

/**
 * 教材版本字典接口（F-REG-03）。
 *
 * <p>下拉按出版社去重，7 家出版社齐全（TC-01-07）；指定学段时只返回该学段适用的版本（TC-01-03）。</p>
 *
 * @author jeecg-study
 */
@Tag(name = "jeecg-study · 教材版本")
@RestController
@RequestMapping("/api/v1/study/textbook-versions")
@RequiredArgsConstructor
public class StudyTextbookVersionController {

    private final StudyTextbookVersionService textbookVersionService;

    @Operation(summary = "教材版本列表（7 家出版社齐全，可按学段过滤）")
    @RequiresPermissions("study:parent:textbook:query")
    @GetMapping
    public Result<List<TextbookVersionVO>> list(@RequestParam(value = "stageCode", required = false) String stageCode) {
        return Result.OK(textbookVersionService.list(stageCode));
    }
}
