package org.jeecg.modules.study.word.constant;

import java.math.BigDecimal;

/**
 * 背单词模块常量。
 *
 * <p>四档反馈的档位码与《接口设计说明书》§7.2 一致：{@code KNOWN/VAGUE/FORGOT/FAMILIAR}，
 * 对应「认识 / 模糊 / 忘记 / 熟知」，**不是对/错**（F-WRD-04）。</p>
 *
 * @author jeecg-study
 */
public final class WordConstants {

    private WordConstants() {
    }

    /** 反馈档位：认识 */
    public static final String FEEDBACK_KNOWN = "KNOWN";

    /** 反馈档位：模糊 */
    public static final String FEEDBACK_VAGUE = "VAGUE";

    /** 反馈档位：忘记 */
    public static final String FEEDBACK_FORGOT = "FORGOT";

    /** 反馈档位：熟知 */
    public static final String FEEDBACK_FAMILIAR = "FAMILIAR";

    /** 环节码：释义（第一层） */
    public static final String STAGE_MEANING = "MEANING";

    /** 环节码：造句（第二层） */
    public static final String STAGE_SENTENCE = "SENTENCE";

    /** 环节码：短文填词（第三层） */
    public static final String STAGE_PASSAGE = "PASSAGE";

    /** 来源模块：背单词 */
    public static final String SOURCE_MODULE_WORD = "WORD";

    /** 内容类型：单词 */
    public static final String CONTENT_TYPE_WORD = "WORD";

    /** 内容类型：单元（闯关记录） */
    public static final String CONTENT_TYPE_UNIT = "UNIT";

    /** 学科编码：英语 */
    public static final String SUBJECT_CODE_EN = "EN";

    /** 版权类型：自编 */
    public static final String COPYRIGHT_TYPE_SELF = "SELF";

    /** 默认记忆强度 S */
    public static final BigDecimal DEFAULT_MEMORY_STRENGTH = new BigDecimal("0.0000");

    /** 默认衰减系数 λ */
    public static final BigDecimal DEFAULT_DECAY_LAMBDA = new BigDecimal("2.0000");

    /** 认识：S + 0.35 */
    public static final BigDecimal DELTA_KNOWN = new BigDecimal("0.35");

    /** 模糊：S + 0.10 */
    public static final BigDecimal DELTA_VAGUE = new BigDecimal("0.10");

    /** 熟知：S + 0.50 */
    public static final BigDecimal DELTA_FAMILIAR = new BigDecimal("0.50");

    /** 模糊：λ × 1.2 */
    public static final BigDecimal LAMBDA_FACTOR_VAGUE = new BigDecimal("1.2");

    /** 忘记：λ × 1.6 */
    public static final BigDecimal LAMBDA_FACTOR_FORGOT = new BigDecimal("1.6");

    /** 熟知：λ × 0.8 */
    public static final BigDecimal LAMBDA_FACTOR_FAMILIAR = new BigDecimal("0.8");

    /** 遗忘参数换算复习间隔的天数基数 */
    public static final int INTERVAL_BASE_DAYS = 30;

    /** 遗忘参数换算复习间隔的天数上限 */
    public static final int INTERVAL_MAX_DAYS = 60;

    /** 短文填词：拼入短文的例句条数 */
    public static final int PASSAGE_SENTENCE_COUNT = 3;

    /** 挖空占位符（短文填词与例句遮蔽共用） */
    public static final String BLANK_PLACEHOLDER = "____";
}
