package org.jeecg.modules.study.error.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 复习队列条目视图。
 *
 * @author jeecg-study
 */
public class ReviewQueueItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 错题业务ID */
    private String errorCode;

    /** 来源模块 */
    private String sourceModule;

    /** 内容类型 */
    private String contentType;

    /** 内容 ID */
    private String contentId;

    /** 排序分值（降序） */
    private BigDecimal orderWeight;

    /** 队列日期 */
    private Date queueDate;

    /** 知识点编码 */
    private String knowledgePointCode;

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getSourceModule() {
        return sourceModule;
    }

    public void setSourceModule(String sourceModule) {
        this.sourceModule = sourceModule;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getContentId() {
        return contentId;
    }

    public void setContentId(String contentId) {
        this.contentId = contentId;
    }

    public BigDecimal getOrderWeight() {
        return orderWeight;
    }

    public void setOrderWeight(BigDecimal orderWeight) {
        this.orderWeight = orderWeight;
    }

    public Date getQueueDate() {
        return queueDate;
    }

    public void setQueueDate(Date queueDate) {
        this.queueDate = queueDate;
    }

    public String getKnowledgePointCode() {
        return knowledgePointCode;
    }

    public void setKnowledgePointCode(String knowledgePointCode) {
        this.knowledgePointCode = knowledgePointCode;
    }
}
