package org.jeecg.modules.study.kid.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import lombok.RequiredArgsConstructor;

import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.util.JwtUtil;
import org.jeecg.common.util.RedisUtil;
import org.jeecg.modules.system.entity.SysUser;
import org.jeecg.modules.system.service.ISysUserService;
import org.jeecg.modules.study.common.config.StudyProperties;
import org.jeecg.modules.study.common.constant.StudyConstants;
import org.jeecg.modules.study.common.constant.StudyErrorCode;
import org.jeecg.modules.study.common.exception.StudyForbiddenException;
import org.jeecg.modules.study.common.util.StudySecretUtil;
import org.jeecg.modules.study.family.entity.StudyChild;
import org.jeecg.modules.study.family.entity.StudyFamily;
import org.jeecg.modules.study.family.entity.StudyParentChildRel;
import org.jeecg.modules.study.family.mapper.StudyChildMapper;
import org.jeecg.modules.study.family.mapper.StudyFamilyMapper;
import org.jeecg.modules.study.family.mapper.StudyParentChildRelMapper;
import org.jeecg.modules.study.kid.service.StudyKidAuthService;
import org.jeecg.modules.study.kid.vo.KidCandidateVO;
import org.jeecg.modules.study.kid.vo.KidLoginVO;
import org.jeecg.modules.study.kid.vo.KidProfileVO;
import org.jeecg.modules.study.scope.service.ChildScopeService;

/**
 * 孩子端两步登录实现。
 *
 * <p><b>失败次数与锁定</b>：连续 5 次错误 → 锁 10 分钟；锁定期间正确 PIN 也被拒。
 * 锁定提示<b>不暴露失败次数</b>，避免被用来试探剩余机会。</p>
 *
 * @author jeecg-study
 */
@Service
@RequiredArgsConstructor
public class StudyKidAuthServiceImpl implements StudyKidAuthService {

    private final StudyChildMapper childMapper;
    private final StudyFamilyMapper familyMapper;
    private final StudyParentChildRelMapper relMapper;
    private final ChildScopeService scopeService;
    private final StudyProperties studyProperties;
    private final ISysUserService sysUserService;
    private final RedisUtil redisUtil;

    @Override
    public List<KidCandidateVO> lookup(String familyCode) {
        if (familyCode == null || familyCode.trim().isEmpty()) {
            throw new JeecgBootException("家庭码不正确", StudyErrorCode.FAMILY_CODE_INVALID);
        }
        String hash = StudySecretUtil.hashFamilyCode(familyCode.trim().toUpperCase(), pepper());
        StudyFamily family = familyMapper.selectByCodeHash(hash);
        if (family == null || family.getStatus() == null || family.getStatus() != StudyConstants.STATUS_NORMAL) {
            throw new JeecgBootException("家庭码不正确", StudyErrorCode.FAMILY_CODE_INVALID);
        }
        List<StudyChild> children = childMapper.selectByFamilyId(family.getId());
        List<KidCandidateVO> list = new ArrayList<>();
        if (children == null) {
            return list;
        }
        for (StudyChild child : children) {
            if (child.getStatus() == null || child.getStatus() != StudyConstants.STATUS_NORMAL) {
                continue;
            }
            KidCandidateVO vo = new KidCandidateVO();
            // 候选列表**只暴露三个字段**，不泄露 pinHash / userId / 生日 / 家长昵称
            vo.setId(child.getId());
            vo.setNickname(child.getNickname());
            vo.setAvatar(child.getAvatar());
            list.add(vo);
        }
        return list;
    }

