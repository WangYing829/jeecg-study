package org.jeecg.modules.study.family.vo;

import java.io.Serializable;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 家长↔孩子 关系视图
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "家长↔孩子 关系视图")
public class RelationVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 关系行 ID（study_parent_child_rel.id） */
    @Schema(description = "关系行 ID（study_parent_child_rel.id）")
    private String relationId;

    /** 关系标签 */
    @Schema(description = "关系标签")
    private String relationLabel;

    /** 是否主家长 */
    @Schema(description = "是否主家长")
    private Boolean isPrimary;

    /** 状态 1生效 2已解除 */
    @Schema(description = "状态 1生效 2已解除")
    private Integer status;

    /** 是否当前登录者本人 */
    @Schema(description = "是否当前登录者本人")
    private Boolean isSelf;

}
