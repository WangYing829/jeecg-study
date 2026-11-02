package org.jeecg.modules.study.writing.constant;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 写作情境库常量（F-WRT-03）。
 *
 * <p><b>产品红线</b>：情境库只给「处境」，不给「可以直接抄的句子」——
 * 这里的常量也只描述处境的取值范围，没有任何金句、名言或范文句。</p>
 *
 * @author jeecg-study
 */
public final class WritingScenarioConstants {

    private WritingScenarioConstants() {
    }

    /** 学科编码：语文 */
    public static final String SUBJECT_CODE = "CN";

    /** 版权类型：自编 */
    public static final String COPYRIGHT_SELF = "SELF";

    /** 难度层级下限 */
    public static final int MIN_DIFFICULTY = 1;

    /** 难度层级上限 */
    public static final int MAX_DIFFICULTY = 5;

    /** 主题编码：坚持 */
    public static final String THEME_PERSIST = "PERSIST";

    /** 主题编码：成长 */
    public static final String THEME_GROWTH = "GROWTH";

    /** 主题编码：信任 */
    public static final String THEME_TRUST = "TRUST";

    /** 主题编码：失去 */
    public static final String THEME_LOSS = "LOSS";

    /** 主题编码：选择 */
    public static final String THEME_CHOICE = "CHOICE";

    /** 主题编码：第一次 */
    public static final String THEME_FIRSTTIME = "FIRSTTIME";

    /** 首批主题编码（顺序即呈现顺序） */
    public static final List<String> THEME_CODES = Collections.unmodifiableList(Arrays.asList(
            THEME_PERSIST, THEME_GROWTH, THEME_TRUST, THEME_LOSS, THEME_CHOICE, THEME_FIRSTTIME));
}
