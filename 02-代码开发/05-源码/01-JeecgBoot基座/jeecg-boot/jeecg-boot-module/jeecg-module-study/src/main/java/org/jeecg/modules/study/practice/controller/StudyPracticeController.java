package org.jeecg.modules.study.practice.controller;

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
import org.jeecg.modules.study.practice.service.StudyPracticeService;
import org.jeecg.modules.study.practice.vo.AnswerCommand;
import org.jeecg.modules.study.practice.vo.AnswerResultVO;
import org.jeecg.modules.study.practice.vo.ExplanationVO;
import org.jeecg.modules.study.practice.vo.PracticeQuestionVO;
import org.jeecg.modules.study.practice.vo.PracticeSessionVO;
import org.jeecg.modules.study.practice.vo.SessionSummaryVO;

/**
 * 练习与判定（T-V0-09 / T-V0-10）。
 *
 * <p>孩子端接口：身份来自会话，<b>不接受前端传来的 childId</b>。</p>
 *
 * @author jeecg-study
 */
@Tag(name = "jeecg-study · 练习与判定")
@RestController
@RequestMapping("/api/v1/study/practice")
@RequiredArgsConstructor
public class StudyPracticeController {

    private final StudyPracticeService practiceService;

    @Operation(summary = "取下一题（响应不含答案与解析）")
    @RequiresPermissions("study:kid:practice:query")
    @GetMapping("/{type}/next")
    public Result<PracticeQuestionVO> next(@PathVariable("type") String type) {
        return Result.OK(practiceService.nextQuestion(type));
    }

    @Operation(summary = "提交作答（判定 + 错因采集 + 错题入池）")
    @RequiresPermissions("study:kid:practice:add")
    @PostMapping("/answer")
    public Result<AnswerResultVO> answer(@RequestBody AnswerCommand command) {
        return Result.OK(practiceService.submitAnswer(command));
    }

    @Operation(summary = "取解析（必须在作答之后）")
    @RequiresPermissions("study:kid:practice:query")
    @GetMapping("/{contentId}/explanation")
    public Result<ExplanationVO> explanation(@PathVariable("contentId") String contentId,
                                              @RequestParam(value = "sessionId", required = false) String sessionId) {
        return Result.OK(practiceService.explanation(contentId, sessionId));
    }

    @Operation(summary = "生成练习会话")
    @RequiresPermissions("study:kid:practice:query")
    @GetMapping("/{type}/session")
    public Result<PracticeSessionVO> session(@PathVariable("type") String type) {
        return Result.OK(practiceService.createSession(type));
    }

    @Operation(summary = "结束练习会话并汇总")
    @RequiresPermissions("study:kid:practice:add")
    @PostMapping("/session/{id}/finish")
    public Result<SessionSummaryVO> finish(@PathVariable("id") String sessionId) {
        return Result.OK(practiceService.finishSession(sessionId));
    }
}
