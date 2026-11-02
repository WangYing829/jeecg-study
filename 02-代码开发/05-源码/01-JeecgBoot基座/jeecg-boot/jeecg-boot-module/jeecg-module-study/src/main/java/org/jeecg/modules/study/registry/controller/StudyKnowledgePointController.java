package org.jeecg.modules.study.registry.controller;

import java.util.List;

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
import org.jeecg.modules.study.registry.service.StudyKnowledgePointService;
import org.jeecg.modules.study.registry.vo.KnowledgePointContentStatVO;
import org.jeecg.modules.study.registry.vo.KnowledgePointImportRequest;
import org.jeecg.modules.study.registry.vo.KnowledgePointTreeVO;
import org.jeecg.modules.study.registry.vo.KnowledgePointUpdateRequest;
import org.jeecg.modules.study.registry.vo.KnowledgePointVO;
import org.jeecg.modules.study.registry.vo.KnowledgePointVersionVO;
import org.jeecg.modules.study.registry.vo.MotherQuestionOptionVO;

/**
 * 坐标树（考点树）接口（F-BASE-02 / F-REG-01..05）。
 *
 * <p>四层结构：地区考纲 → 学科 → 单元 → 考点；全局内容表，家庭内共享。</p>
 *
 * @author jeecg-study
 */
@Tag(name = "jeecg-study · 坐标树")
@RestController
@RequestMapping("/api/v1/study/knowledge-points")
@RequiredArgsConstructor
public class StudyKnowledgePointController {

    private final StudyKnowledgePointService knowledgePointService;

    @Operation(summary = "坐标树全树（可按学科过滤）")
    @RequiresPermissions("study:parent:knowledge:query")
    @GetMapping("/tree")
    public Result<KnowledgePointTreeVO> tree(@RequestParam(value = "subjectCode", required = false) String subjectCode) {
        return Result.OK(knowledgePointService.tree(subjectCode));
    }

    @Operation(summary = "考纲版本列表")
    @RequiresPermissions("study:parent:knowledge:query")
    @GetMapping("/versions")
    public Result<List<KnowledgePointVersionVO>> versions() {
        return Result.OK(knowledgePointService.versions());
    }

    @Operation(summary = "母题下拉（只列确实存在的母题）")
    @RequiresPermissions("study:parent:knowledge:query")
    @GetMapping("/mother-questions")
    public Result<List<MotherQuestionOptionVO>> motherQuestions(
            @RequestParam(value = "subjectCode", required = false) String subjectCode) {
        return Result.OK(knowledgePointService.motherQuestionOptions(subjectCode));
    }

    @Operation(summary = "按名称 / 编码检索考点")
    @RequiresPermissions("study:parent:knowledge:query")
    @GetMapping("/search")
    public Result<List<KnowledgePointVO>> search(@RequestParam("keyword") String keyword,
                                                 @RequestParam(value = "subjectCode", required = false) String subjectCode) {
        return Result.OK(knowledgePointService.search(keyword, subjectCode));
    }

    @Operation(summary = "考点详情（已失效节点也可查，保证历史记录可解析）")
    @RequiresPermissions("study:parent:knowledge:query")
    @GetMapping("/{code}")
    public Result<KnowledgePointVO> detail(@PathVariable("code") String code) {
        return Result.OK(knowledgePointService.detail(code));
    }

    @Operation(summary = "考点下挂内容统计（题目/单词/母题/语法点/写作）")
    @RequiresPermissions("study:parent:knowledge:query")
    @GetMapping("/{code}/children")
    public Result<KnowledgePointContentStatVO> children(@PathVariable("code") String code) {
        return Result.OK(knowledgePointService.contentStat(code));
    }

    @Operation(summary = "节点历史版本")
    @RequiresPermissions("study:parent:knowledge:query")
    @GetMapping("/{code}/history")
    public Result<List<KnowledgePointVO>> history(@PathVariable("code") String code) {
        return Result.OK(knowledgePointService.history(code));
    }

    @Operation(summary = "维护考点四属性（题型 / 分值权重 / 常见错因 / 母题）")
    @RequiresPermissions("study:parent:knowledge:edit")
    @PutMapping("/{code}")
    public Result<KnowledgePointVO> update(@PathVariable("code") String code,
                                           @RequestBody KnowledgePointUpdateRequest request) {
        return Result.OK(knowledgePointService.updateAttributes(code, request));
    }

    @Operation(summary = "导入坐标树（幂等；导入新版本时旧节点只失效不删除）")
    @RequiresPermissions("study:parent:knowledge:import")
    @PostMapping("/import")
    public Result<List<KnowledgePointVO>> importTree(@RequestBody KnowledgePointImportRequest request) {
        return Result.OK(knowledgePointService.importTree(request));
    }
}
