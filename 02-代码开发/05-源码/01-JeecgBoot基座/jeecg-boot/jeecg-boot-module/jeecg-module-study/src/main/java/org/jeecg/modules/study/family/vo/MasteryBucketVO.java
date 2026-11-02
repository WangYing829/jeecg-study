package org.jeecg.modules.study.family.vo;

import java.io.Serializable;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 掌握度分布桶
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "掌握度分布桶")
public class MasteryBucketVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 桶名（未掌握 / 巩固中 / 已掌握） */
    @Schema(description = "桶名（未掌握 / 巩固中 / 已掌握）")
    private String bucket;

    /** 数量 */
    @Schema(description = "数量")
    private Integer count;

}
