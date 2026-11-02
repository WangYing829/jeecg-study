package org.jeecg.modules.study.context.entity;

import java.io.Serializable;
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
 * 全局上下文（地区 / 学段 / 年级 / 教材版本）。
 *
 * <p>上下文按 {@code child_id} 隔离（多用户改造契约 §2.2）：
 * 一个孩子同时只有一个 {@code is_current=1} 的上下文，
 * 切换上下文时旧上下文置为非当前但**保留**（历史错题按 {@code context_id} 归集，
 * 不随上下文切换混入新队列 —— TC-01-06）。</p>
 *
 * @author jeecg-study
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "全局上下文（地区/学段/年级/教材版本）")
@TableName("study_context")
public class StudyContext extends JeecgEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 所属孩子 ID（多用户隔离维度） */
    @Schema(description = "所属孩子 ID（多用户隔离维度）")
    @TableField(value = "child_id")
    private String childId;

    /** 业务ID CTX-000001 */
    @Schema(description = "业务ID CTX-000001")
    @TableField(value = "context_code")
    private String contextCode;

    /** 省级行政区编码 */
    @Schema(description = "省级行政区编码")
    @TableField(value = "province_code")
    private String provinceCode;

    /** 省级行政区名称 */
    @Schema(description = "省级行政区名称")
    @TableField(value = "province_name")
    private String provinceName;

    /** 学段编码 PRIMARY/JUNIOR/SENIOR */
    @Schema(description = "学段编码 PRIMARY/JUNIOR/SENIOR")
    @TableField(value = "stage_code")
    private String stageCode;

    /** 年级编码 G1..G12 */
    @Schema(description = "年级编码 G1..G12")
    @TableField(value = "grade_code")
    private String gradeCode;

    /** 教材版本 ID */
    @Schema(description = "教材版本 ID")
    @TableField(value = "textbook_version_id")
    private String textbookVersionId;

    /** 护眼提醒间隔（分钟） */
    @Schema(description = "护眼提醒间隔（分钟）")
    @TableField(value = "eye_interval_min")
    private Integer eyeIntervalMin;

    /** 是否当前生效 1是 0否 */
    @Schema(description = "是否当前生效 1是 0否")
    @TableField(value = "is_current")
    private Integer isCurrent;

    /** 生效时间 */
    @Schema(description = "生效时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "effective_from")
    private Date effectiveFrom;

    /** 删除标记 0正常 1删除 */
    @Schema(description = "删除标记 0正常 1删除")
    @TableLogic
    @TableField(value = "del_flag")
    private Integer delFlag;

}
