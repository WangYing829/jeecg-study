package org.jeecg.modules.study.family;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.study.common.constant.StudyErrorCode;
import org.jeecg.modules.study.family.service.StudyChildService;
import org.jeecg.modules.study.family.service.StudyFamilyService;
import org.jeecg.modules.study.family.vo.ChildCreateRequest;
import org.jeecg.modules.study.family.vo.ChildCreateVO;
import org.jeecg.modules.study.family.vo.ChildUpdateRequest;
import org.jeecg.modules.study.family.vo.ChildVO;
import org.jeecg.modules.study.family.vo.RelationVO;
import org.jeecg.modules.study.kid.service.StudyKidAuthService;
import org.jeecg.modules.study.support.StudyTestSupport;

/**
 * 孩子档案 + 家长↔孩子多对多关联 自动化测试（T-V0-15 / T-V0-16）。
 *
 * <p>覆盖 TC-42-01 … TC-42-13、TC-43-01 … TC-43-13 中可在服务层验证的部分。</p>
 *
 * @author jeecg-study
 */
class StudyChildServiceTest extends StudyTestSupport {

    @Autowired
    private StudyFamilyService familyService;

    @Autowired
    private StudyChildService childService;

    @Autowired
    private StudyKidAuthService kidAuthService;

    // ------------------------------------------------------------------ 工具

    /**
     * 造一个「家长 + 家庭 + 孩子」，返回孩子档案；同时记住家长账号名与 ID。
     */
    private ChildCreateVO givenChild(String nickname) {
        String parentName = "ut_p_" + unique();
        String parentId = givenParentAccount(parentName);
        lastParentName = parentName;
        lastParentId = parentId;
        loginAsParent(parentId, parentName);
        familyService.create("UT家庭");
        ChildCreateRequest request = new ChildCreateRequest();
        request.setNickname(nickname);
        request.setGradeCode("G3");
        request.setPin("1234");
        request.setRelationLabel("妈妈");
        return childService.create(request);
    }

    // ------------------------------------------------------------------ TC-42

