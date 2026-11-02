package org.jeecg.modules.study.grammar.gameplay.vo;

import java.io.Serializable;
import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 一个玩法的闯关进度。
 *
 * <p>只跟自己比：已通过几关、答过几回、最近一次什么时候玩。
 * <b>不出现任何与他人比较的字段</b>（R-04）。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "玩法的闯关进度")
public class GrammarGameTypeProgressVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 玩法类型 */
    @Schema(description = "玩法类型")
    private String gameType;

    /** 玩法类型中文名 */
    @Schema(description = "玩法类型中文名")
    private String gameTypeName;

    /** 题库里的关卡总数 */
    @Schema(description = "关卡总数")
    private Integer totalCount;

    /** 已通过的关卡数 */
    @Schema(description = "已通过的关卡数")
    private Integer passedCount;

    /** 作答次数 */
    @Schema(description = "作答次数")
    private Integer answeredCount;

    /** 最近一次作答时间 */
    @Schema(description = "最近一次作答时间")
    private Date lastPlayedAt;

}
