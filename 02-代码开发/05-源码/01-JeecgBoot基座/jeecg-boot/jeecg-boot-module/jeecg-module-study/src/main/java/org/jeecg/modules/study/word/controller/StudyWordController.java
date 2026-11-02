package org.jeecg.modules.study.word.controller;

import java.util.List;

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
import org.jeecg.modules.study.word.service.StudyWordService;
import org.jeecg.modules.study.word.vo.WordDueVO;
import org.jeecg.modules.study.word.vo.WordFeedbackOptionVO;
import org.jeecg.modules.study.word.vo.WordFeedbackRequest;
import org.jeecg.modules.study.word.vo.WordFeedbackVO;
import org.jeecg.modules.study.word.vo.WordKnownTestRequest;
import org.jeecg.modules.study.word.vo.WordKnownTestVO;
import org.jeecg.modules.study.word.vo.WordListVO;
import org.jeecg.modules.study.word.vo.WordPracticeRequest;
import org.jeecg.modules.study.word.vo.WordPracticeResultVO;
import org.jeecg.modules.study.word.vo.WordPracticeVO;
import org.jeecg.modules.study.word.vo.WordStatsVO;
import org.jeecg.modules.study.word.vo.WordUnitCompleteVO;
import org.jeecg.modules.study.word.vo.WordUnitRequest;
import org.jeecg.modules.study.word.vo.WordUnitVO;

/**
 * 背单词接口（F-WRD-01…F-WRD-06 / TC-03-01…TC-03-11）。
 *
 * <p>孩子端 {@code study:kid:word:*}；家长端只读 {@code study:parent:word:view}。</p>
 *
 * <p><b>红线 R-01</b>：取词 / 词表 / 下一词接口的响应<b>不含拼写</b>与答案字段，
 * 正确拼写只在孩子作答之后的判定接口回传。</p>
 *
 * @author jeecg-study
 */
@Tag(name = "jeecg-study · 背单词")
@RestController
@RequestMapping("/api/v1/study/word")
@RequiredArgsConstructor
public class StudyWordController {

    private final StudyWordService wordService;

    // ------------------------------------------------------------------ 孩子端

    @Operation(summary = "反馈档位选项（认识/模糊/忘记/熟知 四档，不是对错）")
    @RequiresPermissions("study:kid:word:view")
    @GetMapping("/feedback/options")
    public Result<List<WordFeedbackOptionVO>> feedbackOptions() {
        return Result.OK(wordService.feedbackOptions());
    }

    @Operation(summary = "课内单元列表")
    @RequiresPermissions("study:kid:word:view")
    @GetMapping("/line/{lineType}/units")
    public Result<List<WordUnitVO>> listUnits(@PathVariable("lineType") String lineType,
                                              @RequestParam(value = "textbookVersionId", required = false) String textbookVersionId) {
        return Result.OK(wordService.listUnits(lineType, textbookVersionId));
    }

    @Operation(summary = "词表分页")
    @RequiresPermissions("study:kid:word:view")
    @GetMapping("/line/{lineType}/list")
    public Result<WordListVO> listWords(@PathVariable("lineType") String lineType,
                                        @RequestParam(value = "textbookVersionId", required = false) String textbookVersionId,
                                        @RequestParam(value = "unitCode", required = false) String unitCode,
                                        @RequestParam(value = "pageNo", required = false) Integer pageNo,
                                        @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return Result.OK(wordService.listWords(lineType, textbookVersionId, unitCode, pageNo, pageSize));
    }

    @Operation(summary = "取下一个要背的词（无图回忆 + 词句篇三层）")
    @RequiresPermissions("study:kid:word:practice")
    @GetMapping("/practice/next")
    public Result<WordPracticeVO> nextWord(@RequestParam(value = "lineType", required = false) String lineType,
                                            @RequestParam(value = "textbookVersionId", required = false) String textbookVersionId,
                                            @RequestParam(value = "unitCode", required = false) String unitCode) {
        return Result.OK(wordService.nextWord(lineType, textbookVersionId, unitCode));
    }

