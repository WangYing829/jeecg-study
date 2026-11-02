package org.jeecg.modules.study.scope.service;

import java.util.List;

/**
 * 数据范围服务（ADR-018 / 契约 §3）。
 *
 * <p>唯一维度是 {@code child_id}：</p>
 * <ul>
 *   <li><b>家长</b>：可见孩子 = {@code study_parent_child_rel} 中与自己生效关联、
 *       且孩子属于自己所在家庭的孩子；</li>
 *   <li><b>孩子</b>：可见孩子 = 自己（按 {@code study_child.user_id} 实时反查）；</li>
 *   <li><b>管理员</b>：本服务不做特殊放行，管理员走底座权限码。</li>
 * </ul>
 *
 * <p><b>越权一律抛 4010</b>（{@code StudyForbiddenException}），不用 404 ——
 * 避免用「404 vs 200」区分资源是否存在（资源探测）。</p>
 *
 * @author jeecg-study
 */
public interface ChildScopeService {

    /**
     * 当前登录用户 ID。未登录抛 401。
     *
     * @return sys_user.id
     */
    String currentUserId();

    /**
     * 当前登录用户的角色码集合。
     *
     * @return 角色码；未登录返回空集合
     */
    List<String> currentRoles();

    /**
     * 当前用户可见的孩子 ID 集合。
     *
     * <p>家长没有生效家庭成员关系时抛 4010（成员被移除后权限立即失效）。</p>
     *
     * @return 孩子 ID 列表
     */
    List<String> visibleChildIds();

    /**
     * 当前用户可见的孩子业务ID（CHD-xxx）集合。
     *
     * @return 业务ID 列表
     */
    List<String> visibleChildCodes();

    /**
     * 当前用户作为孩子身份时自己的孩子 ID。
     *
     * @return 孩子 ID；当前用户不是孩子身份返回 null
     */
    String ownChildId();

    /**
     * 断言当前用户有权访问指定孩子，否则抛 4010。
     *
     * @param childId 孩子 ID
     */
    void assertChildAccess(String childId);

    /**
     * 断言当前用户有权访问指定孩子业务ID，否则抛 4010。
     *
     * <p>业务ID 不存在时同样抛 4010 —— 响应形态与「存在但无权」完全一致，
     * 不泄露 childCode 是否存在。</p>
     *
     * @param childCode 孩子业务ID
     */
    void assertChildCodeAccess(String childCode);
}
