package org.jeecg.modules.study.grammar.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.study.common.constant.StudyErrorCode;
import org.jeecg.modules.study.common.enums.JudgeSource;
import org.jeecg.modules.study.common.exception.StudyForbiddenException;
import org.jeecg.modules.study.error.constant.ErrorCause;
import org.jeecg.modules.study.error.service.StudyErrorPoolService;
import org.jeecg.modules.study.error.vo.StudyErrorReportCommand;
import org.jeecg.modules.study.grammar.constant.GrammarExamType;
import org.jeecg.modules.study.grammar.constant.GrammarSource;
import org.jeecg.modules.study.grammar.engine.GrammarErrorDetector;
import org.jeecg.modules.study.grammar.engine.GrammarTransformEngine;
import org.jeecg.modules.study.grammar.entity.StudyGrammarPoint;
import org.jeecg.modules.study.grammar.mapper.StudyGrammarErrorQueryMapper;
import org.jeecg.modules.study.grammar.mapper.StudyGrammarPointMapper;
import org.jeecg.modules.study.grammar.service.StudyGrammarService;
import org.jeecg.modules.study.grammar.vo.GrammarCollectionResultVO;
import org.jeecg.modules.study.grammar.vo.GrammarCollectionRow;
import org.jeecg.modules.study.grammar.vo.GrammarCollectionVO;
import org.jeecg.modules.study.grammar.vo.GrammarDetectRequest;
import org.jeecg.modules.study.grammar.vo.GrammarDetectResultVO;
import org.jeecg.modules.study.grammar.vo.GrammarErrorDetailRequest;
import org.jeecg.modules.study.grammar.vo.GrammarErrorFeedbackVO;
import org.jeecg.modules.study.grammar.vo.GrammarErrorVO;
import org.jeecg.modules.study.grammar.vo.GrammarPointListVO;
import org.jeecg.modules.study.grammar.vo.GrammarPointVO;
import org.jeecg.modules.study.grammar.vo.GrammarTransformRequest;
import org.jeecg.modules.study.grammar.vo.GrammarTransformResultVO;
import org.jeecg.modules.study.scope.service.ChildScopeService;

/**
 * 英语语法域服务实现（V3）。
 *
 * <p><b>数据范围</b>：孩子端身份来自会话（{@code scopeService.ownChildId()}），
 * <b>不接受前端传来的 childId</b>；错句收藏馆按 child_id 隔离（R-04 / ADR-018）。</p>
 *
 * <p><b>R-02</b>：变形与检测都走确定性规则，判定来源恒为 {@code rule}，
 * 错句入池复用统一错题池，不自建错句表（F-GRM-06 / TC-02-12）。</p>
 *
 * @author jeecg-study
 */
@Service
@RequiredArgsConstructor
public class StudyGrammarServiceImpl implements StudyGrammarService {

    /** 来源模块 */
    private static final String SOURCE_MODULE = "GRAMMAR";

    /** 内容类型：句子 */
    private static final String CONTENT_TYPE = "SENTENCE";

    /** 学科编码 */
    private static final String SUBJECT_CODE = "EN";

    /** 版权类型：自编 */
    private static final String COPYRIGHT_SELF = "SELF";

    /** 即时改正次数上限 */
    private static final int MAX_RETRY = 1;

    private final StudyGrammarPointMapper grammarPointMapper;
    private final StudyGrammarErrorQueryMapper errorQueryMapper;
    private final GrammarTransformEngine transformEngine;
    private final GrammarErrorDetector errorDetector;
    private final StudyErrorPoolService errorPoolService;
    private final ChildScopeService scopeService;

    // ------------------------------------------------------------------ 语法点

    @Override
    public GrammarPointListVO listPoints(String examType) {
        String type = trim(examType);
        if (!type.isEmpty() && !GrammarExamType.isValid(type)) {
            throw new JeecgBootException("中考题型只能是单选、完形、语法填空、书面表达", 400);
        }
        List<StudyGrammarPoint> rows = grammarPointMapper.selectByExamType(type);
        List<GrammarPointVO> items = new ArrayList<>();
        if (rows != null) {
            for (StudyGrammarPoint row : rows) {
                if (row != null) {
                    items.add(toPointVO(row));
                }
            }
        }
        GrammarPointListVO vo = new GrammarPointListVO();
        vo.setItems(items);
        vo.setTotal(items.size());
        return vo;
    }

