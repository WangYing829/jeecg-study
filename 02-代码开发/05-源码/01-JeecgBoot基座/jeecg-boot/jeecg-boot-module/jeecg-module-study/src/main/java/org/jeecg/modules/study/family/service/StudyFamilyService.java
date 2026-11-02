package org.jeecg.modules.study.family.service;

import java.util.List;

import org.jeecg.modules.study.family.vo.FamilyCreateVO;
import org.jeecg.modules.study.family.vo.FamilyDetailVO;
import org.jeecg.modules.study.family.vo.FamilyMemberVO;

/**
 * 家庭与家长成员管理（T-V0-14）。
 *
 * @author jeecg-study
 */
public interface StudyFamilyService {

    /**
     * 创建家庭；首个家长自动成为 OWNER。家庭码明文仅本次返回。
     *
     * @param familyName 家庭名称，空则用默认名
     * @return 创建结果
     */
    FamilyCreateVO create(String familyName);

    /**
     * 家庭详情（含成员清单）。
     *
     * @param familyId 家庭 ID，可空（空 = 当前登录者所在家庭）
     * @return 家庭详情
     */
    FamilyDetailVO detail(String familyId);

    /**
     * 修改家庭名称。
     *
     * @param familyName 新名称
     */
    void rename(String familyName);

    /**
     * 重置家庭码；明文**仅本次返回**，旧码立即失效。
     *
     * @return 新的家庭码明文
     */
    String resetFamilyCode();

    /**
     * 家庭成员列表。
     *
     * @param familyId 家庭 ID，可空（空 = 当前登录者所在家庭）
     * @return 成员列表
     */
    List<FamilyMemberVO> listMembers(String familyId);

    /**
     * 按用户名添加家长成员。
     *
     * @param username      底座账号用户名
     * @param relationLabel 关系标签
     */
    void addMember(String username, String relationLabel);

    /**
     * 移除家长成员。
     *
     * @param memberId 成员行 ID
     */
    void removeMember(String memberId);

    /**
     * 当前登录者所在的有效家庭 ID。
     *
     * @return 家庭 ID；不在任何家庭返回 null
     */
    String currentFamilyId();
}
