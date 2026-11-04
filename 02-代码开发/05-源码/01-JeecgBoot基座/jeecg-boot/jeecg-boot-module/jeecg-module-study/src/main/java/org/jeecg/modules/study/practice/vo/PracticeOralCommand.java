package org.jeecg.modules.study.practice.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 口述提交入参（F-MTH-03）。
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "口述提交入参")
public class PracticeOralCommand implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 口述内容（孩子自己讲的关键关系） */
    @Schema(description = "口述内容")
    private String speechText;
}