    @Override
    public KidLoginVO login(String childId, String pin) {
        StudyChild child = childId == null ? null : childMapper.selectById(childId.trim());
        if (child == null) {
            throw new StudyForbiddenException();
        }
        if (child.getStatus() == null || child.getStatus() != StudyConstants.STATUS_NORMAL) {
            throw new JeecgBootException("账号已停用", 400);
        }
        Date now = new Date();
        if (child.getPinLockUntil() != null && child.getPinLockUntil().after(now)) {
            throw new JeecgBootException("登录已锁定，请稍后再试", 400);
        }
        if (!StudySecretUtil.verifyPin(pin, child.getPinSalt(), child.getPinHash())) {
            int failCount = child.getPinFailCount() == null ? 0 : child.getPinFailCount();
            failCount = failCount + 1;
            Date lockUntil = null;
            if (failCount >= StudyConstants.PIN_FAIL_LIMIT) {
                lockUntil = new Date(now.getTime() + StudyConstants.PIN_LOCK_MINUTES * 60L * 1000L);
            }
            // 失败计数必须**独立提交**：下面紧接着要抛异常，
            // 若与这次写入处在同一事务里，计数会被一起回滚，
            // 「连续 5 次锁 10 分钟」就形同虚设（这是本实现踩过的坑）。
            updatePinFailState(child.getId(), failCount, lockUntil);
            if (lockUntil != null) {
                throw new JeecgBootException("登录已锁定，请稍后再试", 400);
            }
            throw new JeecgBootException("PIN 不正确", 400);
        }

        // 登录成功：失败计数归零、锁定时间清空
        updatePinFailState(child.getId(), 0, null);

        SysUser account = sysUserService.getById(child.getUserId());
        if (account == null) {
            throw new JeecgBootException("账号不存在", 400);
        }
        String token = JwtUtil.sign(account.getUsername(), account.getPassword(), CommonConstant.CLIENT_TYPE_APP);
        redisUtil.set(CommonConstant.PREFIX_USER_TOKEN + token, token);
        redisUtil.expire(CommonConstant.PREFIX_USER_TOKEN + token, JwtUtil.APP_EXPIRE_TIME * 2 / 1000);

        KidLoginVO vo = new KidLoginVO();
        vo.setToken(token);
        vo.setChildId(child.getId());
        vo.setChildCode(child.getChildCode());
        vo.setNickname(child.getNickname());
        vo.setAvatar(child.getAvatar());
        vo.setGradeCode(child.getGradeCode());
        vo.setParentNames(parentNames(child.getId()));
        return vo;
    }

    /**
     * 更新 PIN 失败计数与锁定时间。
     *
     * <p>用显式 UpdateWrapper 而不是 {@code updateById}：MyBatis-Plus 的
     * {@code updateById} 默认**不更新 null 字段**，锁定时间就永远清不掉；
     * 而登录成功后必须把「失败次数归零 + 锁定时间清空」一起落库。</p>
     *
     * <p>该方法<b>不加事务</b>：调用方随后要抛异常，
     * 若与失败计数写在同一事务里，计数会被回滚，锁定机制形同虚设。</p>
     *
     * @param childId   孩子 ID
     * @param failCount 失败次数
     * @param lockUntil 锁定截止时间；null 表示未锁定
     */
    private void updatePinFailState(String childId, int failCount, Date lockUntil) {
        LambdaUpdateWrapper<StudyChild> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(StudyChild::getId, childId)
                .set(StudyChild::getPinFailCount, failCount)
                .set(StudyChild::getPinLockUntil, lockUntil);
        childMapper.update(null, wrapper);
    }

    @Override
    public void logout(String token) {
        if (token == null || token.trim().isEmpty()) {
            return;
        }
        redisUtil.del(CommonConstant.PREFIX_USER_TOKEN + token.trim());
    }

    @Override
    public KidProfileVO me() {
        String childId = scopeService.ownChildId();
        if (childId == null) {
            throw new StudyForbiddenException();
        }
        StudyChild child = childMapper.selectById(childId);
        if (child == null) {
            throw new StudyForbiddenException();
        }
        KidProfileVO vo = new KidProfileVO();
        vo.setChildId(child.getId());
        vo.setChildCode(child.getChildCode());
        vo.setNickname(child.getNickname());
        vo.setAvatar(child.getAvatar());
        vo.setGradeCode(child.getGradeCode());
        vo.setParentNames(parentNames(child.getId()));
        return vo;
    }

    // ------------------------------------------------------------------ 内部

    /**
     * 孩子绑定的家长昵称列表（仅昵称，不含账号）。
     */
    private List<String> parentNames(String childId) {
        List<StudyParentChildRel> rels = relMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StudyParentChildRel>()
                        .eq(StudyParentChildRel::getChildId, childId)
                        .eq(StudyParentChildRel::getStatus, StudyConstants.STATUS_NORMAL));
        if (rels == null || rels.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<>();
        for (StudyParentChildRel rel : rels) {
            SysUser parent = sysUserService.getById(rel.getParentUserId());
            if (parent != null && parent.getRealname() != null) {
                names.add(parent.getRealname());
            }
        }
        return names;
    }

    private String pepper() {
        String pepper = studyProperties.getFamilyCodePepper();
        if (pepper == null || pepper.trim().isEmpty()) {
            throw new JeecgBootException("未配置家庭码 pepper（jeecg.study.family-code-pepper）", 500);
        }
        return pepper;
    }
}
