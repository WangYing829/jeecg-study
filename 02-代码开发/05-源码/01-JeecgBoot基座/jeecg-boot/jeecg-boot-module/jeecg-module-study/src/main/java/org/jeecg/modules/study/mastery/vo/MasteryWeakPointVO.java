package org.jeecg.modules.study.mastery.vo;

import java.io.Serializable;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 薄弱考点条目。
 *
 * <p><b>只有客观状态，没有名次</b>：R-04 明确禁止家长端出现任何名次 / 百分位类字段，
 * 所以本视图只给「考点 + 当前掌握状态 + 错题条数」，顺序由服务端的错题条数决定，
 * 但响应体里<b>不出现名次字段</b>。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "薄弱考点条目")
public class MasteryWeakPointVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 考点业务ID KPT-xx-xxxx */
    @Schema(description = "考点业务ID")
    private String pointCode;

    /** 考点名称 */
    @Schema(description = "考点名称")
    private String pointName;

    /** 学科编码 MA/CH/EN/CN */
    @Schema(description = "学科编码")
    private String subjectCode;

    /** 掌握状态 1未知 2学习中 3熟悉 4掌握 */
    @Schema(description = "掌握状态")
    private Integer masteryState;

    /** 该考点下的错题条数 */
    @Schema(description = "错题条数")
    private Integer errorCount;

    /** 最近一次错题时间 */
    @Schema(description = "最近一次错题时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastErrorAt;
}
