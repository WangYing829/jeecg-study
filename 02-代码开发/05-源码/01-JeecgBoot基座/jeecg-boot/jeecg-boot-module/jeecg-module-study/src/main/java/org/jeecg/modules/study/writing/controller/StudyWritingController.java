package org.jeecg.modules.study.writing.controller;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.study.writing.service.StudyWritingService;
import org.jeecg.modules.study.writing.vo.WritingDissectionListVO;
import org.jeecg.modules.study.writing.vo.WritingDissectionVO;
import org.jeecg.modules.study.writing.vo.WritingDraftRequest;
import org.jeecg.modules.study.writing.vo.WritingDraftResultVO;
import org.jeecg.modules.study.writing.vo.WritingOutlineRequest;
import org.jeecg.modules.study.writing.vo.WritingOutlineResultVO;

/**
 * 语文写作（V4）孩子端接口。
 *
 * <p>对应《接口设计说明书》§3.4 写作的 4 个接口：
 * 拆解库列表、拆解详情、提交提纲、提交初稿。</p>
 *
 * <p><b>数据范围</b>：身份来自会话，<b>不接受前端传来的 childId</b>；
 * 家长等非孩子身份一律 4010。</p>
 *
 * <p><b>权限码</b>用 {@code study:writing:*}（§13 权限码设计），
 * 与 {@code study:kid:*} 同属孩子侧，不与家长端权限码混用。</p>
 *
 * @author jeecg-study
 */
@Tag(name = "jeecg-study · 语文写作")
@RestController
@RequestMapping("/api/v1/study/writing")
@RequiredArgsConstructor
public class StudyWritingController {

    private final StudyWritingService writingService;

    @Operation(summary = "佳作拆解库列表（六层）")
    @RequiresPermissions("study:writing:view")
    @GetMapping("/dissections")
    public Result<WritingDissectionListVO> dissections(
            @RequestParam(value = "genre", required = false) String genre,
            @RequestParam(value = "stageCode", required = false) String stageCode) {
        return Result.OK(writingService.listDissections(genre, stageCode));
    }

    @Operation(summary = "佳作拆解详情（六层，按题目钩子到结尾回扣的顺序）")
    @RequiresPermissions("study:writing:view")
    @GetMapping("/dissections/{code}")
    public Result<WritingDissectionVO> dissection(@PathVariable("code") String code) {
        return Result.OK(writingService.getDissection(code));
    }

    @Operation(summary = "提交提纲（进入下一步的前置）")
    @RequiresPermissions("study:writing:train")
    @PostMapping("/train/outline")
    public Result<WritingOutlineResultVO> outline(@RequestBody WritingOutlineRequest request) {
        return Result.OK(writingService.submitOutline(request));
    }

    @Operation(summary = "提交初稿（要求提纲已通过，未通过返回 4002）")
    @RequiresPermissions("study:writing:train")
    @PostMapping("/train/draft")
    public Result<WritingDraftResultVO> draft(@RequestBody WritingDraftRequest request) {
        return Result.OK(writingService.submitDraft(request));
    }
}
