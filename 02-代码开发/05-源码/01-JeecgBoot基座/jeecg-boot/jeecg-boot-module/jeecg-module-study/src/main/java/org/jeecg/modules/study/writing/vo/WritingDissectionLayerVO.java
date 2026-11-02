package org.jeecg.modules.study.writing.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 拆解库的单层视图。
 *
 * <p>每一层都带一个<b>层末提示题</b>（{@code promptQuestion}），
 * 让孩子把这一层的写法搬到自己的题目上（TC-06-02）。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "拆解库的单层")
public class WritingDissectionLayerVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 层编码：HOOK / OPEN / MATERIAL / STRUCT / HAND / END */
    @Schema(description = "层编码")
    private String layer;

    /** 层名称：题目钩子 / 开篇三句 / 选材角度 / 结构图 / 三个手法例句 / 结尾回扣 */
    @Schema(description = "层名称")
    private String layerName;

    /** 该层标题 */
    @Schema(description = "该层标题")
    private String title;

    /** 该层正文 */
    @Schema(description = "该层正文")
    private String content;

    /** 写法要点（可迁移的写法） */
    @Schema(description = "写法要点")
    private String teachingPoint;

    /** 层末提示题：换到你的题目上会怎么写 */
    @Schema(description = "层末提示题")
    private String promptQuestion;
}
