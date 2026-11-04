package org.jeecg.modules.study.error.controller;

import java.util.List;
import java.util.Map;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.study.common.exception.StudyForbiddenException;
import org.jeecg.modules.study.error.service.StudyErrorManageService;
import org.jeecg.modules.study.error.service.StudyErrorPoolService;
import org.jeecg.modules.study.error.service.StudyErrorVariantService;
import org.jeecg.modules.study.error.service.StudyReviewBatchService;
import org.jeecg.modules.study.error.vo.ErrorCauseUpdateRequest;
import org.jeecg.modules.study.error.vo.ErrorDetailVO;
import org.jeecg.modules.study.error.vo.ErrorHotStatisticVO;
import org.jeecg.modules.study.error.vo.ErrorPoolItemVO;
import org.jeecg.modules.study.error.vo.ErrorPoolReportVO;
import org.jeecg.modules.study.error.vo.ErrorVariantGenerateVO;
import org.jeecg.modules.study.error.vo.ErrorVariantListVO;
import org.jeecg.modules.study.error.vo.ReviewBatchCompleteCommand;
import org.jeecg.modules.study.error.vo.ReviewBatchCompleteVO;
import org.jeecg.modules.study.error.vo.ReviewCompleteCommand;
import org.jeecg.modules.study.error.vo.ReviewCompleteVO;
import org.jeecg.modules.study.error.vo.ReviewQueueItemVO;
import org.jeecg.modules.study.error.vo.StudyErrorReportCommand;
import org.jeecg.modules.study.scope.service.ChildScopeService;

/**
 * 统一错题池 + 复习排期（T-V0-06 / T-V0-07）。
 *
 * <p><b>全站只有这一套错题接口</b>：各学习模块不提供独立错题列表（TC-02-12），
 * 统一走 {@code /error-pool} 与 {@code /review}。</p>
 *
 * <p><b>孩子端可读自己的错题</b>：{@code GET /error-pool} 与 {@code /error-pool/statistics}
 * 的权限码补 {@code study:kid:error:query}，且 {@code childCode} 改为可选 ——
 * 孩子不传时由服务端按会话取自己（契约 §3.2），不需要前端先查 childCode 再回传。</p>
 *
 * @author jeecg-study
 */
// update-begin---author:mavis ---date:2026-11-04  for：【第二档-6 上线学习端错题线】错题列表/错因统计放开 study:kid:error:query 且 childCode 改可选；childCode→childId 的解析统一交给 scope 服务（原先本类私有实现不做关系表校验）-----------
@Tag(name = "jeecg-study · 统一错题池与复习排期")
@RestController
@RequestMapping("/api/v1/study")
@RequiredArgsConstructor
public class StudyErrorPoolController {

    private final StudyErrorPoolService errorPoolService;
    private final StudyErrorManageService errorManageService;
    private final StudyErrorVariantService variantService;
    private final StudyReviewBatchService reviewBatchService;
    private final ChildScopeService scopeService;

    @Operation(summary = "错题入池（拍照录入 / 各模块判错后调用）")
    @RequiresPermissions({"study:parent:error:add", "study:kid:error:add"})
    @PostMapping("/error-pool")
    public Result<ErrorPoolReportVO> report(@RequestBody StudyErrorReportCommand command) {
        return Result.OK(errorManageService.reportToPool(command));
    }

    @Operation(summary = "错题列表（统一错题本）")
    @RequiresPermissions({"study:parent:error:query", "study:kid:error:query"})
    @GetMapping("/error-pool")
    public Result<List<ErrorPoolItemVO>> list(@RequestParam(value = "childCode", required = false) String childCode) {
        return Result.OK(errorPoolService.listByChild(resolveChildId(childCode)));
    }

    @Operation(summary = "错因分布统计")
    @RequiresPermissions({"study:parent:error:query", "study:kid:error:query"})
    @GetMapping("/error-pool/statistics")
    public Result<Map<String, Integer>> statistics(@RequestParam(value = "childCode", required = false) String childCode) {
        return Result.OK(errorPoolService.errorCauseStatistics(resolveChildId(childCode)));
    }

    @Operation(summary = "错题详情（不下发答案与解析）")
    @RequiresPermissions({"study:parent:error:query", "study:kid:error:query"})
    @GetMapping("/error-pool/{code}")
    public Result<ErrorDetailVO> detail(@PathVariable("code") String code) {
        return Result.OK(errorManageService.detail(code));
    }

