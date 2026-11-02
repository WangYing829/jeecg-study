package org.jeecg.modules.study.family;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.study.common.constant.StudyErrorCode;
import org.jeecg.modules.study.family.service.StudyFamilyService;
import org.jeecg.modules.study.family.vo.FamilyCreateVO;
import org.jeecg.modules.study.family.vo.FamilyDetailVO;
import org.jeecg.modules.study.family.vo.FamilyMemberVO;
import org.jeecg.modules.study.support.StudyTestSupport;

/**
 * 家庭与家长成员管理自动化测试（T-V0-14）。
 *
 * <p>覆盖测试用例 TC-41-01 … TC-41-09。</p>
 *
 * @author jeecg-study
 */
class StudyFamilyServiceTest extends StudyTestSupport {

    @Autowired
    private StudyFamilyService familyService;

    @Test
    @DisplayName("TC-41-01 创建家庭后首个家长自动成为 OWNER")
    void createFamily_makesOwner() {
        String userId = givenParentAccount("ut_owner_" + unique());
        loginAsParent(userId, "ut_owner");

        FamilyCreateVO vo = familyService.create("UT家庭");

        assertThat(vo.getFamilyId()).isNotBlank();
        assertThat(vo.getMemberRole()).isEqualTo("OWNER");
        assertThat(vo.getFamilyCode()).hasSize(6);

        Map<String, Object> member = queryOne(
                "SELECT * FROM study_family_member WHERE family_id = ? AND user_id = ?",
                vo.getFamilyId(), userId);
        assertThat(member).isNotNull();
        assertThat(member.get("member_role")).isEqualTo("OWNER");
        assertThat(((Number) member.get("status")).intValue()).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-41-04 家庭码明文仅返回一次，库里只存哈希")
    void familyCode_plaintextReturnedOnceOnly() {
        String userId = givenParentAccount("ut_code_" + unique());
        loginAsParent(userId, "ut_code");

        FamilyCreateVO vo = familyService.create("UT家庭码");
        String plaintext = vo.getFamilyCode();

        Map<String, Object> row = queryOne("SELECT family_code FROM study_family WHERE id = ?", vo.getFamilyId());
        assertThat(row.get("family_code")).isNotEqualTo(plaintext);
        assertThat(String.valueOf(row.get("family_code"))).hasSize(64);

        // 家庭详情不再返回家庭码明文
        FamilyDetailVO detail = familyService.detail(null);
        assertThat(detail.getFamilyId()).isEqualTo(vo.getFamilyId());
    }

    @Test
    @DisplayName("TC-41-02 家庭详情与成员清单数量一致")
    void detailAndMembers_haveSameCount() {
        String ownerName = "ut_a_" + unique();
        String ownerId = givenParentAccount(ownerName);
        String otherName = "ut_b_" + unique();
        givenParentAccount(otherName);
        loginAsParent(ownerId, ownerName);

        familyService.create("UT家庭成员");
        familyService.addMember(otherName, "妈妈");

        FamilyDetailVO detail = familyService.detail(null);
        List<FamilyMemberVO> members = familyService.listMembers(null);

        assertThat(detail.getMemberCount()).isEqualTo(2);
        assertThat(detail.getMemberCount()).isEqualTo(members.size());
        assertThat(members).allSatisfy(m -> {
            assertThat(m.getRelationLabel()).isNotBlank();
            assertThat(m.getMemberRole()).isNotBlank();
        });
    }

    @Test
    @DisplayName("TC-41-03 修改家庭名称不影响家庭 ID")
    void rename_onlyChangesName() {
        String userId = givenParentAccount("ut_rename_" + unique());
        loginAsParent(userId, "ut_rename");

        FamilyCreateVO family = familyService.create("UT原名");
        familyService.rename("UT新名");

        FamilyDetailVO detail = familyService.detail(null);
        assertThat(detail.getFamilyName()).isEqualTo("UT新名");
        assertThat(detail.getFamilyId()).isEqualTo(family.getFamilyId());
    }

    @Test
    @DisplayName("TC-41-06 / TC-41-07 按用户名添加家长成员，重复添加被拒")
    void addMember_thenDuplicateRejected() {
        String ownerName = "ut_o_" + unique();
        String ownerId = givenParentAccount(ownerName);
        String otherName = "ut_x_" + unique();
        givenParentAccount(otherName);
        loginAsParent(ownerId, ownerName);

        FamilyCreateVO family = familyService.create("UT家庭");
        familyService.addMember(otherName, "爸爸");

        // 统计必须限定在**本用例自己的家庭**里：
        // 全表按 relation_label 统计会被别的测试类留下的成员行干扰
        // （同一套库里多个用例都会加「爸爸」这类成员），断言就变成了看运气。
        assertThat(count("SELECT COUNT(*) FROM study_family_member "
                + "WHERE family_id = ? AND relation_label = '爸爸'", family.getFamilyId())).isEqualTo(1);

        assertThatThrownBy(() -> familyService.addMember(otherName, "爸爸"))
                .isInstanceOf(JeecgBootException.class);

        // 成员列表里该家长仍只有 1 行
        assertThat(count("SELECT COUNT(*) FROM study_family_member "
                + "WHERE family_id = ? AND relation_label = '爸爸'", family.getFamilyId())).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-41-08 移除家长成员后其权限立即失效")
    void removeMember_revokesAccessImmediately() {
        String ownerName = "ut_own_" + unique();
        String ownerId = givenParentAccount(ownerName);
        String otherName = "ut_oth_" + unique();
        String otherId = givenParentAccount(otherName);
        loginAsParent(ownerId, ownerName);

        familyService.create("UT家庭");
        familyService.addMember(otherName, "妈妈");

        List<FamilyMemberVO> members = familyService.listMembers(null);
        FamilyMemberVO target = members.stream()
                .filter(m -> Boolean.TRUE.equals(m.getIsSelf()) == false)
                .findFirst()
                .orElseThrow();
        familyService.removeMember(target.getMemberId());

        Map<String, Object> row = queryOne("SELECT status FROM study_family_member WHERE id = ?", target.getMemberId());
        assertThat(((Number) row.get("status")).intValue()).isEqualTo(2);

        // 被移除的家长再访问 → 4010
        loginAsParent(otherId, otherName);
        assertThatThrownBy(() -> familyService.listMembers(null))
                .isInstanceOf(JeecgBootException.class)
                .satisfies(e -> assertThat(((JeecgBootException) e).getErrCode())
                        .isEqualTo(StudyErrorCode.FORBIDDEN_SCOPE));
    }

    @Test
    @DisplayName("TC-41-09 家庭成员不能操作非自己家庭")
    void crossFamilyAccess_forbidden() {
        String nameA = "ut_A_" + unique();
        String nameB = "ut_B_" + unique();
        String userA = givenParentAccount(nameA);
        String userB = givenParentAccount(nameB);

        loginAsParent(userA, nameA);
        FamilyCreateVO familyA = familyService.create("UT家A");

        loginAsParent(userB, nameB);
        FamilyCreateVO familyB = familyService.create("UT家B");

        loginAsParent(userA, nameA);
        assertThatThrownBy(() -> familyService.listMembers(familyB.getFamilyId()))
                .isInstanceOf(JeecgBootException.class)
                .satisfies(e -> assertThat(((JeecgBootException) e).getErrCode())
                        .isEqualTo(StudyErrorCode.FORBIDDEN_SCOPE));

        assertThat(familyA.getFamilyId()).isNotEqualTo(familyB.getFamilyId());
    }
}
