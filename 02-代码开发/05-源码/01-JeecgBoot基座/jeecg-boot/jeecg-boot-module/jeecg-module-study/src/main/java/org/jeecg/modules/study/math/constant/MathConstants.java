package org.jeecg.modules.study.math.constant;

import java.math.BigDecimal;

/**
 * 数学母题域常量。
 *
 * <p><b>红线相关</b>：</p>
 * <ul>
 *   <li>R-02 —— 变式生成方式只允许 {@link #GENERATED_BY_RULE} 与 {@link #GENERATED_BY_RULE_MODEL}，
 *       <b>不允许出现纯 MODEL</b>；变式答案一律由确定性规则算出。</li>
 *   <li>R-01 —— 变式答案只在服务端参与判定，<b>任何响应体都不下发答案字段</b>。</li>
 * </ul>
 *
 * @author jeecg-study
 */
public final class MathConstants {

    private MathConstants() {
    }

    /** 来源模块：数学 */
    public static final String SOURCE_MODULE = "MATH";

    /** 内容类型：母题变式（错题池按此区分来源） */
    public static final String CONTENT_TYPE_VARIANT = "MATH_VARIANT";

    /** 内容类型：母题 */
    public static final String CONTENT_TYPE_MOTHER = "MOTHER";

    /** 学科编码：数学 */
    public static final String SUBJECT_MATH = "MA";

    /** 生成方式：结构与答案全由规则产出 */
    public static final String GENERATED_BY_RULE = "RULE";

    /** 生成方式：结构与答案由规则产出，仅自然语言表述经模型润色 */
    public static final String GENERATED_BY_RULE_MODEL = "RULE+MODEL";

    /** 判定题型：数值容差（变式答案一律为数值） */
    public static final String JUDGE_CONTENT_TYPE_NUMERIC = "NUMERIC";

    /** 变式默认容差 */
    public static final BigDecimal DEFAULT_TOLERANCE = new BigDecimal("0.01");

    /** 过关所需变式全对数（首版 N=3） */
    public static final int DEFAULT_PASS_VARIANT_COUNT = 3;

    /** 变式层级数量：换数字 / 换情境 / 逆向 */
    public static final int LAYER_COUNT = 3;

    /** 权限码：查看 */
    public static final String PERMISSION_VIEW = "study:math:view";

    /** 权限码：编辑（含母题维护与变式生成） */
    public static final String PERMISSION_EDIT = "study:math:edit";

    /** 权限码：过关（作答 / 口述 / 过关判定） */
    public static final String PERMISSION_PASS = "study:math:pass";

    /** 掌握度：没有任何作答记录时的中性值（未练习 ≠ 已掌握） */
    public static final BigDecimal MASTERY_NEUTRAL = new BigDecimal("0.5");

    /** 苏格拉底式追问（口述不过关时给，<b>不含答案、不含关键关系</b>） */
    public static final String FOLLOW_UP_QUESTION =
            "这道题里，哪几个量之间存在固定关系？";
}
