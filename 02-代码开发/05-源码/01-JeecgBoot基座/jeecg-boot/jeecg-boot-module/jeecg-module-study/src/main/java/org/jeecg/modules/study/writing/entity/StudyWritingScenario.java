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
 * 语文写作情境（表 {@code study_writing_scenario}）。
 *
 * <p><b>内容表，全局共享</b>：情境是「所有人共享的素材供给」，因此本表
 * <b>没有 child_id 列</b>，也不按孩子隔离（见《数据库设计说明书》§9）。
 * 孩子的作答落在 {@code study_writing}（该表带 child_id）。</p>
 *
 * <p><b>产品红线</b>：本表<b>只描述处境</b>（时间 / 地点 / 人物 / 阻力）、
 * 可观察的细节线索与限制条件，<b>不存任何可以直接抄走的句子</b>
 * （F-WRT-03「给情境不给金句，强制填自己的经历」）。</p>
 *
 * @author jeecg-study
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "语文写作情境")
@TableName("study_writing_scenario")
public class StudyWritingScenario extends JeecgEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务ID：全局唯一，形如 SEED-SCN-{主题码}-{序号} */
    @Schema(description = "业务ID")
    @TableField(value = "scenario_code")
    private String scenarioCode;

    /** 主题编码：PERSIST / GROWTH / TRUST / LOSS / CHOICE / FIRSTTIME */
    @Schema(description = "主题编码")
    @TableField(value = "theme_code")
    private String themeCode;

    /** 主题名称：坚持 / 成长 / 信任 … */
    @Schema(description = "主题名称")
    @TableField(value = "theme_name")
    private String themeName;

    /** 情境标题：处境一句话（不是金句） */
    @Schema(description = "情境标题")
    @TableField(value = "title")
    private String title;

    /** 处境描述：时间 / 地点 / 人物 / 阻力 */
    @Schema(description = "处境描述")
    @TableField(value = "situation_desc")
    private String situationDesc;

    /** 年龄段：小学低年级 / 小学中年级 / 小学高年级 / 初中 */
    @Schema(description = "年龄段")
    @TableField(value = "age_stage")
    private String ageStage;

    /** 年龄下限 */
    @Schema(description = "年龄下限")
    @TableField(value = "age_min")
    private Integer ageMin;

    /** 年龄上限 */
    @Schema(description = "年龄上限")
    @TableField(value = "age_max")
    private Integer ageMax;

    /** 细节线索：JSON 数组文本（只给可观察的物与动作） */
    @Schema(description = "细节线索")
    @TableField(value = "detail_clue")
    private String detailClue;

    /** 适用学段：G1…G9 */
    @Schema(description = "适用学段")
    @TableField(value = "stage_code")
    private String stageCode;

    /** 适用文体：写事 / 写人 / 写物 / 写景 / 抒情 */
    @Schema(description = "适用文体")
    @TableField(value = "genre")
    private String genre;

    /** 难度层级：1…5 */
    @Schema(description = "难度层级")
    @TableField(value = "difficulty")
    private Integer difficulty;

    /** 写作提示题：引导孩子填自己的经历 */
    @Schema(description = "写作提示题")
    @TableField(value = "prompt_question")
    private String promptQuestion;

    /** 限制条件：只写自己身上发生过的事 */
    @Schema(description = "限制条件")
    @TableField(value = "restriction_note")
    private String restrictionNote;

    /** 禁用提示：禁止写成金句 / 禁止抄范文 */
    @Schema(description = "禁用提示")
    @TableField(value = "forbidden_words")
    private String forbiddenWords;

    /** 中性排序权重：同主题内的呈现先后（不是分数，也不是名次） */
    @Schema(description = "排序权重")
    @TableField(value = "order_weight")
    private Integer orderWeight;

    /** 出处：自编情境 */
    @Schema(description = "出处")
    @TableField(value = "source_ref")
    private String sourceRef;

    /** 版权类型：SELF / PUBLIC / LICENSE */
    @Schema(description = "版权类型")
    @TableField(value = "copyright_type")
    private String copyrightType;

    /** 状态：1 启用 / 0 停用 */
    @Schema(description = "状态")
    @TableField(value = "status")
    private Integer status;

    /** 删除标记 */
    @Schema(description = "删除标记")
    @TableLogic
    @TableField(value = "del_flag")
    private Integer delFlag;
}
