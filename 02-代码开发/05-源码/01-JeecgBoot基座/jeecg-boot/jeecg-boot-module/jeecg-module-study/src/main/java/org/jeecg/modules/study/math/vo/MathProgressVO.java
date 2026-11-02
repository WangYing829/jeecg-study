package org.jeecg.modules.study.math.vo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 关卡进度（TC-04-12，F-MTH-06）。
 *
 * <p><b>单位是「母题」不是「题量」</b>：孩子看到的是「这类题我会了 N 类」，
 * 不出现「刷了 M 道」这种按题量计的说法。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "关卡进度")
public class MathProgressVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 已掌握的母题类数 */
    @Schema(description = "已掌握的母题类数")
    private Integer masteredTypeCount;

    /** 母题总类数 */
    @Schema(description = "母题总类数")
    private Integer totalTypeCount;

    /** 进度文案：这类题我会了 N 类 */
    @Schema(description = "进度文案")
    private String progressText;

    /** 已掌握的母题业务ID */
    @Schema(description = "已掌握的母题业务ID")
    private List<String> masteredMotherCodes = new ArrayList<>();
}
