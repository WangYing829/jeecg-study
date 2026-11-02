package org.jeecg.modules.study.writing.controller;

import java.util.List;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.apache.shiro.authz.annotation.RequiresRoles;
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
import org.jeecg.modules.study.writing.service.StudyWritingParentService;
import org.jeecg.modules.study.writing.vo.WritingDraftBriefVO;
import org.jeecg.modules.study.writing.vo.WritingFeedbackListVO;
import org.jeecg.modules.study.writing.vo.WritingFeedbackRequest;
import org.jeecg.modules.study.writing.vo.WritingFeedbackVO;

/**
 * 家长端写作反馈接口（F-PAR-02 / F-WRT-06）。
 *
 * <p><b>路径说明</b>：《接口设计说明书》把这两个接口列在 {@code /parent/feedback}
 * 与 {@code /parent/feedback/list}（§3.5 #65 / #66），归属家长端资源域。
 * 写作域不新建 {@code parent} 包，因此实现放在写作命名空间
 * （{@code /writing/parent/feedback}、{@code /writing/parent/feedbacks}），
 * 避免与家长端控制器的路径映射冲突。</p>
 *
 * <p><b>角色边界</b>：每个方法都带 {@code @RequiresRoles("study_parent")}，
 * 孩子角色不持有家长端角色，服务端就会拦下。</p>
 *
 * <p><b>权限码</b>：按 ADR-025 §3，家长反馈归写作域，权限码用
 * {@code study:parent:writing:feedback} / {@code study:parent:writing:query}。</p>
 *
 * <p><b>只读边界</b>：本控制器只有「提交反馈」一个写操作，
 * 响应里不含讲解、建议、分数一类字段，也不下发作文正文（R-04 / F-PAR-01）。</p>
 *
 * @author jeecg-study
 */
@Tag(name = "jeecg-study · 家长端写作反馈")
@RestController
@RequestMapping("/api/v1/study/writing/parent")
@RequiredArgsConstructor
public class StudyWritingParentController {

    private final StudyWritingParentService writingParentService;

    @Operation(summary = "提交一句话反馈（不超过 100 字）")
    @RequiresRoles("study_parent")
    @RequiresPermissions("study:parent:writing:feedback")
    @PostMapping("/feedback")
    public Result<WritingFeedbackVO> feedback(@RequestBody WritingFeedbackRequest request) {
        return Result.OK(writingParentService.submitFeedback(request));
    }

    @Operation(summary = "反馈清单（只含自己关联的孩子）")
    @RequiresRoles("study_parent")
    @RequiresPermissions("study:parent:writing:query")
    @GetMapping("/feedbacks")
    public Result<WritingFeedbackListVO> feedbacks(
            @RequestParam(value = "childCode", required = false) String childCode) {
        return Result.OK(writingParentService.listFeedbacks(childCode));
    }

    @Operation(summary = "可见孩子的最近初稿（供家长挑反馈对象，只给元信息不给正文）")
    @RequiresRoles("study_parent")
    @RequiresPermissions("study:parent:writing:query")
    @GetMapping("/drafts")
    public Result<List<WritingDraftBriefVO>> drafts(
            @RequestParam(value = "childCode", required = false) String childCode) {
        return Result.OK(writingParentService.listDrafts(childCode));
    }
}
