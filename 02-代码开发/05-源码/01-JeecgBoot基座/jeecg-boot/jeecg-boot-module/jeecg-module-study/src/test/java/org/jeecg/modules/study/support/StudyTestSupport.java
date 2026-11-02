package org.jeecg.modules.study.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;

import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.util.RedisUtil;
import org.jeecg.modules.system.entity.SysRole;
import org.jeecg.modules.system.entity.SysUser;
import org.jeecg.modules.system.entity.SysUserRole;
import org.jeecg.modules.system.service.ISysRoleService;
import org.jeecg.modules.system.service.ISysUserRoleService;
import org.jeecg.modules.system.service.ISysUserService;
import org.jeecg.modules.study.StudyTestApplication;

/**
 * 自动化测试基类。
 *
 * <p><b>测试跑在真实的 {@code jeecg-study} 库上</b>——唯一约束、幂等、
 * child_id 隔离这类行为只有在真实 MySQL 上才验得准；纯逻辑仍可用 mock。</p>
 *
 * <p>底座能力（用户 / 角色 / Redis）在测试里用 Mock 顶替，
 * 只保留本模块真正依赖的最小行为：</p>
 * <ul>
 *   <li>{@code ISysUserService}：按用户名 / ID 查账号、创建账号；</li>
 *   <li>{@code ISysRoleService}：按角色码查角色；</li>
 *   <li>{@code ISysUserRoleService}：记录用户角色关系；</li>
 *   <li>{@code RedisUtil}：记录 token 写入与删除。</li>
 * </ul>
 *
 * <p>登录身份通过往 Shiro {@code ThreadContext} 绑一个 Mock Subject 模拟，
 * 这样 {@code SecurityUtils.getSubject().getPrincipal()} 能拿到
 * {@link LoginUser}，与底座真实运行时的取值路径一致。</p>
 *
 * @author jeecg-study
 */
@SpringBootTest(classes = StudyTestApplication.class)
@ActiveProfiles("test")
public abstract class StudyTestSupport {

    /** 家长角色 ID（测试内固定） */
    protected static final String ROLE_PARENT_ID = "ut_role_parent";

    /** 孩子角色 ID（测试内固定） */
    protected static final String ROLE_KID_ID = "ut_role_kid";

    @Autowired
    protected DataSource dataSource;

    @Autowired
    protected JdbcTemplate jdbc;

    @MockBean
    protected ISysUserService sysUserService;

    @MockBean
    protected ISysRoleService sysRoleService;

    @MockBean
    protected ISysUserRoleService sysUserRoleService;

    @MockBean
    protected RedisUtil redisUtil;

    /** 测试内创建的 sys_user，键为用户名 */
    private final List<SysUser> createdUsers = new ArrayList<>();

    /** 测试内创建的用户角色关系 */
    private final List<String[]> createdUserRoles = new ArrayList<>();

    @BeforeEach
    void prepareMocks() {
        when(sysRoleService.getRoleNoTenant("study_parent")).thenReturn(role(ROLE_PARENT_ID, "study_parent"));
        when(sysRoleService.getRoleNoTenant("study_kid")).thenReturn(role(ROLE_KID_ID, "study_kid"));
        // IService 的 lambdaQuery()/list() 是默认方法，Mockito 会直接返回 null，
        // 因此业务代码统一走 getBaseMapper()，这里顶一个空实现
        BaseMapper<SysUserRole> userRoleMapper = mock(BaseMapper.class);
        when(userRoleMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(userRoleMapper.selectCount(any())).thenReturn(0L);
        when(sysUserRoleService.getBaseMapper()).thenReturn(userRoleMapper);
        doAnswer(invocation -> {
            SysUser user = invocation.getArgument(0);
            String roles = invocation.getArgument(1);
            if (user.getId() == null) {
                user.setId(IdWorker.get32UUID());
            }
            createdUsers.add(user);
            for (String roleId : roles.split(",")) {
                createdUserRoles.add(new String[]{user.getId(), roleId});
            }
            return null;
        }).when(sysUserService).addUserWithRole(any(SysUser.class), anyString());        when(sysUserService.getById(anyString())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            for (SysUser user : createdUsers) {
                if (id != null && id.equals(user.getId())) {
                    return user;
                }
            }
            return null;
        });
        when(sysUserService.getUserByName(anyString())).thenAnswer(invocation -> {
            String username = invocation.getArgument(0);
            for (SysUser user : createdUsers) {
                if (username != null && username.equals(user.getUsername())) {
                    return user;
                }
            }
            return null;
        });
    }

    @AfterEach
    void clearSubject() {
        ThreadContext.remove();
        cleanTestData();
    }

