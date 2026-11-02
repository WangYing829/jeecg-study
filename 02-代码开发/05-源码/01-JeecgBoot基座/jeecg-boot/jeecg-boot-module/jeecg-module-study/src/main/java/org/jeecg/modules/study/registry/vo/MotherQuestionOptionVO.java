package org.jeecg.modules.study.registry.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 母题下拉项（只列确实存在的母题）。
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "母题下拉项")
public class MotherQuestionOptionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 母题业务ID */
    @Schema(description = "母题业务ID")
    private String motherCode;

    /** 题型名（母题第 1 段） */
    @Schema(description = "题型名")
    private String title;

    /** 学科编码 */
    @Schema(description = "学科编码")
    private String subjectCode;

}
