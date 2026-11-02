package org.jeecg.modules.study.registry.entity;

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
 * 坐标树节点（地区考纲 / 学科 / 单元 / 考点，四层，版本化）。
 *
 * <p>全局内容表，<b>不加 child_id</b>（多用户改造契约 §2.2）：内容供给成本高，家庭内共享。</p>
 *
 * <p><b>版本化口径</b>：考纲调整时<b>旧节点不删除</b>，只置 {@code status=0}（失效）
 * 并填 {@code invalid_at}；新节点 {@code version} 递增。
 * 旧记录（错题等）仍指向旧 {@code point_code}，坐标名照常解析（TC-01-12 / TC-01-13 / TC-08-23）。</p>
 *
 * @author jeecg-study
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "坐标树节点（考纲/学科/单元/考点，四层，版本化）")
@TableName("study_knowledge_point")
public class StudyKnowledgePoint extends JeecgEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 层级：1考纲 */
    public static final int LEVEL_OUTLINE = 1;

    /** 层级：2学科 */
    public static final int LEVEL_SUBJECT = 2;

    /** 层级：3单元 */
    public static final int LEVEL_UNIT = 3;

    /** 层级：4考点 */
    public static final int LEVEL_POINT = 4;

    /** 业务ID KPT-{学科码}-{序号} */
    @Schema(description = "业务ID KPT-{学科码}-{序号}")
    @TableField(value = "point_code")
    private String pointCode;

    /** 父节点 ID */
    @Schema(description = "父节点 ID")
    @TableField(value = "parent_id")
    private String parentId;

    /** 层级 1考纲 2学科 3单元 4考点 */
    @Schema(description = "层级 1考纲 2学科 3单元 4考点")
    @TableField(value = "level")
    private Integer level;

    /** 学科编码 MA/CH/EN/CN */
    @Schema(description = "学科编码 MA/CH/EN/CN")
    @TableField(value = "subject_code")
    private String subjectCode;

    /** 名称 */
    @Schema(description = "名称")
    @TableField(value = "name")
    private String name;

    /** 物化路径 /id/id/id/（存的是祖先链，不含自身） */
    @Schema(description = "物化路径 /id/id/id/")
    @TableField(value = "path")
    private String path;

    /** 考点：题型 */
    @Schema(description = "考点：题型")
    @TableField(value = "question_type")
    private String questionType;

    /** 考点：分值权重 */
    @Schema(description = "考点：分值权重")
    @TableField(value = "score_weight")
    private BigDecimal scoreWeight;

    /** 考点：常见错误类型（逗号分隔） */
    @Schema(description = "考点：常见错误类型（逗号分隔）")
    @TableField(value = "error_types")
    private String errorTypes;

    /** 考点：对应母题业务ID */
    @Schema(description = "考点：对应母题业务ID")
    @TableField(value = "mother_question_code")
    private String motherQuestionCode;

    /** 节点版本，如 v1 / v2 */
    @Schema(description = "节点版本，如 v1 / v2")
    @TableField(value = "version")
    private String version;

    /** 生效日期 */
    @Schema(description = "生效日期")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @TableField(value = "effective_from")
    private Date effectiveFrom;

    /** 失效日期（NULL = 现行） */
    @Schema(description = "失效日期（NULL = 现行）")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @TableField(value = "invalid_at")
    private Date invalidAt;

    /** 状态 1现行 0失效 */
    @Schema(description = "状态 1现行 0失效")
    @TableField(value = "status")
    private Integer status;

    /** 排序号 */
    @Schema(description = "排序号")
    @TableField(value = "sort_no")
    private Integer sortNo;

    /** 删除标记 0正常 1删除 */
    @Schema(description = "删除标记 0正常 1删除")
    @TableLogic
    @TableField(value = "del_flag")
    private Integer delFlag;

}
