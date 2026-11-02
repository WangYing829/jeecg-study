package org.jeecg.modules.study.kid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.study.common.constant.StudyErrorCode;
import org.jeecg.modules.study.family.service.StudyChildService;
import org.jeecg.modules.study.family.service.StudyFamilyService;
import org.jeecg.modules.study.family.vo.ChildCreateRequest;
import org.jeecg.modules.study.family.vo.ChildCreateVO;
import org.jeecg.modules.study.family.vo.FamilyCreateVO;
import org.jeecg.modules.study.kid.service.StudyKidAuthService;
import org.jeecg.modules.study.kid.vo.KidCandidateVO;
import org.jeecg.modules.study.kid.vo.KidLoginVO;
import org.jeecg.modules.study.kid.vo.KidProfileVO;
import org.jeecg.modules.study.support.StudyTestSupport;

/**
 * 孩子端两步登录 自动化测试（T-V0-15）。
 *
 * <p>覆盖 TC-44-01 … TC-44-11。</p>
 *
 * @author jeecg-study
 */
class StudyKidAuthServiceTest extends StudyTestSupport {

    @Autowired
    private StudyFamilyService familyService;

    @Autowired
    private StudyChildService childService;

    @Autowired
    private StudyKidAuthService kidAuthService;

    // ------------------------------------------------------------------ 工具

    private String familyCode;
    private String childId;
    private String childCode;
    private String kidUserId;

    private void givenFamilyWithTwoChildren() {
        String parentName = "ut_kid_" + unique();
        String parentId = givenParentAccount(parentName);
        loginAsParent(parentId, parentName);
        FamilyCreateVO family = familyService.create("UT家庭");
        familyCode = family.getFamilyCode();

        ChildCreateRequest first = new ChildCreateRequest();
        first.setNickname("UT孩子一");
        first.setPin("1234");
        ChildCreateVO created = childService.create(first);
        childId = created.getChildId();
        childCode = created.getChildCode();
        kidUserId = created.getUsername();

        ChildCreateRequest second = new ChildCreateRequest();
        second.setNickname("UT孩子二");
        second.setPin("5678");
        childService.create(second);
    }

    // ------------------------------------------------------------------ 测试

    @Test
    @DisplayName("TC-44-01 家庭码正确返回候选孩子列表")
    void lookup_returnsCandidates() {
        givenFamilyWithTwoChildren();

        List<KidCandidateVO> candidates = kidAuthService.lookup(familyCode);

        assertThat(candidates).hasSize(2);
        assertThat(candidates).extracting(KidCandidateVO::getNickname)
                .containsExactlyInAnyOrder("UT孩子一", "UT孩子二");
    }

    @Test
    @DisplayName("TC-44-03 候选列表只暴露 id / nickname / avatar 三个字段")
    void lookup_exposesOnlyThreeFields() {
        givenFamilyWithTwoChildren();

        List<String> fieldNames = Arrays.stream(KidCandidateVO.class.getDeclaredFields())
                .filter(f -> !java.lang.reflect.Modifier.isStatic(f.getModifiers()))
                .map(Field::getName).sorted().toList();

        assertThat(fieldNames).containsExactly("avatar", "id", "nickname");
        assertThat(fieldNames).noneMatch(n -> n.toLowerCase().contains("pin")
                || n.toLowerCase().contains("user")
                || n.toLowerCase().contains("phone")
                || n.toLowerCase().contains("birthday")
                || n.toLowerCase().contains("parent"));
    }

    @Test
    @DisplayName("TC-44-02 家庭码错误返回 4009")
    void lookup_wrongCode_4009() {
        givenFamilyWithTwoChildren();

        assertThatThrownBy(() -> kidAuthService.lookup("ZZZZZZ"))
                .isInstanceOf(JeecgBootException.class)
                .satisfies(e -> assertThat(((JeecgBootException) e).getErrCode())
                        .isEqualTo(StudyErrorCode.FAMILY_CODE_INVALID));
    }

    @Test
    @DisplayName("TC-44-04 PIN 正确登录成功并签发孩子 token")
    void login_successIssuesToken() {
        givenFamilyWithTwoChildren();

        KidLoginVO vo = kidAuthService.login(childId, "1234");

        assertThat(vo.getToken()).isNotBlank();
        assertThat(vo.getChildId()).isEqualTo(childId);
        assertThat(vo.getChildCode()).isEqualTo(childCode);
        assertThat(vo.getNickname()).isEqualTo("UT孩子一");
        verify(redisUtil).set(anyString(), anyString());
    }

    @Test
    @DisplayName("TC-44-05 PIN 错误登录失败并累计次数")
    void login_wrongPin_incrementsFailCount() {
        givenFamilyWithTwoChildren();

        assertThatThrownBy(() -> kidAuthService.login(childId, "9999"))
                .isInstanceOf(JeecgBootException.class);

        Map<String, Object> row = queryOne("SELECT pin_fail_count FROM study_child WHERE id = ?", childId);
        assertThat(((Number) row.get("pin_fail_count")).intValue()).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-44-06 / TC-44-07 连续失败 5 次锁定 10 分钟，锁定期正确 PIN 也被拒")
    void login_fiveFailures_locksTenMinutes() {
        givenFamilyWithTwoChildren();

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> kidAuthService.login(childId, "9999"))
                    .isInstanceOf(JeecgBootException.class);
        }

        Map<String, Object> row = queryOne(
                "SELECT pin_fail_count, pin_lock_until FROM study_child WHERE id = ?", childId);
        assertThat(((Number) row.get("pin_fail_count")).intValue()).isEqualTo(5);

        // MySQL 驱动对 datetime 列可能返回 java.util.Date，也可能返回 LocalTime，
        // 两种都要能取到时间戳
        Object lockRaw = row.get("pin_lock_until");
        long lockMs;
        if (lockRaw instanceof java.util.Date) {
            lockMs = ((java.util.Date) lockRaw).getTime();
        } else if (lockRaw instanceof java.time.LocalDateTime) {
            lockMs = ((java.time.LocalDateTime) lockRaw)
                    .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        } else {
            throw new AssertionError("pin_lock_until 类型不认识：" + lockRaw);
        }
        long deltaMin = (lockMs - System.currentTimeMillis()) / 60000L;
        assertThat(deltaMin).isBetween(8L, 10L);

        // 锁定期内正确 PIN 也被拒
        assertThatThrownBy(() -> kidAuthService.login(childId, "1234"))
                .isInstanceOf(JeecgBootException.class)
                .satisfies(e -> assertThat(e.getMessage()).contains("锁定"));
    }

    @Test
    @DisplayName("TC-44-09 孩子 token 只能读自己的数据")
    void me_returnsOwnChild() {
        givenFamilyWithTwoChildren();
        // 以孩子身份登录：study_child.user_id 指向孩子的 sys_user.id
        String kidAccountId = (String) queryOne("SELECT user_id FROM study_child WHERE id = ?", childId).get("user_id");
        loginAsKid((String) kidAccountId, "UT孩子一");

        KidProfileVO me = kidAuthService.me();

        assertThat(me.getChildId()).isEqualTo(childId);
        assertThat(me.getChildCode()).isEqualTo(childCode);
        assertThat(me.getNickname()).isEqualTo("UT孩子一");
    }

    @Test
    @DisplayName("TC-44-11 登出后 token 立即失效")
    void logout_removesToken() {
        givenFamilyWithTwoChildren();
        KidLoginVO vo = kidAuthService.login(childId, "1234");

        kidAuthService.logout(vo.getToken());

        verify(redisUtil).del(CommonConstant.PREFIX_USER_TOKEN + vo.getToken());
    }
}