    /**
     * 清理测试数据。约定：测试造的家庭名 / 孩子昵称一律以 {@code UT} 开头。
     *
     * <p><b>必须先删行为表、再删孩子</b>：这些行为表都挂 {@code child_id}，
     * 只删 {@code study_child} 会留下一堆**孤儿行**（孩子没了、排期还在），
     * 库里越攒越多，也让「今天有没有残留」这件事没法自证。</p>
     */
    protected void cleanTestData() {
        try {
            // 行为表（挂 child_id）：必须在删孩子之前清掉
            for (String table : new String[]{
                    "study_review_schedule", "study_error_pool", "study_writing",
                    "study_eye_usage", "study_context", "study_question_variant"}) {
                deleteByUtChild(table);
            }
            // 练习记录是分区表，表名带月份后缀，按实际存在的分区逐个清
            for (String table : practiceRecordTables()) {
                deleteByUtChild(table);
            }
            jdbc.update("DELETE FROM study_parent_child_rel WHERE child_id IN "
                    + "(SELECT id FROM study_child WHERE nickname LIKE 'UT%')");
            jdbc.update("DELETE FROM study_child WHERE nickname LIKE 'UT%'");
            jdbc.update("DELETE FROM study_family_member WHERE family_id IN "
                    + "(SELECT id FROM study_family WHERE family_name LIKE 'UT%')");
            jdbc.update("DELETE FROM study_family WHERE family_name LIKE 'UT%'");
        } catch (Exception e) {
            // 清理失败不影响断言结果，下一轮仍按唯一命名隔离
            System.out.println("[StudyTestSupport] 清理测试数据失败：" + e.getMessage());
        }
    }

    /**
     * 删除指定行为表里属于「UT 开头」孩子的行。
     *
     * @param table 表名
     */
    private void deleteByUtChild(String table) {
        jdbc.update("DELETE FROM " + table + " WHERE child_id IN "
                + "(SELECT id FROM study_child WHERE nickname LIKE 'UT%')");
    }

    /**
     * 列出当前库里存在的练习记录分区表。
     *
     * <p><b>断言里禁止写死具体月份</b>：生产侧按服务端时钟拼表名
     * （{@code study_practice_record_yyyyMM}，见 {@code StudyPracticeServiceImpl#practiceRecordTable}），
     * 跨月当天写死的表名就查不到行。需要查作答记录时用本方法拿到真实存在的分表。</p>
     *
     * @return 表名列表（如 {@code study_practice_record_202610}）
     */
    protected List<String> practiceRecordTables() {
        List<String> tables = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT TABLE_NAME AS t FROM information_schema.TABLES "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME LIKE 'study_practice_record%'");
            for (Map<String, Object> row : rows) {
                Object name = row.get("t");
                if (name != null) {
                    tables.add(String.valueOf(name));
                }
            }
        } catch (Exception e) {
            // 查不到就跳过，清理失败不影响断言
            System.out.println("[StudyTestSupport] 查练习记录分区失败：" + e.getMessage());
        }
        return tables;
    }

    // ------------------------------------------------------------------ 身份

    /**
     * 以某个家长身份登录。
     *
     * @param userId sys_user.id
     * @param name   显示名
     */
    protected void loginAsParent(String userId, String name) {
        bindPrincipal(userId, name, "study_parent");
    }

    /**
     * 以某个孩子身份登录。
     *
     * @param userId sys_user.id
     * @param name   显示名
     */
    protected void loginAsKid(String userId, String name) {
        bindPrincipal(userId, name, "study_kid");
    }

    protected void bindPrincipal(String userId, String username, String roleCode) {
        LoginUser loginUser = new LoginUser();
        loginUser.setId(userId);
        loginUser.setUsername(username);
        loginUser.setRealname(username);
        loginUser.setRoleCode(roleCode);
        Subject subject = mock(Subject.class);
        when(subject.getPrincipal()).thenReturn(loginUser);
        when(subject.isAuthenticated()).thenReturn(true);
        ThreadContext.bind(subject);
    }

    // ------------------------------------------------------------------ 数据

    /**
     * 造一个已存在的家长账号（同时登记到 mock 的用户表）。
     *
     * @param name 账号名
     * @return sys_user.id
     */
    protected String givenParentAccount(String name) {
        SysUser user = new SysUser();
        user.setId(IdWorker.get32UUID());
        user.setUsername(name);
        user.setRealname(name);
        user.setStatus(1);
        user.setDelFlag(0);
        createdUsers.add(user);
        return user.getId();
    }

    /**
     * 查询单行。
     */
    protected java.util.Map<String, Object> queryOne(String sql, Object... args) {
        List<java.util.Map<String, Object>> rows = jdbc.queryForList(sql, args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 查询多行。
     */
    protected List<java.util.Map<String, Object>> queryList(String sql, Object... args) {
        return jdbc.queryForList(sql, args);
    }

    /**
     * 统计行数。
     */
    protected int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    /**
     * 造一个唯一后缀，避免测试之间撞 ID。
     */
    protected String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    protected Date now() {
        return new Date();
    }

    private SysRole role(String id, String code) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setRoleCode(code);
        role.setRoleName(code);
        return role;
    }
}
