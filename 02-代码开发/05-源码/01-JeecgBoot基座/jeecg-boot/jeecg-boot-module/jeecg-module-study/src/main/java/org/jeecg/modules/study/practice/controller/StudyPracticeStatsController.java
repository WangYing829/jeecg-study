package org.jeecg.modules.study.practice.controller;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.study.practice.service.StudyPracticeStatsService;
import org.jeecg.modules.study.practice.vo.PracticeRecordPageVO;
import org.jeecg.modules.study.practice.vo.PracticeStreakVO;
import org.jeecg.modules.study.practice.vo.PracticeTodayStatsVO;

/**
 * 作答记录与练习统计（F-PAR-01 / F-FAM-07 / F-WRD-02）。
 *
 * <p><b>家长端只读口径</b>：家长侧传 {@code childCode}，服务端与关系表求交集后
 * 才进 where；孩子侧不传就是自己。越权一律 4010（不用 404，避免资源探测）。</p>
 *
 * <p><b>只给客观计数与趋势</b>（R-04）：响应里没有名次类字段，
 * 也没有答案、解析与讲解字段。</p>
 *
 * @author jeecg-study
 */
@Tag(name = "jeecg-study · 作答记录与练习统计")
@RestController
@RequestMapping("/api/v1/study/practice")
@RequiredArgsConstructor
public class StudyPracticeStatsController {

    private final StudyPracticeStatsService statsService;

    @Operation(summary = "作答记录分页（只给客观状态，不下发作答原文与答案）")
    @RequiresPermissions({"study:parent:report:view", "study:kid:practice:query"})
    @GetMapping("/records")
    public Result<PracticeRecordPageVO> records(
            @RequestParam(value = "childCode", required = false) String childCode,
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "month", required = false) String month) {
        return Result.OK(statsService.records(childCode, pageNo, pageSize, month));
    }

    @Operation(summary = "今日练习统计（题数 / 对错 / 用时）")
    @RequiresPermissions({"study:parent:report:view", "study:kid:practice:query"})
    @GetMapping("/stats/today")
    public Result<PracticeTodayStatsVO> todayStats(
            @RequestParam(value = "childCode", required = false) String childCode) {
        return Result.OK(statsService.todayStats(childCode));
    }

    @Operation(summary = "连续学习天数（只跟自己比）")
    @RequiresPermissions({"study:parent:report:view", "study:kid:practice:query"})
    @GetMapping("/stats/streak")
    public Result<PracticeStreakVO> streak(
            @RequestParam(value = "childCode", required = false) String childCode) {
        return Result.OK(statsService.streak(childCode));
    }
}
