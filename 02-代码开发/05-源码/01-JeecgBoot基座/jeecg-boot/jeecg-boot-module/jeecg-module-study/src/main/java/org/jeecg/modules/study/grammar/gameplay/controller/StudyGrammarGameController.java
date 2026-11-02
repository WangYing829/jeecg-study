package org.jeecg.modules.study.grammar.gameplay.controller;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.study.grammar.gameplay.service.GrammarGameService;
import org.jeecg.modules.study.grammar.gameplay.vo.GrammarDetectiveJudgeRequest;
import org.jeecg.modules.study.grammar.gameplay.vo.GrammarDetectiveJudgeVO;
import org.jeecg.modules.study.grammar.gameplay.vo.GrammarDetectiveRoundVO;
import org.jeecg.modules.study.grammar.gameplay.vo.GrammarGameCollectionResultVO;
import org.jeecg.modules.study.grammar.gameplay.vo.GrammarGameProgressVO;
import org.jeecg.modules.study.grammar.gameplay.vo.GrammarTransformJudgeRequest;
import org.jeecg.modules.study.grammar.gameplay.vo.GrammarTransformJudgeVO;
import org.jeecg.modules.study.grammar.gameplay.vo.GrammarTransformRoundVO;

/**
 * 英语语法 · 趣味玩法（F-GRM-04）。
 *
 * <p>三个玩法：句子侦探、语法变形闯关、我的错句收藏馆。</p>
 *
 * <p><b>身份来自会话</b>，不接受前端传来的 childId；
 * 判定全部由确定性规则给出，取题接口不下发答案（R-01 / R-02）。</p>
 *
 * @author jeecg-study
 */
// update-begin---author:mavis ---date:2026-10-31  for：【F-GRM-04】本文件 6 个端点的权限码由 study:grammar:game:play 并入语法域既有口径 study:kid:grammar:practice —— 同一域不出现两套前缀，也不必新增权限行与角色授权-----------
@Tag(name = "jeecg-study · 英语语法 · 趣味玩法")
@RestController
@RequestMapping("/api/v1/study/grammar/game")
@RequiredArgsConstructor
public class StudyGrammarGameController {

    private final GrammarGameService gameService;

    @Operation(summary = "句子侦探取题（四句话，只给句子不给答案）")
    @RequiresPermissions("study:kid:grammar:practice")
    @GetMapping("/detective/next")
    public Result<GrammarDetectiveRoundVO> detectiveNext() {
        return Result.OK(gameService.detectiveNext());
    }

    @Operation(summary = "句子侦探判定（孩子指出错的是第几句）")
    @RequiresPermissions("study:kid:grammar:practice")
    @PostMapping("/detective/judge")
    public Result<GrammarDetectiveJudgeVO> detectiveJudge(@RequestBody GrammarDetectiveJudgeRequest request) {
        return Result.OK(gameService.detectiveJudge(request));
    }

    @Operation(summary = "语法变形闯关取题（只给中文意思）")
    @RequiresPermissions("study:kid:grammar:practice")
    @GetMapping("/transform/next")
    public Result<GrammarTransformRoundVO> transformNext() {
        return Result.OK(gameService.transformNext());
    }

    @Operation(summary = "语法变形闯关判定（孩子写的句子对不对）")
    @RequiresPermissions("study:kid:grammar:practice")
    @PostMapping("/transform/judge")
    public Result<GrammarTransformJudgeVO> transformJudge(@RequestBody GrammarTransformJudgeRequest request) {
        return Result.OK(gameService.transformJudge(request));
    }

    @Operation(summary = "我的错句收藏馆（统一错题池里自己的错句）")
    @RequiresPermissions("study:kid:grammar:practice")
    @GetMapping("/collection")
    public Result<GrammarGameCollectionResultVO> collection(
            @RequestParam(value = "errorCause", required = false) String errorCause) {
        return Result.OK(gameService.collection(errorCause));
    }

    @Operation(summary = "闯关进度（只跟自己比）")
    @RequiresPermissions("study:kid:grammar:practice")
    @GetMapping("/progress")
    public Result<GrammarGameProgressVO> progress() {
        return Result.OK(gameService.progress());
    }

}
// update-end---author:mavis ---date:2026-10-31  for：【F-GRM-04】本文件 6 个端点的权限码由 study:grammar:game:play 并入语法域既有口径 study:kid:grammar:practice —— 同一域不出现两套前缀，也不必新增权限行与角色授权-----------
