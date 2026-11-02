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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.study.family.service.StudyFamilyService;
import org.jeecg.modules.study.family.vo.FamilyCreateVO;
import org.jeecg.modules.study.family.vo.FamilyDetailVO;
import org.jeecg.modules.study.family.vo.FamilyMemberVO;

/**
 * 家庭与家长成员管理（T-V0-14）。
 *
 * <p>路径对齐《多用户改造契约》§4.1。所有方法走底座 {@code Result} 包装，
 * 业务错误一律 HTTP 200 + code != 0。</p>
 *
 * @author jeecg-study
 */
@Tag(name = "jeecg-study · 家庭与成员")
@RestController
@RequestMapping("/api/v1/study/family")
@RequiredArgsConstructor
public class StudyFamilyController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final StudyFamilyService familyService;

    @Operation(summary = "创建家庭（首个家长自动成为 OWNER）")
    @RequiresPermissions("study:parent:family:add")
    @PostMapping
    public Result<FamilyCreateVO> create(@RequestBody(required = false) String body) {
        return Result.OK(familyService.create(extractName(body)));
    }

    @Operation(summary = "家庭详情（含成员）")
    @RequiresPermissions("study:parent:family:query")
    @GetMapping
    public Result<FamilyDetailVO> detail(@RequestParam(required = false) String familyId) {
        return Result.OK(familyService.detail(familyId));
    }

    @Operation(summary = "修改家庭名称")
    @RequiresPermissions("study:parent:family:edit")
    @PutMapping
    public Result<String> rename(@RequestBody(required = false) String body) {
        familyService.rename(extractName(body));
        return Result.OK("修改成功");
    }

    @Operation(summary = "重置家庭码（明文仅本次返回）")
    @RequiresPermissions("study:parent:family:edit")
    @PostMapping("/code/reset")
    public Result<String> resetCode() {
        return Result.OK(familyService.resetFamilyCode());
    }

    @Operation(summary = "家庭成员列表")
    @RequiresPermissions("study:parent:family:query")
    @GetMapping("/members")
    public Result<List<FamilyMemberVO>> members(@RequestParam(required = false) String familyId) {
        return Result.OK(familyService.listMembers(familyId));
    }

    @Operation(summary = "添加家长成员（按用户名）")
    @RequiresPermissions("study:parent:family:member")
    @PostMapping("/members")
    public Result<String> addMember(@RequestBody(required = false) String body) {
        String username = extractField(body, "username");
        String relationLabel = extractField(body, "relationLabel");
        familyService.addMember(username, relationLabel);
        return Result.OK("添加成功");
    }

    @Operation(summary = "移除家长成员")
    @RequiresPermissions("study:parent:family:member")
    @DeleteMapping("/members/{id}")
    public Result<String> removeMember(@PathVariable("id") String id) {
        familyService.removeMember(id);
        return Result.OK("移除成功");
    }

    /**
     * 从 JSON 体里取字符串字段。
     */
    private String extractField(String body, String field) {
        if (body == null || body.trim().isEmpty()) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node = MAPPER.readTree(body);
            com.fasterxml.jackson.databind.JsonNode value = node.get(field);
            return value == null || value.isNull() ? null : value.asText();
        } catch (Exception e) {
            throw new IllegalArgumentException("请求体格式错误", e);
        }
    }

    private String extractName(String body) {
        return extractField(body, "familyName");
    }
}