    @Operation(summary = "背单词作答（拼写判定）")
    @RequiresPermissions("study:kid:word:practice")
    @PostMapping("/practice")
    public Result<WordPracticeResultVO> practice(@RequestBody WordPracticeRequest request) {
        return Result.OK(wordService.practice(request));
    }

    @Operation(summary = "四档反馈")
    @RequiresPermissions("study:kid:word:feedback")
    @PostMapping("/feedback")
    public Result<WordFeedbackVO> feedback(@RequestBody WordFeedbackRequest request) {
        return Result.OK(wordService.feedback(request));
    }

    @Operation(summary = "熟词测试（词汇量过滤）")
    @RequiresPermissions("study:kid:word:feedback")
    @PostMapping("/known-test")
    public Result<WordKnownTestVO> knownTest(@RequestBody WordKnownTestRequest request) {
        return Result.OK(wordService.knownTest(request));
    }

    @Operation(summary = "到期复习词")
    @RequiresPermissions("study:kid:word:view")
    @GetMapping("/due")
    public Result<WordDueVO> dueWords(@RequestParam(value = "lineType", required = false) String lineType) {
        return Result.OK(wordService.dueWords(lineType, null));
    }

    @Operation(summary = "背诵统计")
    @RequiresPermissions("study:kid:word:view")
    @GetMapping("/stats")
    public Result<WordStatsVO> stats(@RequestParam(value = "lineType", required = false) String lineType) {
        return Result.OK(wordService.stats(lineType, null));
    }

    @Operation(summary = "进入课内单元（复习欠账未清空时拒绝进入）")
    @RequiresPermissions("study:kid:word:practice")
    @PostMapping("/unit/enter")
    public Result<WordListVO> enterUnit(@RequestBody WordUnitRequest request) {
        return Result.OK(wordService.enterUnit(request));
    }

    @Operation(summary = "完成单元闯关（奖励反馈 + 连续天数）")
    @RequiresPermissions("study:kid:word:practice")
    @PostMapping("/unit/complete")
    public Result<WordUnitCompleteVO> completeUnit(@RequestBody WordUnitRequest request) {
        return Result.OK(wordService.completeUnit(request));
    }

    // ------------------------------------------------------------------ 家长端（只读）

    @Operation(summary = "孩子的课内单元列表")
    @RequiresPermissions("study:parent:word:view")
    @GetMapping("/parent/{childCode}/units")
    public Result<List<WordUnitVO>> parentUnits(@PathVariable("childCode") String childCode,
                                                @RequestParam(value = "textbookVersionId", required = false) String textbookVersionId) {
        return Result.OK(wordService.listUnits("IN", textbookVersionId));
    }

    @Operation(summary = "孩子的词表")
    @RequiresPermissions("study:parent:word:view")
    @GetMapping("/parent/{childCode}/list")
    public Result<WordListVO> parentWords(@PathVariable("childCode") String childCode,
                                           @RequestParam(value = "lineType", required = false) String lineType,
                                           @RequestParam(value = "textbookVersionId", required = false) String textbookVersionId,
                                           @RequestParam(value = "unitCode", required = false) String unitCode,
                                           @RequestParam(value = "pageNo", required = false) Integer pageNo,
                                           @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return Result.OK(wordService.listWords(lineType, textbookVersionId, unitCode, pageNo, pageSize));
    }

    @Operation(summary = "孩子的到期复习词")
    @RequiresPermissions("study:parent:word:view")
    @GetMapping("/parent/{childCode}/due")
    public Result<WordDueVO> parentDue(@PathVariable("childCode") String childCode,
                                        @RequestParam(value = "lineType", required = false) String lineType) {
        return Result.OK(wordService.dueWords(lineType, childCode));
    }

    @Operation(summary = "孩子的背诵统计")
    @RequiresPermissions("study:parent:word:view")
    @GetMapping("/parent/{childCode}/stats")
    public Result<WordStatsVO> parentStats(@PathVariable("childCode") String childCode,
                                            @RequestParam(value = "lineType", required = false) String lineType) {
        return Result.OK(wordService.stats(lineType, childCode));
    }
}
