package org.jeecg.modules.study.context.entity;

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
 * 教材版本维表（人教 / 苏教 / 北师大 / 浙教 / 沪教 / 京教 / 鲁教）。
 *
 * <p>全局字典表，<b>不加 child_id</b>（多用户改造契约 §2.2）。</p>
 *
 * @author jeecg-study
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "教材版本维表（人教/苏教/北师大/浙教/沪教/京教/鲁教）")
@TableName("study_textbook_version")
public class StudyTextbookVersion extends JeecgEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 教材版本编码 */
    @Schema(description = "教材版本编码")
    @TableField(value = "version_code")
    private String versionCode;

    /** 教材版本名称（人教版…） */
    @Schema(description = "教材版本名称（人教版…）")
    @TableField(value = "version_name")
    private String versionName;

    /** 出版社（人教/苏教/北师大/浙教/沪教/京教/鲁教） */
    @Schema(description = "出版社（人教/苏教/北师大/浙教/沪教/京教/鲁教）")
    @TableField(value = "publisher")
    private String publisher;

    /** 适用地区（逗号分隔） */
    @Schema(description = "适用地区（逗号分隔）")
    @TableField(value = "region_scope")
    private String regionScope;

    /** 适用学段 PRIMARY/JUNIOR/SENIOR */
    @Schema(description = "适用学段 PRIMARY/JUNIOR/SENIOR")
    @TableField(value = "stage_code")
    private String stageCode;

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
