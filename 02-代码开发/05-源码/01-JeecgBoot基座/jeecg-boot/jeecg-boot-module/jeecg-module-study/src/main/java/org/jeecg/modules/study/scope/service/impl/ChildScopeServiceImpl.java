package org.jeecg.modules.study.scope.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.shiro.SecurityUtils;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.study.common.constant.StudyConstants;
import org.jeecg.modules.study.common.exception.StudyForbiddenException;
import org.jeecg.modules.study.family.entity.StudyChild;
import org.jeecg.modules.study.family.mapper.StudyChildMapper;
import org.jeecg.modules.study.family.mapper.StudyFamilyMemberMapper;
import org.jeecg.modules.study.family.mapper.StudyParentChildRelMapper;
import org.jeecg.modules.study.scope.service.ChildScopeService;

/**
 * 数据范围服务实现。
 *
 * <p>家长与孩子的判定顺序：**先看是不是孩子**（{@code study_child.user_id} 命中即是），
 * 再按家长关系表算可见集合。家长↔孩子多对多，因此家长可见集合可能不止一个。</p>
 *
 * @author jeecg-study
 */
@Service
@RequiredArgsConstructor
public class ChildScopeServiceImpl implements ChildScopeService {

    private final StudyChildMapper childMapper;
    private final StudyFamilyMemberMapper familyMemberMapper;
    private final StudyParentChildRelMapper relMapper;

    @Override
    public String currentUserId() {
        LoginUser loginUser = currentLoginUser();
        if (loginUser == null || loginUser.getId() == null || loginUser.getId().isEmpty()) {
            throw new JeecgBootException("未登录或会话已过期", 401);
        }
        return loginUser.getId();
    }

    @Override
    public List<String> currentRoles() {
        LoginUser loginUser = currentLoginUser();
        if (loginUser == null || loginUser.getRoleCode() == null) {
            return Collections.emptyList();
        }
        String roleCode = loginUser.getRoleCode().trim();
        if (roleCode.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> roles = new ArrayList<>();
        for (String r : roleCode.split(",")) {
            String trimmed = r.trim();
            if (!trimmed.isEmpty()) {
                roles.add(trimmed);
            }
        }
        return roles;
    }

    @Override
    public String ownChildId() {
        LoginUser loginUser = currentLoginUser();
        if (loginUser == null || loginUser.getId() == null) {
            return null;
        }
        StudyChild child = childMapper.selectByUserId(loginUser.getId());
        return child == null ? null : child.getId();
    }

    @Override
    public List<String> visibleChildIds() {
        String userId = currentUserId();

        // 孩子身份：只有自己
        StudyChild self = childMapper.selectByUserId(userId);
        if (self != null) {
            if (self.getStatus() == null || self.getStatus() != StudyConstants.STATUS_NORMAL) {
                throw new StudyForbiddenException("账号已停用");
            }
            return Collections.singletonList(self.getId());
        }

        // 家长身份：必须是某个家庭的有效成员，否则权限已失效
        List<String> familyIds = familyMemberMapper.selectActiveFamilyIdsByUserId(userId);
        if (familyIds == null || familyIds.isEmpty()) {
            throw new StudyForbiddenException("不在任何有效家庭中，无权访问孩子数据");
        }
        Set<String> familyIdSet = new LinkedHashSet<>(familyIds);

        // 关系表里生效关联的孩子，再与「孩子所属家庭」求交集
        List<String> relChildIds = relMapper.selectActiveChildIds(userId);
        Set<String> visible = new LinkedHashSet<>();
        if (relChildIds != null) {
            for (String childId : relChildIds) {
                StudyChild child = childMapper.selectById(childId);
                if (child == null) {
                    continue;
                }
                if (familyIdSet.contains(child.getFamilyId())) {
                    visible.add(childId);
                }
            }
        }
        return new ArrayList<>(visible);
    }

    @Override
    public List<String> visibleChildCodes() {
        List<String> ids = visibleChildIds();
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> codes = childMapper.selectChildCodesByIds(ids);
        return codes == null ? Collections.emptyList() : codes;
    }

    @Override
    public void assertChildAccess(String childId) {
        if (childId == null || childId.trim().isEmpty()) {
            throw new StudyForbiddenException();
        }
        if (!visibleChildIds().contains(childId)) {
            throw new StudyForbiddenException();
        }
    }

    @Override
    public void assertChildCodeAccess(String childCode) {
        if (childCode == null || childCode.trim().isEmpty()) {
            throw new StudyForbiddenException();
        }
        StudyChild child = childMapper.selectByChildCode(childCode.trim());
        // 不存在与「存在但无权」返回完全一致的响应，避免资源探测
        if (child == null) {
            throw new StudyForbiddenException();
        }
        assertChildAccess(child.getId());
    }

    @Override
    public String resolveChildId(String childCode) {
        String code = childCode == null ? "" : childCode.trim();
        if (!code.isEmpty()) {
            // 越权 / 不存在一律 4010
            assertChildCodeAccess(code);
            StudyChild child = childMapper.selectByChildCode(code);
            if (child == null) {
                throw new StudyForbiddenException();
            }
            return child.getId();
        }
        String own = ownChildId();
        if (own != null && !own.trim().isEmpty()) {
            return own;
        }
        List<String> visible = visibleChildIds();
        if (visible == null || visible.isEmpty()) {
            throw new StudyForbiddenException("没有关联的孩子，无法访问学习数据");
        }
        List<String> sorted = new ArrayList<>(visible);
        Collections.sort(sorted);
        return sorted.get(0);
    }

    private LoginUser currentLoginUser() {
        try {
            Object principal = SecurityUtils.getSubject().getPrincipal();
            return principal instanceof LoginUser ? (LoginUser) principal : null;
        } catch (Exception e) {
            return null;
        }
    }
}