    @Override
    public GrammarPointVO getPoint(String grammarCode) {
        String code = trim(grammarCode);
        if (code.isEmpty()) {
            throw new JeecgBootException("语法点ID不能为空", 400);
        }
        StudyGrammarPoint point = grammarPointMapper.selectByCode(code);
        if (point == null) {
            throw new JeecgBootException("语法点不存在：" + code, 400);
        }
        return toPointVO(point);
    }

    // ------------------------------------------------------------------ 变形器

    @Override
    public GrammarTransformResultVO transform(GrammarTransformRequest request) {
        currentChildId();
        if (request == null) {
            throw new JeecgBootException("参数不能为空", 400);
        }
        return transformEngine.transform(request.getZhMeaning(), request.getGrammarCode());
    }

    // ------------------------------------------------------------------ 错误即入口

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GrammarDetectResultVO detect(GrammarDetectRequest request) {
        if (request == null) {
            throw new JeecgBootException("参数不能为空", 400);
        }
        String childId = currentChildId();
        String sentence = trim(request.getSentence());
        if (sentence.isEmpty()) {
            throw new JeecgBootException("句子不能为空", 400);
        }
        String errorCause = trim(request.getErrorCause());
        if (!errorCause.isEmpty() && !ErrorCause.isValid(errorCause)) {
            throw new JeecgBootException("错因必须是五维之一", StudyErrorCode.MISSING_ERROR_CAUSE);
        }
        if (errorCause.isEmpty()) {
            errorCause = ErrorCause.CONCEPT_UNCLEAR;
        }

        List<GrammarErrorVO> errors = errorDetector.detect(sentence);

        GrammarDetectResultVO vo = new GrammarDetectResultVO();
        vo.setOriginalSentence(sentence);
        vo.setHasError(errors != null && !errors.isEmpty());
        vo.setSource(GrammarSource.RULE);
        vo.setErrors(errors == null ? new ArrayList<>() : errors);
        vo.setCanRetry(vo.getHasError());
        vo.setMaxRetry(MAX_RETRY);
        vo.setExplainStatus(vo.getHasError() ? "NORMAL" : "UNAVAILABLE");
        if (vo.getHasError()) {
            vo.setErrorCode(reportToPool(childId, sentence, request.getGrammarCode(), errors, errorCause));
        }
        return vo;
    }

    @Override
    public GrammarErrorFeedbackVO errorDetail(GrammarErrorDetailRequest request) {
        currentChildId();
        if (request == null) {
            throw new JeecgBootException("参数不能为空", 400);
        }
        String sentence = trim(request.getSentence());
        String errorId = trim(request.getErrorId());
        if (sentence.isEmpty() || errorId.isEmpty()) {
            throw new JeecgBootException("句子与错误标记不能为空", 400);
        }
        List<GrammarErrorVO> errors = errorDetector.detect(sentence);
        GrammarErrorVO hit = null;
        if (errors != null) {
            for (GrammarErrorVO error : errors) {
                if (error != null && errorId.equals(error.getErrorId())) {
                    hit = error;
                    break;
                }
            }
        }
        if (hit == null) {
            throw new JeecgBootException("这个错误标记已经失效了，请重新提交一次", 400);
        }
        GrammarErrorFeedbackVO vo = new GrammarErrorFeedbackVO();
        vo.setOriginalSentence(sentence);
        vo.setError(hit);
        vo.setCanRetry(true);
        vo.setMaxRetry(MAX_RETRY);
        return vo;
    }

    // ------------------------------------------------------------------ 收藏馆

    @Override
    public GrammarCollectionResultVO collection(String errorCause) {
        String childId = currentChildId();
        String cause = trim(errorCause);
        if (!cause.isEmpty() && !ErrorCause.isValid(cause)) {
            throw new JeecgBootException("错因必须是五维之一", StudyErrorCode.MISSING_ERROR_CAUSE);
        }
        List<GrammarCollectionRow> rows = errorQueryMapper.selectGrammarSentences(childId);
        List<GrammarCollectionVO> items = new ArrayList<>();
        if (rows != null) {
            for (GrammarCollectionRow row : rows) {
                if (row == null) {
                    continue;
                }
                if (!cause.isEmpty() && !cause.equals(trim(row.getErrorCause()))) {
                    continue;
                }
                items.add(toCollectionVO(row));
            }
        }
        GrammarCollectionResultVO vo = new GrammarCollectionResultVO();
        vo.setItems(items);
        vo.setTotal(items.size());
        return vo;
    }

