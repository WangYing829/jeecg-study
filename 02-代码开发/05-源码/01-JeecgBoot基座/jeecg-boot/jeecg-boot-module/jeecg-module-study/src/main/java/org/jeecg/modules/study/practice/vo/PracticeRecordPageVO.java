package org.jeecg.modules.study.practice.vo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 作答记录分页结果。
 *
 * <p>字段名与《接口设计说明书》§1.3 的分页约定一致
 * （{@code records / total / pageNo / pageSize / pages}）。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "作答记录分页结果")
public class PracticeRecordPageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前页记录 */
    @Schema(description = "当前页记录")
    private List<PracticeRecordVO> records = new ArrayList<>();

    /** 总条数 */
    @Schema(description = "总条数")
    private Long total = 0L;

    /** 当前页码 */
    @Schema(description = "当前页码")
    private Integer pageNo = 1;

    /** 每页条数 */
    @Schema(description = "每页条数")
    private Integer pageSize = 10;

    /** 总页数 */
    @Schema(description = "总页数")
    private Integer pages = 0;

    /** 统计所属月份 yyyyMM */
    @Schema(description = "统计所属月份 yyyyMM")
    private String month;
}
