package org.jeecg.modules.study.word.enums;

/**
 * 线别：课内 / 课外。
 *
 * <p><b>取值为 {@code IN} / {@code OUT}</b>——与《数据库设计说明书》§5.4、§10.1
 * 及线上表 {@code study_word.line_type} 的注释口径一致（线上库该列注释即
 * 「线别 IN课内 OUT课外」）。</p>
 *
 * <p>为兼容另一种叫法，解析时额外接受 {@code TEXTBOOK}（课内）与
 * {@code EXTRA}（课外）两个别名，但<b>落库一律写 {@code IN} / {@code OUT}</b>，
 * 避免同一列出现两套取值。</p>
 *
 * @author jeecg-study
 */
public enum WordLineType {

    /** 课内（教材单元词表） */
    IN("IN", "课内"),

    /** 课外（高频词线） */
    OUT("OUT", "课外");

    private final String code;

    private final String label;

    WordLineType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 宽松解析：接受 {@code IN/OUT} 与别名 {@code TEXTBOOK/EXTRA}，其余判为非法。
     *
     * @param code 原始值
     * @return 合法枚举；非法返回 null
     */
    public static WordLineType of(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.trim().toUpperCase();
        if ("IN".equals(normalized) || "TEXTBOOK".equals(normalized)) {
            return IN;
        }
        if ("OUT".equals(normalized) || "EXTRA".equals(normalized)) {
            return OUT;
        }
        return null;
    }

    public static boolean isValid(String code) {
        return of(code) != null;
    }
}
