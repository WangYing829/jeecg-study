package org.jeecg.modules.study.common.constant;

/**
 * jeecg-study 全局常量。
 *
 * @author jeecg-study
 */
public final class StudyConstants {

    private StudyConstants() {
    }

    /** 角色码：家长 */
    public static final String ROLE_PARENT = "study_parent";

    /** 角色码：孩子 */
    public static final String ROLE_KID = "study_kid";

    /** 权限码前缀：家长端 */
    public static final String PERM_PARENT_PREFIX = "study:parent:";

    /** 权限码前缀：孩子端 */
    public static final String PERM_KID_PREFIX = "study:kid:";

    /** 家庭成员角色：创建者 */
    public static final String MEMBER_ROLE_OWNER = "OWNER";

    /** 家庭成员角色：家长 */
    public static final String MEMBER_ROLE_PARENT = "PARENT";

    /** 状态：正常 */
    public static final int STATUS_NORMAL = 1;

    /** 状态：冻结 / 停用 / 已退出 / 已解除 */
    public static final int STATUS_DISABLED = 2;

    /** 关系标签：妈妈 */
    public static final String RELATION_MOTHER = "妈妈";

    /** 关系标签：爸爸 */
    public static final String RELATION_FATHER = "爸爸";

    /** 关系标签：爷爷 */
    public static final String RELATION_GRANDFATHER = "爷爷";

    /** 关系标签：奶奶 */
    public static final String RELATION_GRANDMOTHER = "奶奶";

    /** 关系标签：其他 */
    public static final String RELATION_OTHER = "其他";

    /** 允许的关系标签白名单（前端下拉与后端校验共用） */
    public static final String[] RELATION_LABELS = {
            RELATION_MOTHER, RELATION_FATHER, RELATION_GRANDFATHER, RELATION_GRANDMOTHER, RELATION_OTHER
    };

    /** 家庭码长度 */
    public static final int FAMILY_CODE_LENGTH = 6;

    /** PIN 最小长度 */
    public static final int PIN_MIN_LENGTH = 4;

    /** PIN 最大长度 */
    public static final int PIN_MAX_LENGTH = 6;

    /** PIN 连续失败多少次锁定 */
    public static final int PIN_FAIL_LIMIT = 5;

    /** PIN 锁定时长（分钟） */
    public static final int PIN_LOCK_MINUTES = 10;

    /** 孩子业务ID 前缀 */
    public static final String CHILD_CODE_PREFIX = "CHD-";
}
