package org.jeecg.modules.study.family.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;

import lombok.RequiredArgsConstructor;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.system.entity.SysRole;
import org.jeecg.modules.system.entity.SysUser;
import org.jeecg.modules.system.entity.SysUserRole;
import org.jeecg.modules.system.service.ISysRoleService;
import org.jeecg.modules.system.service.ISysUserRoleService;
import org.jeecg.modules.system.service.ISysUserService;
import org.jeecg.modules.study.common.config.StudyProperties;
import org.jeecg.modules.study.common.constant.StudyConstants;
import org.jeecg.modules.study.common.exception.StudyForbiddenException;
import org.jeecg.modules.study.common.util.StudySecretUtil;
import org.jeecg.modules.study.family.entity.StudyFamily;
import org.jeecg.modules.study.family.entity.StudyFamilyMember;
import org.jeecg.modules.study.family.mapper.StudyFamilyMapper;
import org.jeecg.modules.study.family.mapper.StudyFamilyMemberMapper;
import org.jeecg.modules.study.family.service.StudyFamilyService;
import org.jeecg.modules.study.family.vo.FamilyCreateVO;
import org.jeecg.modules.study.family.vo.FamilyDetailVO;
import org.jeecg.modules.study.family.vo.FamilyMemberVO;
import org.jeecg.modules.study.scope.service.ChildScopeService;

/**
 * 家庭与家长成员管理实现。
 *
 * @author jeecg-study
 */
@Service
@RequiredArgsConstructor
public class StudyFamilyServiceImpl implements StudyFamilyService {

    /** 默认家庭名称 */
    private static final String DEFAULT_FAMILY_NAME = "我的家庭";

    private final StudyFamilyMapper familyMapper;
    private final StudyFamilyMemberMapper memberMapper;
    private final ChildScopeService scopeService;
    private final StudyProperties studyProperties;
    private final ISysUserService sysUserService;
    private final ISysRoleService sysRoleService;
    private final ISysUserRoleService sysUserRoleService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FamilyCreateVO create(String familyName) {
        String userId = scopeService.currentUserId();
        List<String> owned = memberMapper.selectActiveFamilyIdsByUserId(userId);
        if (owned != null && !owned.isEmpty()) {
            throw new JeecgBootException("已存在家庭，一个账号只能属于一个家庭", 400);
        }

        String plaintextCode = StudySecretUtil.newFamilyCode();
        StudyFamily family = new StudyFamily();
        family.setId(IdWorker.get32UUID());
        family.setFamilyCode(StudySecretUtil.hashFamilyCode(plaintextCode, pepper()));
        family.setFamilyCodeSalt(StudySecretUtil.newSalt());
        family.setFamilyName(familyName == null || familyName.trim().isEmpty()
                ? DEFAULT_FAMILY_NAME : familyName.trim());
        family.setStatus(StudyConstants.STATUS_NORMAL);
        family.setDelFlag(0);
        familyMapper.insert(family);

        StudyFamilyMember owner = new StudyFamilyMember();
        owner.setId(IdWorker.get32UUID());
        owner.setFamilyId(family.getId());
        owner.setUserId(userId);
        owner.setMemberRole(StudyConstants.MEMBER_ROLE_OWNER);
        owner.setRelationLabel(StudyConstants.RELATION_OTHER);
        owner.setStatus(StudyConstants.STATUS_NORMAL);
        owner.setDelFlag(0);
        memberMapper.insert(owner);

        FamilyCreateVO vo = new FamilyCreateVO();
        vo.setFamilyId(family.getId());
        vo.setFamilyName(family.getFamilyName());
        vo.setFamilyCode(plaintextCode);
        vo.setMemberRole(StudyConstants.MEMBER_ROLE_OWNER);
        return vo;
    }

