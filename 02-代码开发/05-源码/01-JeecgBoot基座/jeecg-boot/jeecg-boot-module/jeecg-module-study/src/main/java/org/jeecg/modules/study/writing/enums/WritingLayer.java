package org.jeecg.modules.study.writing.enums;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 写作内容的<b>层</b>。
 *
 * <p>两类层共用 {@code study_writing} 一张表，靠 {@code child_id} 区分：</p>
 * <ul>
 *   <li><b>拆解库层</b>（{@code child_id IS NULL}）：佳作拆解的六层，
 *       全局共享，内容表不带孩子维度；</li>
 *   <li><b>练习层</b>（{@code child_id = 孩子的 id}）：提纲 / 初稿 / 家长反馈，
 *       必须按孩子隔离。</li>
 * </ul>
 *
 * <p>六层的顺序即 TC-06-01 要求的呈现顺序：
 * 题目钩子 → 开篇三句 → 选材角度 → 结构图 → 三个手法例句 → 结尾回扣。</p>
 *
 * @author jeecg-study
 */
public enum WritingLayer {

    /** 题目钩子 */
    HOOK("HOOK", "题目钩子", 1, true),

    /** 开篇三句 */
    OPEN("OPEN", "开篇三句", 2, true),

    /** 选材角度 */
    MATERIAL("MATERIAL", "选材角度", 3, true),

    /** 结构图 */
    STRUCT("STRUCT", "结构图", 4, true),

    /** 三个手法例句 */
    HAND("HAND", "三个手法例句", 5, true),

    /** 结尾回扣 */
    END("END", "结尾回扣", 6, true),

    /** 提纲（孩子练习数据） */
    OUTLINE("OUTLINE", "提纲", 0, false),

    /** 初稿（孩子练习数据） */
    DRAFT("DRAFT", "初稿", 0, false),

    /** 家长反馈（家长写、孩子看不到） */
    FEEDBACK("FEEDBACK", "家长反馈", 0, false);

    /** 层编码 */
    private final String code;

    /** 中文名称 */
    private final String label;

    /** 呈现顺序（拆解库层用） */
    private final int order;

    /** 是否属于拆解库（内容表，child_id 为空） */
    private final boolean library;

    WritingLayer(String code, String label, int order, boolean library) {
        this.code = code;
        this.label = label;
        this.order = order;
        this.library = library;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public int getOrder() {
        return order;
    }

    public boolean isLibrary() {
        return library;
    }

    /**
     * 宽松解析：按编码取层。
     *
     * @param code 层编码
     * @return 合法枚举；非法返回 null
     */
    public static WritingLayer of(String code) {
        if (code == null) {
            return null;
        }
        String trimmed = code.trim();
        for (WritingLayer layer : values()) {
            if (layer.code.equalsIgnoreCase(trimmed)) {
                return layer;
            }
        }
        return null;
    }

    /**
     * 拆解库六层（按呈现顺序）。
     *
     * @return 六层列表
     */
    public static List<WritingLayer> libraryLayers() {
        List<WritingLayer> layers = new ArrayList<>();
        for (WritingLayer layer : values()) {
            if (layer.library) {
                layers.add(layer);
            }
        }
        layers.sort((a, b) -> Integer.compare(a.order, b.order));
        return layers;
    }

    /**
     * 拆解库六层的层编码（按呈现顺序）。
     *
     * @return 层编码列表
     */
    public static List<String> libraryLayerCodes() {
        List<String> codes = new ArrayList<>();
        for (WritingLayer layer : libraryLayers()) {
            codes.add(layer.getCode());
        }
        return codes;
    }

    /**
     * 拆解库六层的中文名称（按呈现顺序）。
     *
     * @return 中文名称列表
     */
    public static List<String> libraryLayerLabels() {
        return Arrays.asList(
                HOOK.label, OPEN.label, MATERIAL.label, STRUCT.label, HAND.label, END.label);
    }
}
