package org.jeecg.modules.study.writing.enums;

/**
 * 三步训练流的步骤（TC-06-05）。
 *
 * <p>顺序强制：审题 → 提纲 → 初稿。提纲没过就不允许提交初稿，
 * 服务端返回 4002（前置步骤未完成）。</p>
 *
 * @author jeecg-study
 */
public enum WritingTrainStep {

    /** 审题：选题 */
    TOPIC("TOPIC"),

    /** 提纲：写开—中—末三段 */
    OUTLINE("OUTLINE"),

    /** 初稿：写正文 */
    DRAFT("DRAFT");

    /** 步骤编码 */
    private final String code;

    WritingTrainStep(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /**
     * 宽松解析。
     *
     * @param code 步骤编码
     * @return 合法枚举；非法返回 null
     */
    public static WritingTrainStep of(String code) {
        if (code == null) {
            return null;
        }
        String trimmed = code.trim();
        for (WritingTrainStep step : values()) {
            if (step.code.equalsIgnoreCase(trimmed)) {
                return step;
            }
        }
        return null;
    }
}