    @Operation(summary = "高频错题（按同一道题的出错次数从多到少，只给客观计数）")
    @RequiresPermissions({"study:parent:error:query", "study:kid:error:query"})
    @GetMapping("/error-pool/statistics/hot")
    public Result<List<ErrorHotStatisticVO>> hotStatistics(
            @RequestParam(value = "childCode", required = false) String childCode,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return Result.OK(errorManageService.hotStatistics(resolveChildId(childCode),
                limit == null ? 0 : limit));
    }

    @Operation(summary = "事后修改错因")
    @RequiresPermissions("study:parent:error:edit")
    @PutMapping("/error-pool/{code}/cause")
    public Result<String> updateCause(@PathVariable("code") String code,
                                       @RequestBody ErrorCauseUpdateRequest request) {
        errorManageService.updateErrorCause(code, request == null ? null : request.getErrorCause());
        return Result.OK("修改成功");
    }

    @Operation(summary = "错题变式清单")
    @RequiresPermissions({"study:parent:error:query", "study:kid:error:query"})
    @GetMapping("/error-pool/{code}/variants")
    public Result<ErrorVariantListVO> variants(@PathVariable("code") String code) {
        return Result.OK(errorManageService.listVariants(code));
    }

    @Operation(summary = "为错题生成变式（由母题机制生成，幂等）")
    @RequiresPermissions({"study:parent:error:add", "study:kid:error:add"})
    @PostMapping("/error-pool/{code}/variants")
    public Result<ErrorVariantGenerateVO> generateVariants(@PathVariable("code") String code) {
        return Result.OK(variantService.generateVariants(code));
    }

    @Operation(summary = "今日复习队列（跨模块统一）")
    @RequiresPermissions("study:kid:review:query")
    @GetMapping("/review/queue")
    public Result<List<ReviewQueueItemVO>> todayQueue() {
        return Result.OK(errorPoolService.todayQueue(currentChildId()));
    }

    @Operation(summary = "未来 7 天排期预览")
    @RequiresPermissions({"study:kid:review:query", "study:parent:error:query"})
    @GetMapping("/review/upcoming")
    public Result<List<ReviewQueueItemVO>> upcoming(@RequestParam(value = "childCode", required = false) String childCode) {
        String childId = childCode == null || childCode.trim().isEmpty()
                ? currentChildId() : resolveChildId(childCode);
        return Result.OK(errorManageService.upcoming(childId, 7));
    }

    @Operation(summary = "完成一项复习（推进排期）")
    @RequiresPermissions("study:kid:review:add")
    @PostMapping("/review/queue/{code}/complete")
    public Result<ReviewCompleteVO> complete(@PathVariable("code") String code,
                                              @RequestBody ReviewCompleteCommand command) {
        ReviewCompleteCommand payload = command == null ? new ReviewCompleteCommand() : command;
        payload.setErrorCode(code);
        return Result.OK(errorManageService.completeReview(payload));
    }

    @Operation(summary = "批量完成复习项（逐项独立，一项失败不影响其它项）")
    @RequiresPermissions("study:kid:review:add")
    @PostMapping("/review/queue/complete-batch")
    public Result<ReviewBatchCompleteVO> completeBatch(
            @RequestBody(required = false) ReviewBatchCompleteCommand command) {
        return Result.OK(reviewBatchService.completeBatch(command));
    }

    // ------------------------------------------------------------------ 内部

    /**
     * 解析目标孩子主键ID —— 统一走 scope 服务，**不在本层另写一套解析口径**。
     *
     * <p>口径（ADR-018 / 契约 §3.2）：家长传 childCode 时先过关系表校验，
     * 孩子不传时取会话里的自己；不存在与无权访问一律 4010。</p>
     */
    private String resolveChildId(String childCode) {
        return scopeService.resolveChildId(childCode);
    }

    /**
     * 孩子端取当前登录孩子。
     */
    private String currentChildId() {
        String childId = scopeService.ownChildId();
        if (childId == null || childId.trim().isEmpty()) {
            throw new StudyForbiddenException("当前登录身份不是孩子，无权访问学习数据");
        }
        return childId;
    }
}
// update-end---author:mavis ---date:2026-11-04  for：【第二档-6 上线学习端错题线】错题列表/错因统计放开 study:kid:error:query 且 childCode 改可选；childCode→childId 的解析统一交给 scope 服务（原先本类私有实现不做关系表校验）-----------
