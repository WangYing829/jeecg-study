package org.jeecg.modules.study.idseq.entity;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecg.common.system.base.entity.JeecgEntity;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 统一业务ID 分配序列（只增不复用）
 *
 * @author jeecg-study
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "统一业务ID 分配序列（只增不复用）")
@TableName("study_id_sequence")
public class StudyIdSequence extends JeecgEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 序列前缀 CHD / KPT / QST / MTH / WRD / GRP / WRT / ERR */
    @Schema(description = "序列前缀 CHD / KPT / QST / MTH / WRD / GRP / WRT / ERR")
    @TableField(value = "prefix")
    private String prefix;

    /** 下一个可用序号 */
    @Schema(description = "下一个可用序号")
    @TableField(value = "next_value")
    private Long nextValue;

    /** 步长 */
    @Schema(description = "步长")
    @TableField(value = "step")
    private Integer step;

    /** 删除标记 0正常 1删除 */
    @Schema(description = "删除标记 0正常 1删除")
    @TableLogic
    @TableField(value = "del_flag")
    private Integer delFlag;

}
