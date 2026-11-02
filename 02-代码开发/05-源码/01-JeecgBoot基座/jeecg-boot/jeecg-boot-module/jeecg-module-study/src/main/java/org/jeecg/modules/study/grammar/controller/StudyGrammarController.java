package org.jeecg.modules.study.grammar.controller;

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
import org.jeecg.modules.study.grammar.service.StudyGrammarService;
import org.jeecg.modules.study.grammar.vo.GrammarCollectionResultVO;
import org.jeecg.modules.study.grammar.vo.GrammarDetectRequest;
import org.jeecg.modules.study.grammar.vo.GrammarDetectResultVO;
import org.jeecg.modules.study.grammar.vo.GrammarErrorDetailRequest;
import org.jeecg.modules.study.grammar.vo.GrammarErrorFeedbackVO;
import org.jeecg.modules.study.grammar.vo.GrammarPointListVO;
import org.jeecg.modules.study.grammar.vo.GrammarPointVO;
import org.jeecg.modules.study.grammar.vo.GrammarTransformRequest;
import org.jeecg.modules.study.grammar.vo.GrammarTransformResultVO;

/**
 * 英语语法（V3）。
 *
 * <p>孩子端接口：身份来自会话，<b>不接受前端传来的 childId</b>。</p>
 *
 * @author jeecg-study
 */
@Tag(name = "jeecg-study · 英语语法")
@RestController
@RequestMapping("/api/v1/study/grammar")
@RequiredArgsConstructor
public class StudyGrammarController {

    private final StudyGrammarService grammarService;

    @Operation(summary = "语法点列表（每个语法点都标注对应中考题型）")
    @RequiresPermissions("study:kid:grammar:view")
    @GetMapping("/points")
    public Result<GrammarPointListVO> listPoints(@RequestParam(value = "examType", required = false) String examType) {
        return Result.OK(grammarService.listPoints(examType));
    }

    @Operation(summary = "语法点详情（最小讲解 + 同类例句 + 反例）")
    @RequiresPermissions("study:kid:grammar:view")
    @GetMapping("/points/{code}")
    public Result<GrammarPointVO> getPoint(@PathVariable("code") String code) {
        return Result.OK(grammarService.getPoint(code));
    }

    @Operation(summary = "句子变形器（同一意思的五种表达）")
    @RequiresPermissions("study:kid:grammar:practice")
    @PostMapping("/transform")
    public Result<GrammarTransformResultVO> transform(@RequestBody GrammarTransformRequest request) {
        return Result.OK(grammarService.transform(request));
    }

    @Operation(summary = "错误检测（标出错处，并把错句送进统一错题池）")
    @RequiresPermissions("study:kid:grammar:practice")
    @PostMapping("/detect")
    public Result<GrammarDetectResultVO> detect(@RequestBody GrammarDetectRequest request) {
        return Result.OK(grammarService.detect(request));
    }

    @Operation(summary = "点开某个错误：最小讲解 + 同类例句 + 反例")
    @RequiresPermissions("study:kid:grammar:practice")
    @PostMapping("/error-detail")
    public Result<GrammarErrorFeedbackVO> errorDetail(@RequestBody GrammarErrorDetailRequest request) {
        return Result.OK(grammarService.errorDetail(request));
    }

    @Operation(summary = "我的错句收藏馆")
    @RequiresPermissions("study:kid:grammar:view")
    @GetMapping("/collection")
    public Result<GrammarCollectionResultVO> collection(@RequestParam(value = "errorCause", required = false) String errorCause) {
        return Result.OK(grammarService.collection(errorCause));
    }
}
