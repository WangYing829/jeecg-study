package org.jeecg.modules.study.error.vo;

import java.io.Serializable;
import java.util.Date;

/**
 * 错题条目视图。
 *
 * @author jeecg-study
 */
public class ErrorPoolItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 错题业务ID */
    private String errorCode;

    /** 来源模块 */
    private String sourceModule;

    /** 内容类型 */
    private String contentType;

    /** 内容 ID */
    private String contentId;

    /** 知识点编码 */
    private String knowledgePointCode;

    /** 学科编码 */
    private String subjectCode;

    /** 错因 */
    private String errorCause;

    /** 判定来源 rule / rule+model */
    private String judgeSource;

    /** 掌握状态 */
    private Integer masteryState;

    /** 连续答对次数 */
    private Integer correctStreak;

    /** 复习状态 */
    private Integer reviewStatus;

    /** 下次复习日期 */
    private Date nextReviewAt;

    /** 是否已归档（连续答对 3 次后移出队列） */
    private Boolean archived;

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

    public String getKnowledgePointCode() {
        return knowledgePointCode;
    }

    public void setKnowledgePointCode(String knowledgePointCode) {
        this.knowledgePointCode = knowledgePointCode;
    }

    public String getSubjectCode() {
        return subjectCode;
    }

    public void setSubjectCode(String subjectCode) {
        this.subjectCode = subjectCode;
    }

    public String getErrorCause() {
        return errorCause;
    }

    public void setErrorCause(String errorCause) {
        this.errorCause = errorCause;
    }

    public String getJudgeSource() {
        return judgeSource;
    }

    public void setJudgeSource(String judgeSource) {
        this.judgeSource = judgeSource;
    }

    public Integer getMasteryState() {
        return masteryState;
    }

    public void setMasteryState(Integer masteryState) {
        this.masteryState = masteryState;
    }

    public Integer getCorrectStreak() {
        return correctStreak;
    }

    public void setCorrectStreak(Integer correctStreak) {
        this.correctStreak = correctStreak;
    }

    public Integer getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(Integer reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public Date getNextReviewAt() {
        return nextReviewAt;
    }

    public void setNextReviewAt(Date nextReviewAt) {
        this.nextReviewAt = nextReviewAt;
    }

    public Boolean getArchived() {
        return archived;
    }

    public void setArchived(Boolean archived) {
        this.archived = archived;
    }
}
