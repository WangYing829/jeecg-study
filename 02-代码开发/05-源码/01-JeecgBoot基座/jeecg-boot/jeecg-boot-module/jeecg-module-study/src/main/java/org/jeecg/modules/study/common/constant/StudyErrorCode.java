package org.jeecg.modules.study.common.constant;

/**
 * jeecg-study 业务错误码。
 *
 * <p>口径（《接口设计说明书》§2.2）：业务错误码一律返回 **HTTP 200 + code != 0**。
 * 表中的 HTTP 列只标注语义状态，**不代表真实 HTTP 状态码**。</p>
 *
 * <p>4010 的语义特别重要：越权一律返回 4010，**不用 404**。
 * 若越权返 404，攻击者可用「404 vs 200」区分资源是否存在（资源探测 / 枚举 childCode）；
 * 统一 4010 让「无权访问」与「资源不存在」的响应形态完全一致。</p>
 *
 * @author jeecg-study
 */
public final class StudyErrorCode {

    private StudyErrorCode() {
    }

    /** 4001 缺少错因 —— 提交练习未选错因 */
    public static final int MISSING_ERROR_CAUSE = 4001;

    /** 4002 前置步骤未完成 —— 写作未过提纲提交初稿 */
    public static final int PREREQUISITE_NOT_DONE = 4002;

    /** 4003 违反练习顺序 —— 未作答就请求解析（R-01 反搜题） */
    public static final int PRACTICE_ORDER_VIOLATION = 4003;

    /** 4004 上下文未配置 —— 首次使用未配置地区 / 教材 */
    public static final int CONTEXT_NOT_CONFIGURED = 4004;

    /** 4005 判定策略缺失 —— 该题型未注册 JudgeStrategy（R-02） */
    public static final int JUDGE_STRATEGY_MISSING = 4005;

    /** 4006 版权信息不完整 —— 导入非自编题目未填 source_ref */
    public static final int COPYRIGHT_INCOMPLETE = 4006;

    /** 4007 母题结构不完整 —— 母题五段缺任一段 */
    public static final int MOTHER_STRUCTURE_INCOMPLETE = 4007;

    /** 4008 变式不足 —— 过关要求 N 道变式但不足 */
    public static final int VARIANT_NOT_ENOUGH = 4008;

    /** 4009 家庭码不正确 —— 孩子端 POST /kid/lookup 家庭码校验失败 */
    public static final int FAMILY_CODE_INVALID = 4009;

    /** 4010 越权 —— 家长访问非关联孩子的数据 / 孩子访问他人数据 */
    public static final int FORBIDDEN_SCOPE = 4010;
}
