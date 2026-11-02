package org.jeecg.modules.study.math.entity;

import java.io.Serializable;
import java.util.Date;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 数学作答记录写入载体。
 *
 * <p>母题域的作答记录写入<b>统一作答记录分表</b>
 * （{@code study_practice_record_yyyyMM}，{@code source_module = MATH}），
 * 这样错题池、复习排期、掌握度统计都能在同一个地方取数，
 * 数学域不需要自建记录表（与练习域共用同一张统一表）。</p>
 *
 * <p>不是数据库实体，只作写入参数用。</p>
 *
 * @author jeecg-study
 */
@Data
@Accessors(chain = true)
public class StudyMathRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键（幂等键的确定性摘要） */
    private String id;

    /** 所属孩子 ID */
    private String childId;

    /** 全局上下文 ID */
    private String contextId;

    /** 来源模块 */
    private String sourceModule;

    /** 内容类型 */
    private String contentType;

    /** 内容业务ID（变式业务ID） */
    private String contentId;

    /** 考点业务ID */
    private String knowledgePointCode;

    /** 孩子作答 */
    private String answer;

    /** 是否正确 1是 0否 */
    private Integer correct;

    /** 判定来源 rule / rule+model */
    private String judgeSource;

    /** 错因 */
    private String errorCause;

    /** 错题业务ID */
    private String errorCode;

    /** 耗时（毫秒） */
    private Integer durationMs;

    /** 步骤级错因打标（JSON 文本） */
    private String stepTags;

    /** 作答日期 */
    private Date practiceDate;
}