    @Test
    @DisplayName("TC-42-01 新增孩子档案并创建登录账号")
    void create_childWithSysUserAndPinHash() {
        ChildCreateVO child = givenChild("UT小明");

        assertThat(child.getChildCode()).matches("CHD-\\d{6}");
        assertThat(child.getUsername()).isNotBlank();

        Map<String, Object> row = queryOne("SELECT * FROM study_child WHERE id = ?", child.getChildId());
        assertThat(row).isNotNull();
        assertThat(row.get("pin_hash")).isNotEqualTo("1234");
        assertThat(String.valueOf(row.get("pin_hash"))).hasSize(64);
        assertThat(row.get("family_id")).isNotNull();

        // 创建者自动成为主家长
        Map<String, Object> rel = queryOne(
                "SELECT * FROM study_parent_child_rel WHERE child_id = ? AND status = 1", child.getChildId());
        assertThat(rel).isNotNull();
        assertThat(((Number) rel.get("is_primary")).intValue()).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-42-02 我关联的孩子列表只含关联孩子")
    void list_onlyRelatedChildren() {
        String parentName = "ut_A_" + unique();
        String parentA = givenParentAccount(parentName);
        loginAsParent(parentA, parentName);
        familyService.create("UT家庭A");
        ChildCreateRequest req = new ChildCreateRequest();
        req.setNickname("UT孩子A");
        req.setPin("1234");
        childService.create(req);

        // 另一个家长的孩子
        String parentNameB = "ut_B_" + unique();
        String parentB = givenParentAccount(parentNameB);
        loginAsParent(parentB, parentNameB);
        familyService.create("UT家庭B");
        ChildCreateRequest reqB = new ChildCreateRequest();
        reqB.setNickname("UT孩子B");
        reqB.setPin("1234");
        childService.create(reqB);

        loginAsParent(parentA, parentName);
        List<ChildVO> children = childService.listChildren();

        assertThat(children).hasSize(1);
        assertThat(children.get(0).getNickname()).isEqualTo("UT孩子A");
    }

    @Test
    @DisplayName("TC-42-03 档案详情字段完整且不含 PIN 信息")
    void detail_hasNoPinFields() {
        ChildCreateVO child = givenChild("UT小红");

        ChildVO detail = childService.detail(child.getChildCode());

        assertThat(detail.getChildCode()).isEqualTo(child.getChildCode());
        assertThat(detail.getNickname()).isEqualTo("UT小红");
        assertThat(detail.getGradeCode()).isEqualTo("G3");
        assertThat(detail.getAvatar()).isNull();
        assertThat(detail.getGender()).isNotNull();
        assertThat(detail.getBirthday()).isNull();

        // 视图里绝不能有 PIN 相关字段
        List<String> fieldNames = Arrays.stream(ChildVO.class.getDeclaredFields())
                .map(Field::getName).map(n -> n.toLowerCase()).toList();
        assertThat(fieldNames).noneMatch(n -> n.contains("pinhash") || n.contains("pinsalt")
                || n.contains("pinfail") || n.contains("pinlock"));
    }

    @Test
    @DisplayName("TC-42-04 更新孩子档案生效")
    void update_appliesFields() {
        ChildCreateVO child = givenChild("UT小刚");

        ChildUpdateRequest request = new ChildUpdateRequest();
        request.setNickname("UT小刚改");
        request.setAvatar("avatar-1");
        request.setGradeCode("G4");
        childService.update(child.getChildCode(), request);

        ChildVO detail = childService.detail(child.getChildCode());
        assertThat(detail.getNickname()).isEqualTo("UT小刚改");
        assertThat(detail.getAvatar()).isEqualTo("avatar-1");
        assertThat(detail.getGradeCode()).isEqualTo("G4");
        assertThat(detail.getChildCode()).isEqualTo(child.getChildCode());
        assertThat(detail.getStatus()).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-42-05 重复 user_id 被拒")
    void duplicateUserId_rejected() {
        String parentName = "ut_dup_" + unique();
        String parentId = givenParentAccount(parentName);
        loginAsParent(parentId, parentName);
        familyService.create("UT家庭");

        String existingUserId = givenParentAccount("ut_exist_" + unique());

        ChildCreateRequest first = new ChildCreateRequest();
        first.setNickname("UT绑定一");
        first.setPin("1234");
        first.setUserId(existingUserId);
        childService.create(first);

        long before = count("SELECT COUNT(*) FROM study_child WHERE user_id = ?", existingUserId);

        ChildCreateRequest second = new ChildCreateRequest();
        second.setNickname("UT绑定二");
        second.setPin("1234");
        second.setUserId(existingUserId);
        assertThatThrownBy(() -> childService.create(second)).isInstanceOf(JeecgBootException.class);

        long after = count("SELECT COUNT(*) FROM study_child WHERE user_id = ?", existingUserId);
        assertThat(after).isEqualTo(before);
    }

    @Test
    @DisplayName("TC-42-06 停用后孩子不可登录")
    void disable_thenCannotLogin() {
        ChildCreateVO child = givenChild("UT停用");

        childService.disable(child.getChildCode());

        Map<String, Object> row = queryOne("SELECT status FROM study_child WHERE id = ?", child.getChildId());
        assertThat(((Number) row.get("status")).intValue()).isEqualTo(2);

        assertThatThrownBy(() -> kidAuthService.login(child.getChildId(), "1234"))
                .isInstanceOf(JeecgBootException.class)
                .satisfies(e -> assertThat(e.getMessage()).contains("停用"));
    }

    @Test
    @DisplayName("TC-42-08 重置孩子 PIN 后旧 PIN 失效")
    void resetPin_oldPinInvalid() {
        ChildCreateVO child = givenChild("UT改PIN");

        String newPin = childService.resetPin(child.getChildCode());
        assertThat(newPin).matches("[0-9]{4,6}");

        assertThatThrownBy(() -> kidAuthService.login(child.getChildId(), "1234"))
                .isInstanceOf(JeecgBootException.class);
        assertThat(kidAuthService.login(child.getChildId(), newPin).getToken()).isNotBlank();
    }

    @Test
    @DisplayName("TC-42-09 / TC-42-11 家长 A 访问孩子 B → 4010")
    void crossParentAccess_forbidden() {
        String nameA = "ut_pa_" + unique();
        String userA = givenParentAccount(nameA);
        loginAsParent(userA, nameA);
        familyService.create("UT家A");
        ChildCreateRequest reqA = new ChildCreateRequest();
        reqA.setNickname("UT甲");
        reqA.setPin("1234");
        ChildCreateVO childA = childService.create(reqA);

        String nameB = "ut_pb_" + unique();
        String userB = givenParentAccount(nameB);
        loginAsParent(userB, nameB);
        familyService.create("UT家B");
        ChildCreateRequest reqB = new ChildCreateRequest();
        reqB.setNickname("UT乙");
        reqB.setPin("1234");
        ChildCreateVO childB = childService.create(reqB);

        loginAsParent(userA, nameA);
        assertThatThrownBy(() -> childService.detail(childB.getChildCode()))
                .isInstanceOf(JeecgBootException.class)
                .satisfies(e -> assertThat(((JeecgBootException) e).getErrCode())
                        .isEqualTo(StudyErrorCode.FORBIDDEN_SCOPE));
        assertThatThrownBy(() -> childService.overview(childB.getChildCode()))
                .isInstanceOf(JeecgBootException.class);
        assertThatThrownBy(() -> childService.listRelations(childB.getChildCode()))
                .isInstanceOf(JeecgBootException.class);

        // 家长 A 自己的孩子仍可访问
        assertThat(childService.detail(childA.getChildCode()).getNickname()).isEqualTo("UT甲");
    }

    @Test
    @DisplayName("TC-42-12 查询不存在的 childCode 返回 4010")
    void unknownChildCode_forbidden() {
        givenChild("UT存在的");

        assertThatThrownBy(() -> childService.detail("CHD-999999"))
                .isInstanceOf(JeecgBootException.class)
                .satisfies(e -> assertThat(((JeecgBootException) e).getErrCode())
                        .isEqualTo(StudyErrorCode.FORBIDDEN_SCOPE));
    }

    @Test
    @DisplayName("TC-42-13 家长 A 改写或停用孩子 B → 4010")
    void crossParentWrite_forbidden() {
        String nameA = "ut_wa_" + unique();
        String userA = givenParentAccount(nameA);
        loginAsParent(userA, nameA);
        familyService.create("UT家A");

        String nameB = "ut_wb_" + unique();
        String userB = givenParentAccount(nameB);
        loginAsParent(userB, nameB);
        familyService.create("UT家B");
        ChildCreateRequest reqB = new ChildCreateRequest();
        reqB.setNickname("UT乙");
        reqB.setPin("1234");
        ChildCreateVO childB = childService.create(reqB);

        loginAsParent(userA, nameA);
        ChildUpdateRequest update = new ChildUpdateRequest();
        update.setNickname("UT被改");
        assertThatThrownBy(() -> childService.update(childB.getChildCode(), update))
                .isInstanceOf(JeecgBootException.class);
        assertThatThrownBy(() -> childService.disable(childB.getChildCode()))
                .isInstanceOf(JeecgBootException.class);

        // 孩子 B 的档案未被改动
        Map<String, Object> row = queryOne("SELECT nickname, status FROM study_child WHERE id = ?", childB.getChildId());
        assertThat(row.get("nickname")).isEqualTo("UT乙");
        assertThat(((Number) row.get("status")).intValue()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ TC-43

    @Test
    @DisplayName("TC-43-01 / TC-43-03 一家长关联多个孩子并记录关系标签")
    void relations_addMultiple() {
        String parentName = "ut_rm_" + unique();
        String parentId = givenParentAccount(parentName);
        loginAsParent(parentId, parentName);
        familyService.create("UT家庭");

        ChildCreateRequest req = new ChildCreateRequest();
        req.setNickname("UT一");
        req.setPin("1234");
        ChildCreateVO first = childService.create(req);
        req.setNickname("UT二");
        ChildCreateVO second = childService.create(req);

        List<RelationVO> relations = childService.listRelations(first.getChildCode());
        assertThat(relations).hasSize(1);
        assertThat(relations.get(0).getIsPrimary()).isTrue();
        assertThat(relations.get(0).getIsSelf()).isTrue();

        assertThat(childService.listChildren()).hasSize(2);
        assertThat(childService.detail(second.getChildCode()).getNickname()).isEqualTo("UT二");
    }

    @Test
    @DisplayName("TC-43-04 重复关联同一家长与孩子被拒")
    void relations_duplicateRejected() {
        ChildCreateVO child = givenChild("UT重复关联");

        assertThatThrownBy(() -> childService.addRelation(child.getChildCode(),
                currentUserIdOfChildOwner(), "妈妈", true))
                .isInstanceOf(JeecgBootException.class);

        assertThat(childService.listRelations(child.getChildCode())).hasSize(1);
    }

    @Test
    @DisplayName("TC-43-05 解除关联后家长立即看不到该孩子")
    void relations_removeRevokesAccess() {
        ChildCreateVO child = givenChild("UT解除关联");

        childService.removeRelation(child.getChildCode(), currentUserIdOfChildOwner());

        assertThat(childService.listChildren()).isEmpty();
        assertThatThrownBy(() -> childService.overview(child.getChildCode()))
                .isInstanceOf(JeecgBootException.class)
                .satisfies(e -> assertThat(((JeecgBootException) e).getErrCode())
                        .isEqualTo(StudyErrorCode.FORBIDDEN_SCOPE));

        Map<String, Object> row = queryOne(
                "SELECT status FROM study_parent_child_rel WHERE child_id = ?", child.getChildId());
        assertThat(((Number) row.get("status")).intValue()).isEqualTo(2);
    }

    @Test
    @DisplayName("TC-43-07 仅主家长可重置 PIN 与停用")
    void relations_onlyPrimaryCanManage() {
        ChildCreateVO child = givenChild("UT主家长");

        // 第二个家长加入同一家庭并关联该孩子（非主家长）
        String secondName = "ut_second_" + unique();
        String secondId = givenParentAccount(secondName);
        String primaryUserId = currentUserIdOfChildOwner();
        String primaryName = currentParentName();

        // 加人 / 建关联必须由主家长发起
        loginAsParent(primaryUserId, primaryName);
        familyService.addMember(secondName, "爸爸");
        childService.addRelation(child.getChildCode(), secondId, "爸爸", false);

        loginAsParent(secondId, secondName);
        assertThatThrownBy(() -> childService.resetPin(child.getChildCode()))
                .isInstanceOf(JeecgBootException.class)
                .satisfies(e -> assertThat(((JeecgBootException) e).getErrCode())
                        .isEqualTo(StudyErrorCode.FORBIDDEN_SCOPE));
        assertThatThrownBy(() -> childService.disable(child.getChildCode()))
                .isInstanceOf(JeecgBootException.class);

        // 主家长仍可操作
        loginAsParent(primaryUserId, primaryName);
        assertThat(childService.resetPin(child.getChildCode())).isNotBlank();
    }

    @Test
    @DisplayName("TC-43-13 解除关联只影响该家长")
    void relations_removeOnlyAffectsSelf() {
        String nameA = "ut_ra_" + unique();
        String userA = givenParentAccount(nameA);
        loginAsParent(userA, nameA);
        familyService.create("UT家庭");
        ChildCreateRequest req = new ChildCreateRequest();
        req.setNickname("UT共用");
        req.setPin("1234");
        ChildCreateVO child = childService.create(req);

        // 家长 B 加入家庭并关联同一孩子
        String nameB = "ut_rb_" + unique();
        String userB = givenParentAccount(nameB);
        loginAsParent(userA, nameA);
        familyService.addMember(nameB, "爸爸");
        childService.addRelation(child.getChildCode(), userB, "爸爸", false);

        // 家长 A 解除自己的关联
        childService.removeRelation(child.getChildCode(), userA);

        // 家长 B 仍能看到孩子
        loginAsParent(userB, nameB);
        assertThat(childService.listChildren()).hasSize(1);
        assertThat(childService.overview(child.getChildCode())).isNotNull();

        // 家长 A 的关系行状态为 2
        Map<String, Object> row = queryOne(
                "SELECT status FROM study_parent_child_rel WHERE child_id = ? AND parent_user_id = ?",
                child.getChildId(), userA);
        assertThat(((Number) row.get("status")).intValue()).isEqualTo(2);
    }

    // ------------------------------------------------------------------ 内部

    private String currentParentName() {
        return lastParentName;
    }

    private String currentUserIdOfChildOwner() {
        return lastParentId;
    }

    private String lastParentName;
    private String lastParentId;
}
