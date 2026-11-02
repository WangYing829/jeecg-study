package org.jeecg.modules.study.math.enums;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 变式层级（F-MTH-02）。
 *
 * <p>三层固定顺序：</p>
 * <ol>
 *   <li>{@link #NUMBER} 换数字 —— 同结构，数值全部替换；</li>
 *   <li>{@link #CONTEXT} 换情境 —— 同结构不同表层（已知条件的另一种说法）；</li>
 *   <li>{@link #REVERSE} 逆向 —— 已知结果求条件，<b>最能检验真懂</b>。</li>
 * </ol>
 *
 * @author jeecg-study
 */
public enum VariantLayer {

    /** 1 换数字 */
    NUMBER(1, "换数字"),

    /** 2 换情境 */
    CONTEXT(2, "换情境"),

    /** 3 逆向 */
    REVERSE(3, "逆向");

    private final int layer;

    private final String layerName;

    VariantLayer(int layer, String layerName) {
        this.layer = layer;
        this.layerName = layerName;
    }

    public int getLayer() {
        return layer;
    }

    public String getLayerName() {
        return layerName;
    }

    /**
     * 按层级取枚举。
     *
     * @param layer 层级（1/2/3）
     * @return 枚举；非法层级返回 null
     */
    public static VariantLayer of(Integer layer) {
        if (layer == null) {
            return null;
        }
        for (VariantLayer v : values()) {
            if (v.layer == layer) {
                return v;
            }
        }
        return null;
    }

    /**
     * 三层固定顺序（1 → 2 → 3）。
     *
     * @return 层级列表
     */
    public static List<VariantLayer> ordered() {
        return new ArrayList<>(Arrays.asList(NUMBER, CONTEXT, REVERSE));
    }
}
