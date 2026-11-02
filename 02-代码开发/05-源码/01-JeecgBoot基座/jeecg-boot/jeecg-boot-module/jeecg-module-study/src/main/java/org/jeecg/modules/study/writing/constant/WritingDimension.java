package org.jeecg.modules.study.writing.constant;

import java.util.Arrays;
import java.util.List;

/**
 * 对照式批改的<b>差异维度</b>。
 *
 * <p>维度是<b>清单</b>不是分数：每条差异只回答「哪里不一样、怎么改」，
 * 不产出任何分数或等级（R-04 / TC-06-04）。</p>
 *
 * <p>{@link #PRIORITY} 是服务端固定的展示顺序：规则命中后按它排序，
 * 再截断到 {@link WritingConstants#MAX_DIFF_COUNT} 条。</p>
 *
 * @author jeecg-study
 */
public final class WritingDimension {

    /** 结构：结尾有没有回扣开头 */
    public static final String STRUCTURE = "STRUCTURE";

    /** 选材：细节够不够具体 */
    public static final String MATERIAL = "MATERIAL";

    /** 手法：有没有描写 */
    public static final String HAND = "HAND";

    /** 连接词：句与句之间有没有衔接 */
    public static final String CONNECTOR = "CONNECTOR";

    /** 句长：句子是不是太长 */
    public static final String SENTENCE_LENGTH = "SENTENCE_LENGTH";

    /** 字数：篇幅够不够 */
    public static final String LENGTH = "LENGTH";

    /** 维度全集（固定顺序，截断后即为呈现顺序） */
    public static final List<String> PRIORITY = Arrays.asList(
            STRUCTURE, MATERIAL, HAND, CONNECTOR, SENTENCE_LENGTH, LENGTH);

    private WritingDimension() {
    }

    /**
     * 是否为合法维度。
     *
     * @param dimension 维度
     * @return 合法返回 true
     */
    public static boolean isValid(String dimension) {
        return dimension != null && PRIORITY.contains(dimension.trim());
    }
}