    @Override
    public FamilyDetailVO detail(String familyId) {
        String myFamilyId = resolveOwnFamily(familyId);
        StudyFamily family = familyMapper.selectById(myFamilyId);
        if (family == null) {
            throw new StudyForbiddenException();
        }
        List<StudyFamilyMember> members = memberMapper.selectActiveByFamilyId(myFamilyId);

        FamilyDetailVO vo = new FamilyDetailVO();
        vo.setFamilyId(family.getId());
        vo.setFamilyName(family.getFamilyName());
        vo.setMemberCount(members == null ? 0 : members.size());
        vo.setMyRole(myRole(myFamilyId));
        vo.setMembers(toMemberVOs(members));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rename(String familyName) {
        if (familyName == null || familyName.trim().isEmpty()) {
            throw new JeecgBootException("家庭名称不能为空", 400);
        }
        String familyId = resolveOwnFamily(null);
        StudyFamily family = familyMapper.selectById(familyId);
        if (family == null) {
            throw new StudyForbiddenException();
        }
        family.setFamilyName(familyName.trim());
        familyMapper.updateById(family);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String resetFamilyCode() {
        String familyId = resolveOwnFamily(null);
        StudyFamily family = familyMapper.selectById(familyId);
        if (family == null) {
            throw new StudyForbiddenException();
        }
        String plaintextCode = StudySecretUtil.newFamilyCode();
        family.setFamilyCode(StudySecretUtil.hashFamilyCode(plaintextCode, pepper()));
        family.setFamilyCodeSalt(StudySecretUtil.newSalt());
        familyMapper.updateById(family);
        return plaintextCode;
    }

    @Override
    public List<FamilyMemberVO> listMembers(String familyId) {
        String myFamilyId = resolveOwnFamily(familyId);
        List<StudyFamilyMember> members = memberMapper.selectActiveByFamilyId(myFamilyId);
        return toMemberVOs(members);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addMember(String username, String relationLabel) {
        if (username == null || username.trim().isEmpty()) {
            throw new JeecgBootException("用户名不能为空", 400);
        }
        String familyId = resolveOwnFamily(null);
        SysUser target = sysUserService.getUserByName(username.trim());
        if (target == null) {
            throw new JeecgBootException("账号不存在：" + username.trim(), 400);
        }
        StudyFamilyMember existing = memberMapper.selectByFamilyAndUser(familyId, target.getId());
        if (existing != null && existing.getStatus() != null && existing.getStatus() == StudyConstants.STATUS_NORMAL) {
            throw new JeecgBootException("该账号已在本家庭中", 400);
        }
        if (existing != null) {
            existing.setStatus(StudyConstants.STATUS_NORMAL);
            existing.setRelationLabel(relationLabel);
            memberMapper.updateById(existing);
        } else {
            StudyFamilyMember member = new StudyFamilyMember();
            member.setId(IdWorker.get32UUID());
            member.setFamilyId(familyId);
            member.setUserId(target.getId());
            member.setMemberRole(StudyConstants.MEMBER_ROLE_PARENT);
            member.setRelationLabel(relationLabel);
            member.setStatus(StudyConstants.STATUS_NORMAL);
            member.setDelFlag(0);
            memberMapper.insert(member);
        }
        grantParentRole(target.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeMember(String memberId) {
        String userId = scopeService.currentUserId();
        StudyFamilyMember member = memberMapper.selectById(memberId);
        if (member == null) {
            throw new StudyForbiddenException();
        }
        String myFamilyId = resolveOwnFamily(null);
        if (!myFamilyId.equals(member.getFamilyId())) {
            throw new StudyForbiddenException();
        }
        if (member.getUserId() != null && member.getUserId().equals(userId)) {
            throw new JeecgBootException("不能移除自己", 400);
        }
        member.setStatus(StudyConstants.STATUS_DISABLED);
        memberMapper.updateById(member);
    }

    @Override
    public String currentFamilyId() {
        String userId = scopeService.currentUserId();
        List<String> ids = memberMapper.selectActiveFamilyIdsByUserId(userId);
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        return ids.get(0);
    }

    // ------------------------------------------------------------------ 内部

    /**
     * 解析目标家庭：为空时取当前登录者所在家庭；不为空时必须属于当前登录者，否则 4010。
     */
    private String resolveOwnFamily(String familyId) {
        String myFamilyId = currentFamilyId();
        if (myFamilyId == null) {
            throw new StudyForbiddenException("尚未创建家庭");
        }
        if (familyId == null || familyId.trim().isEmpty()) {
            return myFamilyId;
        }
        if (!myFamilyId.equals(familyId.trim())) {
            throw new StudyForbiddenException();
        }
        return myFamilyId;
    }

    private String myRole(String familyId) {
        String userId = scopeService.currentUserId();
        StudyFamilyMember member = memberMapper.selectByFamilyAndUser(familyId, userId);
        return member == null ? null : member.getMemberRole();
    }

    /**
     * 成员视图刻意**不暴露** userId / 用户名 / 昵称 / 头像 ——
     * R-04-5 要求家长 A 的响应里检索不到家长 B 的任何标识。
     */
    private List<FamilyMemberVO> toMemberVOs(List<StudyFamilyMember> members) {
        if (members == null || members.isEmpty()) {
            return Collections.emptyList();
        }
        String selfId = scopeService.currentUserId();
        List<FamilyMemberVO> list = new ArrayList<>(members.size());
        for (StudyFamilyMember member : members) {
            FamilyMemberVO vo = new FamilyMemberVO();
            vo.setMemberId(member.getId());
            vo.setRelationLabel(member.getRelationLabel());
            vo.setMemberRole(member.getMemberRole());
            vo.setStatus(member.getStatus());
            vo.setIsSelf(member.getUserId() != null && member.getUserId().equals(selfId));
            vo.setCreateTime(member.getCreateTime());
            list.add(vo);
        }
        return list;
    }

    private void grantParentRole(String userId) {
        SysRole role = sysRoleService.getRoleNoTenant(StudyConstants.ROLE_PARENT);
        if (role == null) {
            return;
        }
        Long existed = sysUserRoleService.getBaseMapper().selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId)
                        .eq(SysUserRole::getRoleId, role.getId()));
        if (existed != null && existed > 0) {
            return;
        }
        SysUserRole userRole = new SysUserRole();
        userRole.setId(IdWorker.get32UUID());
        userRole.setUserId(userId);
        userRole.setRoleId(role.getId());
        sysUserRoleService.save(userRole);
    }

    private String pepper() {
        String pepper = studyProperties.getFamilyCodePepper();
        if (pepper == null || pepper.trim().isEmpty()) {
            throw new JeecgBootException("未配置家庭码 pepper（jeecg.study.family-code-pepper）", 500);
        }
        return pepper;
    }
}
