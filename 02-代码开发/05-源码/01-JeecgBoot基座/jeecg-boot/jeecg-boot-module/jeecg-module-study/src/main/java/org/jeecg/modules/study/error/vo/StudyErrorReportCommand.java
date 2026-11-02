package org.jeecg.modules.study.error.vo;

import java.io.Serializable;

/**
 * 错题入池入参。
 *
 * <p>这是 V1–V4 四个学习模块共同依赖的<b>上游契约</b>：
 * 各模块判错后调用 {@code StudyErrorPoolService.report} 入池，
 * 不允许各模块自建错题表。</p>
 *
 * @author jeecg-study
 */
public class StudyErrorReportCommand implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 归属孩子 ID（必填，来自服务端会话，不接受前端传入） */
    private String childId;

    /** 来源模块：WORD / MATH / GRAMMAR / WRITING */
    private String sourceModule;

    /** 内容类型（题目 / 单词 / 语法点 / 作文…） */
    private String contentType;

    /** 内容 ID */
    private String contentId;

    /** 知识点编码 */
    private String knowledgePointCode;

    /** 学科编码 MA / CH / EN / CN */
    private String subjectCode;

    /** 错因（五维之一） */
    private String errorCause;

    /** 孩子的作答 */
    private String rawAnswer;

    /** 标准答案 */
    private String correctAnswer;

    /** 判定来源：只能是 rule / rule+model */
    private String judgeSource;

    /** 出处（非自编内容必填，否则 4006） */
    private String sourceRef;

    /** 版权类型：SELF / PUBLIC */
    private String copyrightType;

    public String getChildId() {
        return childId;
    }

    public void setChildId(String childId) {
        this.childId = childId;
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

    public String getRawAnswer() {
        return rawAnswer;
    }

    public void setRawAnswer(String rawAnswer) {
        this.rawAnswer = rawAnswer;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public void setCorrectAnswer(String correctAnswer) {
        this.correctAnswer = correctAnswer;
    }

    public String getJudgeSource() {
        return judgeSource;
    }

    public void setJudgeSource(String judgeSource) {
        this.judgeSource = judgeSource;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    public void setSourceRef(String sourceRef) {
        this.sourceRef = sourceRef;
    }

    public String getCopyrightType() {
        return copyrightType;
    }

    public void setCopyrightType(String copyrightType) {
        this.copyrightType = copyrightType;
    }
}
