package org.jeecg.modules.study.grammar.controller;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.study.grammar.service.StudyGrammarContextService;
import org.jeecg.modules.study.grammar.vo.GrammarContextResultVO;

/**
 * 英语语法 · 例句语境库（F-GRM-05）。
 *
 * <p>语境库与背单词域<b>共用同一批例句</b>：同一张关联表 {@code study_word_context}，
 * 一次建库，背单词与语法两处受益。</p>
 *
 * @author jeecg-study
 */
@Tag(name = "jeecg-study · 英语语法 · 语境库")
@RestController
@RequestMapping("/api/v1/study/grammar")
@RequiredArgsConstructor
public class StudyGrammarContextController {

    private final StudyGrammarContextService contextService;

    @Operation(summary = "语境例句库（与背单词共用同一批例句：课文 / 真题 / 原声素材）")
    // update-begin---author:mavis ---date:2026-10-31  for：【F-GRM-05】权限码并入语法域既有口径 study:kid:grammar:view，避免同一域出现两套前缀、也不必新增权限行与角色授权-----------
    @RequiresPermissions("study:kid:grammar:view")
    // update-end---author:mavis ---date:2026-10-31  for：【F-GRM-05】权限码并入语法域既有口径 study:kid:grammar:view，避免同一域出现两套前缀、也不必新增权限行与角色授权-----------
    @GetMapping("/context")
    public Result<GrammarContextResultVO> context(
            @RequestParam(value = "wordCode", required = false) String wordCode,
            @RequestParam(value = "grammarCode", required = false) String grammarCode,
            @RequestParam(value = "contextType", required = false) String contextType,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return Result.OK(contextService.context(wordCode, grammarCode, contextType, limit));
    }

}
