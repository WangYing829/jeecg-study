package org.jeecg.modules.study.error.vo;

import java.io.Serializable;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 错题详情。
 *
 * <p><b>不下发答案与解析</b>：错题详情里有「孩子当时错在哪」的客观状态，
 * 但没有正确答案、没有解析、没有讲解（R-02 / R-04）。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
@Schema(description = "错题详情")
public class ErrorDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 错题业务ID */
    @Schema(description = "错题业务ID")
    private String errorCode;

    /** 来源模块 */
    @Schema(description = "来源模块")
    private String sourceModule;

    /** 内容类型 */
    @Schema(description = "内容类型")
    private String contentType;

    /** 关联内容业务ID */
    @Schema(description = "关联内容业务ID")
    private String contentId;

    /** 考点业务ID */
    @Schema(description = "考点业务ID")
    private String knowledgePointCode;

    /** 考点名称 */
    @Schema(description = "考点名称")
    private String knowledgePointName;

    /** 学科编码 */
    @Schema(description = "学科编码")
    private String subjectCode;

    /** 错因 */
    @Schema(description = "错因")
    private String errorCause;

    /** 错因展示名 */
    @Schema(description = "错因展示名")
    private String errorCauseName;

    /** 判定来源 rule / rule+model */
    @Schema(description = "判定来源")
    private String judgeSource;

    /** 掌握状态 1未知 2学习中 3熟悉 4掌握 */
    @Schema(description = "掌握状态")
    private Integer masteryState;

    /** 连续答对次数 */
    @Schema(description = "连续答对次数")
    private Integer correctStreak;

    /** 复习状态 1待复习 2已移出 */
    @Schema(description = "复习状态")
    private Integer reviewStatus;

    /** 下次复习日期 */
    @Schema(description = "下次复习日期")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    private Date nextReviewAt;

    /** 上次复习时间 */
    @Schema(description = "上次复习时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastReviewAt;

    /** 是否已移出复习队列 */
    @Schema(description = "是否已移出复习队列")
    private Boolean archived;

    /** 入池时间 */
    @Schema(description = "入池时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 版权类型 */
    @Schema(description = "版权类型")
    private String copyrightType;

    /** 出处 */
    @Schema(description = "出处")
    private String sourceRef;
}
