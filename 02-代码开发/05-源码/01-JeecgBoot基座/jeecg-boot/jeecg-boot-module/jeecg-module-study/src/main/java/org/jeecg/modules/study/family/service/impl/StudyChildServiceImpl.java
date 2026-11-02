package org.jeecg.modules.study.family.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;

import lombok.RequiredArgsConstructor;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.PasswordUtil;
import org.jeecg.modules.system.entity.SysRole;
import org.jeecg.modules.system.entity.SysUser;
import org.jeecg.modules.system.entity.SysUserRole;
import org.jeecg.modules.system.service.ISysRoleService;
import org.jeecg.modules.system.service.ISysUserRoleService;
import org.jeecg.modules.system.service.ISysUserService;
import org.jeecg.modules.study.common.constant.StudyConstants;
import org.jeecg.modules.study.common.exception.StudyForbiddenException;
import org.jeecg.modules.study.common.util.StudySecretUtil;
import org.jeecg.modules.study.family.entity.StudyChild;
import org.jeecg.modules.study.family.entity.StudyFamily;
import org.jeecg.modules.study.family.entity.StudyFamilyMember;
import org.jeecg.modules.study.family.entity.StudyParentChildRel;
import org.jeecg.modules.study.family.mapper.StudyChildMapper;
import org.jeecg.modules.study.family.mapper.StudyFamilyMapper;
import org.jeecg.modules.study.family.mapper.StudyFamilyMemberMapper;
import org.jeecg.modules.study.family.mapper.StudyParentChildRelMapper;
import org.jeecg.modules.study.family.service.StudyChildService;
import org.jeecg.modules.study.family.vo.ChildCreateRequest;
import org.jeecg.modules.study.family.vo.ChildCreateVO;
import org.jeecg.modules.study.family.vo.ChildUpdateRequest;
import org.jeecg.modules.study.family.vo.ChildVO;
import org.jeecg.modules.study.family.vo.MasteryBucketVO;
import org.jeecg.modules.study.family.vo.OverviewVO;
import org.jeecg.modules.study.family.vo.RelationVO;
import org.jeecg.modules.study.idseq.service.StudyIdSequenceService;
import org.jeecg.modules.study.scope.service.ChildScopeService;

/**
 * 孩子档案 + 家长↔孩子多对多关联实现。
 *
 * @author jeecg-study
 */
@Service
@RequiredArgsConstructor
public class StudyChildServiceImpl implements StudyChildService {

    private final StudyChildMapper childMapper;
    private final StudyFamilyMapper familyMapper;
    private final StudyFamilyMemberMapper familyMemberMapper;
    private final StudyParentChildRelMapper relMapper;
    private final ChildScopeService scopeService;
    private final StudyIdSequenceService idSequence;
    private final ISysUserService sysUserService;
    private final ISysRoleService sysRoleService;
    private final ISysUserRoleService sysUserRoleService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChildCreateVO create(ChildCreateRequest request) {
        String userId = scopeService.currentUserId();
        String familyId = currentFamilyId();
        if (familyId == null) {
            throw new JeecgBootException("尚未创建家庭，无法添加孩子", 400);
        }

        String pin = request.getPin() == null || request.getPin().trim().isEmpty()
                ? StudySecretUtil.newPin(6) : request.getPin().trim();
        if (!pin.matches("^[0-9]{" + StudyConstants.PIN_MIN_LENGTH + "," + StudyConstants.PIN_MAX_LENGTH + "}$")) {
            throw new JeecgBootException("PIN 必须是 " + StudyConstants.PIN_MIN_LENGTH + "–"
                    + StudyConstants.PIN_MAX_LENGTH + " 位数字", 400);
        }

        String childCode = idSequence.next("CHD");
        SysUser account = resolveOrCreateAccount(request, childCode);

        String salt = StudySecretUtil.newSalt();
        StudyChild child = new StudyChild();
        child.setId(IdWorker.get32UUID());
        child.setChildCode(childCode);
        child.setFamilyId(familyId);
        child.setUserId(account.getId());
        child.setNickname(request.getNickname());
        child.setAvatar(request.getAvatar());
        child.setGender(request.getGender() == null ? 0 : request.getGender());
        child.setBirthday(request.getBirthday());
        child.setGradeCode(request.getGradeCode());
        child.setPinHash(StudySecretUtil.hashPin(pin, salt));
        child.setPinSalt(salt);
        child.setPinFailCount(0);
        child.setStatus(StudyConstants.STATUS_NORMAL);
        child.setDelFlag(0);
        try {
            childMapper.insert(child);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new JeecgBootException("该账号已绑定过孩子档案", 400);
        }

        // 创建者自动成为主家长
        StudyParentChildRel rel = new StudyParentChildRel();
        rel.setId(IdWorker.get32UUID());
        rel.setParentUserId(userId);
        rel.setChildId(child.getId());
        rel.setRelationLabel(request.getRelationLabel() == null
                ? StudyConstants.RELATION_OTHER : request.getRelationLabel());
        rel.setIsPrimary(1);
        rel.setStatus(StudyConstants.STATUS_NORMAL);
        rel.setDelFlag(0);
        relMapper.insert(rel);

        ChildCreateVO vo = new ChildCreateVO();
        vo.setChildId(child.getId());
        vo.setChildCode(childCode);
        vo.setNickname(child.getNickname());
        vo.setAvatar(child.getAvatar());
        vo.setGender(child.getGender());
        vo.setBirthday(child.getBirthday());
        vo.setGradeCode(child.getGradeCode());
        vo.setUsername(account.getUsername());
        vo.setPin(pin);
        return vo;
    }

