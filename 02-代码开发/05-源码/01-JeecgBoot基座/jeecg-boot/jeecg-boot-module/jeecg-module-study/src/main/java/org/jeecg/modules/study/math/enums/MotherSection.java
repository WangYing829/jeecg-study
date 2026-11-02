package org.jeecg.modules.study.math.enums;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 母题五段结构（F-MTH-01）。
 *
 * <p>五段缺一不可，缺任一段不允许保存（4007）：</p>
 * <ol>
 *   <li>题型名 —— 检索与关卡命名；</li>
 *   <li>已知条件类型 —— 生成变式；</li>
 *   <li>关键关系 —— 过关口述对象；</li>
 *   <li>解题步骤链 —— 生成讲解；</li>
 *   <li>易错点清单 —— 生成追问与错因预判。</li>
 * </ol>
 *
 * @author jeecg-study
 */
public enum MotherSection {

    /** 第 1 段：题型名 */
    TITLE(1, "TITLE", "题型名"),

    /** 第 2 段：已知条件类型 */
    CONDITION_TYPES(2, "CONDITION_TYPES", "已知条件类型"),

    /** 第 3 段：关键关系 */
    KEY_RELATION(3, "KEY_RELATION", "关键关系"),

    /** 第 4 段：解题步骤链 */
    STEPS(4, "STEPS", "解题步骤链"),

    /** 第 5 段：易错点清单 */
    PITFALLS(5, "PITFALLS", "易错点清单");

    private final int index;

    private final String code;

    private final String label;

    MotherSection(int index, String code, String label) {
        this.index = index;
        this.code = code;
        this.label = label;
    }

    public int getIndex() {
        return index;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 五段的固定展示顺序（TC-04-02：依次展示）。
     *
     * @return 段落码列表
     */
    public static List<String> orderedCodes() {
        List<String> codes = new ArrayList<>();
        for (MotherSection section : ordered()) {
            codes.add(section.code);
        }
        return codes;
    }

    /**
     * 五段的固定顺序。
     *
     * @return 段落列表
     */
    public static List<MotherSection> ordered() {
        return new ArrayList<>(Arrays.asList(TITLE, CONDITION_TYPES, KEY_RELATION, STEPS, PITFALLS));
    }
}
