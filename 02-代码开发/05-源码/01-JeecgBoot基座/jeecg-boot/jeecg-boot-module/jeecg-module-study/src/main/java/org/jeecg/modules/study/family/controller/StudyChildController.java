package org.jeecg.modules.study.family.controller;

import java.util.List;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.study.family.service.StudyChildService;
import org.jeecg.modules.study.family.vo.ChildCreateRequest;
import org.jeecg.modules.study.family.vo.ChildCreateVO;
import org.jeecg.modules.study.family.vo.ChildUpdateRequest;
import org.jeecg.modules.study.family.vo.ChildVO;
import org.jeecg.modules.study.family.vo.OverviewVO;
import org.jeecg.modules.study.family.vo.RelationVO;

/**
 * 孩子档案 + 家长↔孩子多对多关联（T-V0-15 / T-V0-16）。
 *
 * @author jeecg-study
 */
@Tag(name = "jeecg-study · 孩子档案")
@RestController
@RequestMapping("/api/v1/study/children")
@RequiredArgsConstructor
public class StudyChildController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final StudyChildService childService;

    @Operation(summary = "我关联的孩子列表")
    @RequiresPermissions("study:parent:child:query")
    @GetMapping
    public Result<List<ChildVO>> list() {
        return Result.OK(childService.listChildren());
    }

    @Operation(summary = "新增孩子档案（同时创建账号 + PIN）")
    @RequiresPermissions("study:parent:child:add")
    @PostMapping
    public Result<ChildCreateVO> create(@RequestBody ChildCreateRequest request) {
        return Result.OK(childService.create(request));
    }

    @Operation(summary = "孩子档案详情")
    @RequiresPermissions("study:parent:child:query")
    @GetMapping("/{code}")
    public Result<ChildVO> detail(@PathVariable("code") String code) {
        return Result.OK(childService.detail(code));
    }

    @Operation(summary = "更新孩子档案")
    @RequiresPermissions("study:parent:child:edit")
    @PutMapping("/{code}")
    public Result<String> update(@PathVariable("code") String code, @RequestBody ChildUpdateRequest request) {
        childService.update(code, request);
        return Result.OK("修改成功");
    }

    @Operation(summary = "停用孩子档案（数据保留）")
    @RequiresPermissions("study:parent:child:edit")
    @DeleteMapping("/{code}")
    public Result<String> disable(@PathVariable("code") String code) {
        childService.disable(code);
        return Result.OK("已停用");
    }

    @Operation(summary = "重置孩子 PIN（明文仅本次返回）")
    @RequiresPermissions("study:parent:child:pin")
    @PostMapping("/{code}/pin/reset")
    public Result<String> resetPin(@PathVariable("code") String code) {
        return Result.OK(childService.resetPin(code));
    }

    @Operation(summary = "孩子的关联家长列表")
    @RequiresPermissions("study:parent:child:query")
    @GetMapping("/{code}/relations")
    public Result<List<RelationVO>> relations(@PathVariable("code") String code) {
        return Result.OK(childService.listRelations(code));
    }

    @Operation(summary = "关联家长")
    @RequiresPermissions("study:parent:child:relation")
    @PostMapping("/{code}/relations")
    public Result<String> addRelation(@PathVariable("code") String code, @RequestBody String body) {
        ChildRelationRequest request = parse(body, ChildRelationRequest.class);
        childService.addRelation(code, request.getParentUserId(), request.getRelationLabel(), request.isPrimary());
        return Result.OK("关联成功");
    }

    @Operation(summary = "解除关联")
    @RequiresPermissions("study:parent:child:relation")
    @DeleteMapping("/{code}/relations/{parentUserId}")
    public Result<String> removeRelation(@PathVariable("code") String code,
                                         @PathVariable("parentUserId") String parentUserId) {
        childService.removeRelation(code, parentUserId);
        return Result.OK("已解除");
    }

    @Operation(summary = "孩子概览（家长视角）")
    @RequiresPermissions("study:parent:child:query")
    @GetMapping("/{code}/overview")
    public Result<OverviewVO> overview(@PathVariable("code") String code) {
        return Result.OK(childService.overview(code));
    }

    private <T> T parse(String body, Class<T> type) {
        try {
            if (body == null || body.trim().isEmpty()) {
                return type.getDeclaredConstructor().newInstance();
            }
            return MAPPER.readValue(body, type);
        } catch (Exception e) {
            throw new IllegalArgumentException("请求体格式错误", e);
        }
    }

    /**
     * 关联家长入参。
     */
    public static class ChildRelationRequest {
        private String parentUserId;
        private String relationLabel;
        private boolean primary;

        public String getParentUserId() {
            return parentUserId;
        }

        public void setParentUserId(String parentUserId) {
            this.parentUserId = parentUserId;
        }

        public String getRelationLabel() {
            return relationLabel;
        }

        public void setRelationLabel(String relationLabel) {
            this.relationLabel = relationLabel;
        }

        public boolean isPrimary() {
            return primary;
        }

        public void setPrimary(boolean primary) {
            this.primary = primary;
        }
    }
}
