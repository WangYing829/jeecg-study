package org.jeecg.modules.study.common.enums;

/**
 * 判定来源（R-02 AI 只做解释层）。
 *
 * <p>**类型层面只允许两个值**，不允许出现 {@code model}。
 * 判定必须由确定性规则给出；AI 只能参与「解释 / 讲解」，
 * 因此最多出现 {@code rule+model}——含义是「判定来自规则，讲解有模型参与」。</p>
 *
 * <p>红线断言 R02-1（ArchUnit）：判定包不得依赖任何 AI 客户端包；
 * R02-4（DB CHECK）：{@code judge_source} 不得为 {@code model}。</p>
 *
 * @author jeecg-study
 */
public enum JudgeSource {

    /** 纯规则判定 */
    RULE("rule"),

    /** 规则判定 + 模型只做解释 */
    RULE_AND_MODEL("rule+model");

    private final String code;

    JudgeSource(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /**
     * 宽松解析：只接受两个合法值，其余一律判为非法。
     *
     * @param code 原始值
     * @return 合法枚举；非法返回 null
     */
    public static JudgeSource of(String code) {
        if (code == null) {
            return null;
        }
        for (JudgeSource v : values()) {
            if (v.code.equalsIgnoreCase(code)) {
                return v;
            }
        }
        return null;
    }

    public static boolean isValid(String code) {
        return of(code) != null;
    }
}