    @Override
    public List<ChildVO> listChildren() {
        List<String> ids = scopeService.visibleChildIds();
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        String userId = scopeService.currentUserId();
        List<ChildVO> list = new ArrayList<>();
        for (String childId : ids) {
            StudyChild child = childMapper.selectById(childId);
            if (child == null) {
                continue;
            }
            list.add(toChildVO(child, userId));
        }
        return list;
    }

    @Override
    public ChildVO detail(String childCode) {
        scopeService.assertChildCodeAccess(childCode);
        StudyChild child = childMapper.selectByChildCode(childCode.trim());
        if (child == null) {
            throw new StudyForbiddenException();
        }
        return toChildVO(child, scopeService.currentUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(String childCode, ChildUpdateRequest request) {
        scopeService.assertChildCodeAccess(childCode);
        StudyChild child = childMapper.selectByChildCode(childCode.trim());
        if (child == null) {
            throw new StudyForbiddenException();
        }
        if (request.getNickname() != null && !request.getNickname().trim().isEmpty()) {
            child.setNickname(request.getNickname().trim());
        }
        if (request.getAvatar() != null) {
            child.setAvatar(request.getAvatar());
        }
        if (request.getGender() != null) {
            child.setGender(request.getGender());
        }
        if (request.getGradeCode() != null && !request.getGradeCode().trim().isEmpty()) {
            child.setGradeCode(request.getGradeCode().trim());
        }
        if (request.getBirthday() != null) {
            child.setBirthday(request.getBirthday());
        }
        childMapper.updateById(child);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disable(String childCode) {
        StudyChild child = requirePrimaryParentChild(childCode);
        child.setStatus(StudyConstants.STATUS_DISABLED);
        childMapper.updateById(child);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String resetPin(String childCode) {
        StudyChild child = requirePrimaryParentChild(childCode);
        String pin = StudySecretUtil.newPin(6);
        String salt = StudySecretUtil.newSalt();
        child.setPinHash(StudySecretUtil.hashPin(pin, salt));
        child.setPinSalt(salt);
        child.setPinFailCount(0);
        child.setPinLockUntil(null);
        childMapper.updateById(child);
        return pin;
    }

    @Override
    public List<RelationVO> listRelations(String childCode) {
        scopeService.assertChildCodeAccess(childCode);
        StudyChild child = childMapper.selectByChildCode(childCode.trim());
        if (child == null) {
            throw new StudyForbiddenException();
        }
        List<StudyParentChildRel> rels = relMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StudyParentChildRel>()
                        .eq(StudyParentChildRel::getChildId, child.getId())
                        .eq(StudyParentChildRel::getStatus, StudyConstants.STATUS_NORMAL)
                        .orderByAsc(StudyParentChildRel::getCreateTime)
                        // 追加主键兜底：同一秒创建的多条关系，顺序不能随执行计划漂移
                        .orderByAsc(StudyParentChildRel::getId));
        String selfId = scopeService.currentUserId();
        List<RelationVO> list = new ArrayList<>();
        if (rels == null) {
            return list;
        }
        for (StudyParentChildRel rel : rels) {
            RelationVO vo = new RelationVO();
            vo.setRelationId(rel.getId());
            vo.setRelationLabel(rel.getRelationLabel());
            vo.setIsPrimary(rel.getIsPrimary() != null && rel.getIsPrimary() == 1);
            vo.setStatus(rel.getStatus());
            vo.setIsSelf(rel.getParentUserId() != null && rel.getParentUserId().equals(selfId));
            list.add(vo);
        }
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addRelation(String childCode, String parentUserId, String relationLabel, boolean isPrimary) {
        scopeService.assertChildCodeAccess(childCode);
        StudyChild child = childMapper.selectByChildCode(childCode.trim());
        if (child == null) {
            throw new StudyForbiddenException();
        }
        if (parentUserId == null || parentUserId.trim().isEmpty()) {
            throw new JeecgBootException("家长不能为空", 400);
        }
        // 只能关联本家庭的家长
        StudyFamilyMember member = familyMemberMapper.selectByFamilyAndUser(child.getFamilyId(), parentUserId.trim());
        if (member == null || member.getStatus() == null || member.getStatus() != StudyConstants.STATUS_NORMAL) {
            throw new StudyForbiddenException();
        }
        StudyParentChildRel existed = relMapper.selectByParentAndChild(parentUserId.trim(), child.getId());
        if (existed != null && existed.getStatus() != null && existed.getStatus() == StudyConstants.STATUS_NORMAL) {
            throw new JeecgBootException("该家长已关联此孩子", 400);
        }
        if (existed != null) {
            existed.setStatus(StudyConstants.STATUS_NORMAL);
            existed.setRelationLabel(relationLabel);
            existed.setIsPrimary(isPrimary ? 1 : 0);
            relMapper.updateById(existed);
            return;
        }
        StudyParentChildRel rel = new StudyParentChildRel();
        rel.setId(IdWorker.get32UUID());
        rel.setParentUserId(parentUserId.trim());
        rel.setChildId(child.getId());
        rel.setRelationLabel(relationLabel);
        rel.setIsPrimary(isPrimary ? 1 : 0);
        rel.setStatus(StudyConstants.STATUS_NORMAL);
        rel.setDelFlag(0);
        relMapper.insert(rel);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeRelation(String childCode, String parentUserId) {
        scopeService.assertChildCodeAccess(childCode);
        StudyChild child = childMapper.selectByChildCode(childCode.trim());
        if (child == null) {
            throw new StudyForbiddenException();
        }
        String selfId = scopeService.currentUserId();
        if (parentUserId == null || !parentUserId.equals(selfId)) {
            throw new StudyForbiddenException();
        }
        StudyParentChildRel rel = relMapper.selectByParentAndChild(selfId, child.getId());
        if (rel == null) {
            throw new StudyForbiddenException();
        }
        rel.setStatus(StudyConstants.STATUS_DISABLED);
        relMapper.updateById(rel);
    }

    @Override
    public OverviewVO overview(String childCode) {
        scopeService.assertChildCodeAccess(childCode);
        StudyChild child = childMapper.selectByChildCode(childCode.trim());
        if (child == null) {
            throw new StudyForbiddenException();
        }
        OverviewVO vo = new OverviewVO();
        vo.setChildId(child.getId());
        vo.setChildCode(child.getChildCode());
        vo.setNickname(child.getNickname());
        vo.setGradeCode(child.getGradeCode());
        vo.setEyeActiveMinutes(0);
        vo.setEyeRestCount(0);
        vo.setErrorTotal(0);
        vo.setErrorPending(0);
        vo.setErrorCauseTop(Collections.emptyList());
        vo.setMasteryDistribution(masteryDistribution());
        return vo;
    }

    // ------------------------------------------------------------------ 内部

    /**
     * 取孩子并校验当前登录者是其主家长；否则 4010。
     */
    private StudyChild requirePrimaryParentChild(String childCode) {
        scopeService.assertChildCodeAccess(childCode);
        StudyChild child = childMapper.selectByChildCode(childCode.trim());
        if (child == null) {
            throw new StudyForbiddenException();
        }
        String selfId = scopeService.currentUserId();
        StudyParentChildRel rel = relMapper.selectByParentAndChild(selfId, child.getId());
        if (rel == null || rel.getStatus() == null || rel.getStatus() != StudyConstants.STATUS_NORMAL
                || rel.getIsPrimary() == null || rel.getIsPrimary() != 1) {
            throw new StudyForbiddenException();
        }
        return child;
    }

    private ChildVO toChildVO(StudyChild child, String userId) {
        ChildVO vo = new ChildVO();
        vo.setChildId(child.getId());
        vo.setChildCode(child.getChildCode());
        vo.setNickname(child.getNickname());
        vo.setAvatar(child.getAvatar());
        vo.setGender(child.getGender());
        vo.setBirthday(child.getBirthday());
        vo.setGradeCode(child.getGradeCode());
        vo.setStatus(child.getStatus());
        StudyParentChildRel rel = relMapper.selectByParentAndChild(userId, child.getId());
        if (rel != null) {
            vo.setRelationLabel(rel.getRelationLabel());
            vo.setIsPrimary(rel.getIsPrimary() != null && rel.getIsPrimary() == 1);
        }
        return vo;
    }

    private List<MasteryBucketVO> masteryDistribution() {
        List<MasteryBucketVO> list = new ArrayList<>();
        list.add(bucket("未掌握", 0));
        list.add(bucket("巩固中", 0));
        list.add(bucket("已掌握", 0));
        return list;
    }

    private MasteryBucketVO bucket(String name, int count) {
        MasteryBucketVO vo = new MasteryBucketVO();
        vo.setBucket(name);
        vo.setCount(count);
        return vo;
    }

    private String currentFamilyId() {
        String userId = scopeService.currentUserId();
        List<String> ids = familyMemberMapper.selectActiveFamilyIdsByUserId(userId);
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        return ids.get(0);
    }

    /**
     * 解析或创建孩子的登录账号（sys_user + 角色 study_kid）。
     */
    private SysUser resolveOrCreateAccount(ChildCreateRequest request, String childCode) {
        if (request.getUserId() != null && !request.getUserId().trim().isEmpty()) {
            String userId = request.getUserId().trim();
            SysUser existed = sysUserService.getById(userId);
            if (existed == null) {
                throw new JeecgBootException("绑定的账号不存在", 400);
            }
            grantKidRole(userId);
            return existed;
        }

        String username = "kid_" + childCode.toLowerCase();
        SysUser existed = sysUserService.getUserByName(username);
        if (existed != null) {
            grantKidRole(existed.getId());
            return existed;
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setRealname(request.getNickname() == null ? username : request.getNickname());
        String salt = org.jeecg.common.util.oConvertUtils.randomGen(8);
        user.setSalt(salt);
        user.setPassword(PasswordUtil.encrypt(username, IdWorker.get32UUID(), salt));
        user.setStatus(1);
        user.setDelFlag(0);
        user.setAvatar(request.getAvatar());
        user.setSex(request.getGender() == null ? 0 : request.getGender());
        user.setBirthday(request.getBirthday());
        sysUserService.addUserWithRole(user, kidRoleId());
        return user;
    }

    private void grantKidRole(String userId) {
        String roleId = kidRoleId();
        if (roleId == null) {
            return;
        }
        Long existed = sysUserRoleService.getBaseMapper().selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId)
                        .eq(SysUserRole::getRoleId, roleId));
        if (existed != null && existed > 0) {
            return;
        }
        SysUserRole userRole = new SysUserRole();
        userRole.setId(IdWorker.get32UUID());
        userRole.setUserId(userId);
        userRole.setRoleId(roleId);
        sysUserRoleService.save(userRole);
    }

    private String kidRoleId() {
        SysRole role = sysRoleService.getRoleNoTenant(StudyConstants.ROLE_KID);
        return role == null ? null : role.getId();
    }
}
