package org.jeecg.modules.study.writing.entity;

import java.io.Serializable;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecg.common.system.base.entity.JeecgEntity;

/**
 * 语文写作内容（表 {@code study_writing}）。
 *
 * <p><b>一张表两种角色</b>，靠 {@code child_id} 区分：</p>
 * <ul>
 *   <li>{@code child_id IS NULL} —— 佳作拆解库六层，<b>内容表全局共享</b>，
 *       不带任何孩子维度；</li>
 *   <li>{@code child_id = 孩子的 id} —— 孩子的提纲 / 初稿，以及家长的反馈，
 *       <b>必须按孩子隔离</b>，查询一律带 {@code child_id} 条件。</li>
 * </ul>
 *
 * @author jeecg-study
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "语文写作内容")
@TableName("study_writing")
public class StudyWriting extends JeecgEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 归属孩子 ID：拆解库为 null，孩子练习数据与家长反馈必填 */
    @Schema(description = "归属孩子ID")
    @TableField(value = "child_id")
    private String childId;

    /** 业务ID：全局唯一。拆解库形如 WRT-HOOK-0031，孩子数据形如 WRT-DRAFT-xxxxxxxxxxxx */
    @Schema(description = "业务ID")
    @TableField(value = "writing_code")
    private String writingCode;

    /** 层：拆解库六层 / OUTLINE / DRAFT / FEEDBACK */
    @Schema(description = "层")
    @TableField(value = "layer")
    private String layer;

    /** 标题：拆解库为该层标题，孩子数据为题目 */
    @Schema(description = "标题")
    @TableField(value = "title")
    private String title;

    /** 正文内容 */
    @Schema(description = "正文内容")
    @TableField(value = "content")
    private String content;

    /** 写法要点（可迁移的写法，不给可直接抄的句子） */
    @Schema(description = "写法要点")
    @TableField(value = "teaching_point")
    private String teachingPoint;

    /** 层末提示题：换到你的题目上会怎么写 */
    @Schema(description = "层末提示题")
    @TableField(value = "prompt_question")
    private String promptQuestion;

    /** 学科编码：CN */
    @Schema(description = "学科编码")
    @TableField(value = "subject_code")
    private String subjectCode;

    /** 题材：记叙文 / 议论文 … */
    @Schema(description = "题材")
    @TableField(value = "genre")
    private String genre;

    /** 学段：G1…G9 */
    @Schema(description = "学段")
    @TableField(value = "stage_code")
    private String stageCode;

    /** 所属考点业务ID */
    @Schema(description = "所属考点业务ID")
    @TableField(value = "knowledge_point_code")
    private String knowledgePointCode;

    /** 拆解集编码：同一组六层共用同一个值 */
    @Schema(description = "拆解集编码")
    @TableField(value = "reference_code")
    private String referenceCode;

    /** 出处（非自编内容必填） */
    @Schema(description = "出处")
    @TableField(value = "source_ref")
    private String sourceRef;

    /** 版权类型：SELF / PUBLIC */
    @Schema(description = "版权类型")
    @TableField(value = "copyright_type")
    private String copyrightType;

    /** 状态：拆解库 1启用；提纲行 1表示已通过提纲 */
    @Schema(description = "状态")
    @TableField(value = "status")
    private Integer status;

    /** 删除标记 */
    @Schema(description = "删除标记")
    @TableLogic
    @TableField(value = "del_flag")
    private Integer delFlag;
}
