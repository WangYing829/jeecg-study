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
import org.jeecg.modules.study.error.mapper.StudyChildLookupMapper;
import org.jeecg.modules.study.error.service.StudyErrorManageService;
import org.jeecg.modules.study.error.service.StudyErrorPoolService;
import org.jeecg.modules.study.error.vo.ErrorCauseUpdateRequest;
import org.jeecg.modules.study.error.vo.ErrorPoolItemVO;
import org.jeecg.modules.study.error.vo.ErrorPoolReportVO;
import org.jeecg.modules.study.error.vo.ErrorVariantListVO;
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
 * @author jeecg-study
 */
@Tag(name = "jeecg-study · 统一错题池与复习排期")
@RestController
@RequestMapping("/api/v1/study")
@RequiredArgsConstructor
public class StudyErrorPoolController {

    private final StudyErrorPoolService errorPoolService;
    private final StudyErrorManageService errorManageService;
    private final StudyChildLookupMapper childLookupMapper;
    private final ChildScopeService scopeService;

    @Operation(summary = "错题入池（拍照录入 / 各模块判错后调用）")
    @RequiresPermissions({"study:parent:error:add", "study:kid:error:add"})
    @PostMapping("/error-pool")
    public Result<ErrorPoolReportVO> report(@RequestBody StudyErrorReportCommand command) {
        return Result.OK(errorManageService.reportToPool(command));
    }

    @Operation(summary = "错题列表（统一错题本）")
    @RequiresPermissions("study:parent:error:query")
    @GetMapping("/error-pool")
    public Result<List<ErrorPoolItemVO>> list(@RequestParam("childCode") String childCode) {
        return Result.OK(errorPoolService.listByChild(resolveChildId(childCode)));
    }

    @Operation(summary = "错因分布统计")
    @RequiresPermissions("study:parent:error:query")
    @GetMapping("/error-pool/statistics")
    public Result<Map<String, Integer>> statistics(@RequestParam("childCode") String childCode) {
        return Result.OK(errorPoolService.errorCauseStatistics(resolveChildId(childCode)));
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

    // ------------------------------------------------------------------ 内部

    /**
     * 家长端按 childCode 定位孩子；孩子不存在与无权访问都返回 4010。
     */
    private String resolveChildId(String childCode) {
        String code = childCode == null ? "" : childCode.trim();
        if (code.isEmpty()) {
            throw new StudyForbiddenException();
        }
        String childId = childLookupMapper.selectIdByChildCode(code);
        if (childId == null) {
            throw new StudyForbiddenException();
        }
        return childId;
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
