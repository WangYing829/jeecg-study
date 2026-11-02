package org.jeecg.modules.study.kid.controller;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.jeecg.common.api.vo.Result;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.config.shiro.IgnoreAuth;
import org.jeecg.modules.study.kid.service.StudyKidAuthService;
import org.jeecg.modules.study.kid.vo.KidCandidateVO;
import org.jeecg.modules.study.kid.vo.KidLoginVO;
import org.jeecg.modules.study.kid.vo.KidProfileVO;

/**
 * 孩子端两步登录（T-V0-15）。
 *
 * <p>① {@code POST /lookup} 家庭码 → 候选孩子列表；
 * ② {@code POST /login} 孩子 ID + PIN → JWT。</p>
 *
 * <p>lookup / login 是<b>免认证</b>端点（{@code @IgnoreAuth}），
 * 需要同步登记到底座 {@code jeecg.shiro.excludeUrls}。</p>
 *
 * @author jeecg-study
 */
@Tag(name = "jeecg-study · 孩子登录")
@RestController
@RequestMapping("/api/v1/study/kid")
@RequiredArgsConstructor
public class StudyKidAuthController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final StudyKidAuthService kidAuthService;

    @Operation(summary = "家庭码 → 候选孩子列表（免认证）")
    @IgnoreAuth
    @PostMapping("/lookup")
    public Result<List<KidCandidateVO>> lookup(@RequestBody String body) {
        String familyCode = extract(body, "familyCode");
        return Result.OK(kidAuthService.lookup(familyCode));
    }

    @Operation(summary = "孩子 PIN 登录（免认证）")
    @IgnoreAuth
    @PostMapping("/login")
    public Result<KidLoginVO> login(@RequestBody String body) {
        String childId = extract(body, "childId");
        String pin = extract(body, "pin");
        return Result.OK(kidAuthService.login(childId, pin));
    }

    @Operation(summary = "退出登录")
    @RequiresPermissions("study:kid:logout")
    @PostMapping("/logout")
    public Result<String> logout(HttpServletRequest request) {
        String token = request.getHeader(CommonConstant.X_ACCESS_TOKEN);
        kidAuthService.logout(token);
        return Result.OK("退出成功");
    }

    @Operation(summary = "当前孩子信息")
    @RequiresPermissions("study:kid:profile")
    @GetMapping("/me")
    public Result<KidProfileVO> me() {
        return Result.OK(kidAuthService.me());
    }

    private String extract(String body, String field) {
        try {
            if (body == null || body.trim().isEmpty()) {
                return null;
            }
            com.fasterxml.jackson.databind.JsonNode node = MAPPER.readTree(body);
            com.fasterxml.jackson.databind.JsonNode value = node.get(field);
            return value == null || value.isNull() ? null : value.asText();
        } catch (Exception e) {
            throw new IllegalArgumentException("请求体格式错误", e);
        }
    }
}