    // ------------------------------------------------------------------ 内部

    /**
     * 错句入统一错题池（F-GRM-06）。
     *
     * <p>幂等键是错题池约定的「孩子 + 内容类型 + 内容」，
     * 所以同一句话重复提交只入一次；内容ID 带上句子的短摘要，
     * 同一语法点下的<b>不同</b>错句各占一条，收藏馆才能筛出全部错句。</p>
     */
    private String reportToPool(String childId, String sentence, String grammarCode,
                                List<GrammarErrorVO> errors, String errorCause) {
        String code = trim(grammarCode);
        if (code.isEmpty() && errors != null && !errors.isEmpty() && errors.get(0) != null) {
            code = trim(errors.get(0).getGrammarCode());
        }
        String knowledgePointCode = null;
        if (!code.isEmpty()) {
            StudyGrammarPoint point = grammarPointMapper.selectByCode(code);
            if (point != null) {
                knowledgePointCode = point.getKnowledgePointCode();
            }
        }

        StudyErrorReportCommand command = new StudyErrorReportCommand();
        command.setChildId(childId);
        command.setSourceModule(SOURCE_MODULE);
        command.setContentType(CONTENT_TYPE);
        command.setContentId(contentIdOf(code, sentence));
        command.setKnowledgePointCode(knowledgePointCode);
        command.setSubjectCode(SUBJECT_CODE);
        command.setErrorCause(errorCause);
        command.setRawAnswer(sentence);
        command.setCorrectAnswer(correctedOf(errors, sentence));
        command.setJudgeSource(JudgeSource.RULE.getCode());
        command.setCopyrightType(COPYRIGHT_SELF);
        return errorPoolService.report(command);
    }

    /**
     * 内容ID = 语法点业务ID + 句子的短摘要。
     */
    private String contentIdOf(String grammarCode, String sentence) {
        String code = grammarCode == null || grammarCode.trim().isEmpty() ? "GRP-SENTENCE" : grammarCode.trim();
        return code + "#" + Integer.toHexString(sentence.hashCode() & 0x7fffffff);
    }

    /**
     * 改正后的句子：取第一条能被规则改正的错误；都改不了就保留原句。
     */
    private String correctedOf(List<GrammarErrorVO> errors, String sentence) {
        if (errors != null) {
            for (GrammarErrorVO error : errors) {
                if (error != null && error.getFix() != null && !error.getFix().trim().isEmpty()) {
                    return error.getFix();
                }
            }
        }
        return sentence;
    }

    private GrammarPointVO toPointVO(StudyGrammarPoint point) {
        GrammarPointVO vo = new GrammarPointVO();
        vo.setGrammarCode(point.getGrammarCode());
        vo.setTitle(point.getTitle());
        vo.setRule(point.getRule());
        vo.setExample(point.getExample());
        vo.setCounterExample(point.getCounterExample());
        vo.setExamType(point.getExamType());
        vo.setKnowledgePointCode(point.getKnowledgePointCode());
        return vo;
    }

    private GrammarCollectionVO toCollectionVO(GrammarCollectionRow row) {
        GrammarCollectionVO vo = new GrammarCollectionVO();
        vo.setErrorCode(row.getErrorCode());
        vo.setContentId(row.getContentId());
        vo.setErrorCause(row.getErrorCause());
        vo.setOriginalSentence(row.getOriginalSentence());
        vo.setCorrectedSentence(row.getCorrectedSentence());
        vo.setJudgeSource(row.getJudgeSource());
        vo.setMasteryState(row.getMasteryState());
        vo.setArchived(row.getReviewStatus() != null && row.getReviewStatus() == 2);
        vo.setNextReviewAt(row.getNextReviewAt());
        return vo;
    }

    /**
     * 当前登录孩子；家长等非孩子身份一律 4010。
     */
    private String currentChildId() {
        String childId = scopeService.ownChildId();
        if (childId == null || childId.trim().isEmpty()) {
            throw new StudyForbiddenException("当前登录身份不是孩子，无权访问学习数据");
        }
        return childId;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 归一化（保留给将来做大小写无关比较用）。
     */
    protected static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
