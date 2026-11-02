package org.jeecg.modules.study.grammar.gameplay.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;

import lombok.RequiredArgsConstructor;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.study.common.enums.JudgeSource;
import org.jeecg.modules.study.common.exception.StudyForbiddenException;
import org.jeecg.modules.study.error.constant.ErrorCause;
import org.jeecg.modules.study.error.service.StudyErrorPoolService;
import org.jeecg.modules.study.error.vo.StudyErrorReportCommand;
import org.jeecg.modules.study.grammar.engine.GrammarErrorDetector;
import org.jeecg.modules.study.grammar.entity.StudyGrammarPoint;
import org.jeecg.modules.study.grammar.gameplay.constant.GrammarGameType;
import org.jeecg.modules.study.grammar.gameplay.engine.GrammarDetectiveRoundModel;
import org.jeecg.modules.study.grammar.gameplay.engine.GrammarGameRoundBuilder;
import org.jeecg.modules.study.grammar.gameplay.engine.GrammarGameText;
import org.jeecg.modules.study.grammar.gameplay.engine.GrammarTransformRoundModel;
import org.jeecg.modules.study.grammar.gameplay.entity.StudyGrammarGameQuestion;
import org.jeecg.modules.study.grammar.gameplay.entity.StudyGrammarGameRecord;
import org.jeecg.modules.study.grammar.gameplay.mapper.StudyGrammarGameQuestionMapper;
import org.jeecg.modules.study.grammar.gameplay.mapper.StudyGrammarGameRecordMapper;
import org.jeecg.modules.study.grammar.gameplay.service.GrammarGameService;
import org.jeecg.modules.study.grammar.gameplay.vo.GrammarDetectiveJudgeRequest;
import org.jeecg.modules.study.grammar.gameplay.vo.GrammarDetectiveJudgeVO;
import org.jeecg.modules.study.grammar.gameplay.vo.GrammarDetectiveRoundVO;
import org.jeecg.modules.study.grammar.gameplay.vo.GrammarDetectiveSentenceVO;
import org.jeecg.modules.study.grammar.gameplay.vo.GrammarGameCollectionItemVO;
import org.jeecg.modules.study.grammar.gameplay.vo.GrammarGameCollectionResultVO;
import org.jeecg.modules.study.grammar.gameplay.vo.GrammarGameProgressVO;
import org.jeecg.modules.study.grammar.gameplay.vo.GrammarGameTypeProgressVO;
import org.jeecg.modules.study.grammar.gameplay.vo.GrammarTransformJudgeRequest;
import org.jeecg.modules.study.grammar.gameplay.vo.GrammarTransformJudgeVO;
import org.jeecg.modules.study.grammar.gameplay.vo.GrammarTransformRoundVO;
import org.jeecg.modules.study.grammar.mapper.StudyGrammarErrorQueryMapper;
import org.jeecg.modules.study.grammar.mapper.StudyGrammarPointMapper;
import org.jeecg.modules.study.grammar.vo.GrammarCollectionRow;
import org.jeecg.modules.study.grammar.vo.GrammarErrorVO;
import org.jeecg.modules.study.scope.service.ChildScopeService;

/**
 * 语法玩法服务实现（F-GRM-04 趣味玩法）。
 *
 * <p><b>R-01 反搜题</b>：取题只给题目材料，答案与讲解一律等孩子作答之后才给。</p>
 *
 * <p><b>R-02 零 AI 依赖</b>：句子侦探的「哪句错」与变形闯关的「写得对不对」
 * 全部由 {@link GrammarErrorDetector} 的确定性规则算出，
 * 判定来源恒为 {@code rule}。</p>
 *
 * <p><b>R-04 数据隔离</b>：孩子身份来自会话（不接受前端传来的 childId），
 * 闯关记录与错句收藏馆一律按 {@code child_id} 过滤。</p>
 *
 * @author jeecg-study
 */
@Service
@RequiredArgsConstructor
public class GrammarGameServiceImpl implements GrammarGameService {

    /** 来源模块 */
    private static final String SOURCE_MODULE = "GRAMMAR";

    /** 内容类型：句子 */
    private static final String CONTENT_TYPE = "SENTENCE";

    /** 学科编码 */
    private static final String SUBJECT_CODE = "EN";

    /** 版权类型：自编 */
    private static final String COPYRIGHT_SELF = "SELF";

    /** 候选题上限 */
    private static final int MAX_CANDIDATE = 50;

    /** 孩子写下的句子最长入库长度 */
    private static final int MAX_SUBMITTED = 512;

    /** 变形闯关的任务说明 */
    private static final String PROMPT_TRANSFORM = "把这句话写成通顺的英文，写完提交就行";

    /** 句子太短时的提示 */
    private static final String HINT_TOO_SHORT = "这句太短了，写一个完整的句子试试";

    /** 没说到点上的提示 */
    private static final String HINT_OFF_TOPIC = "这句话没有用到目标表达里的关键词，换个说法试试";

    private final StudyGrammarGameQuestionMapper questionMapper;

    private final StudyGrammarGameRecordMapper recordMapper;

    private final StudyGrammarErrorQueryMapper errorQueryMapper;

    private final StudyGrammarPointMapper grammarPointMapper;

    private final GrammarGameRoundBuilder roundBuilder;

    private final GrammarErrorDetector errorDetector;

    private final StudyErrorPoolService errorPoolService;

    private final ChildScopeService scopeService;

    // ------------------------------------------------------------------ 句子侦探

    @Override
    public GrammarDetectiveRoundVO detectiveNext() {
        String childId = currentChildId();
        List<StudyGrammarGameQuestion> candidates =
                questionMapper.selectByType(GrammarGameType.DETECTIVE, MAX_CANDIDATE);
        Set<String> passed = passedCodes(childId, GrammarGameType.DETECTIVE);

        GrammarDetectiveRoundModel fallback = null;
        for (StudyGrammarGameQuestion question : candidates) {
            GrammarDetectiveRoundModel round = roundBuilder.buildDetective(question);
            if (round == null) {
                continue;
            }
            if (fallback == null) {
                fallback = round;
            }
            if (!passed.contains(round.getQuestionCode())) {
                return toDetectiveRoundVO(round);
            }
        }
        if (fallback != null) {
            return toDetectiveRoundVO(fallback);
        }
        throw new JeecgBootException("句子侦探的题库还在准备中，先去背几个单词再来玩", 400);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GrammarDetectiveJudgeVO detectiveJudge(GrammarDetectiveJudgeRequest request) {
        String childId = currentChildId();
        if (request == null) {
            throw new JeecgBootException("参数不能为空", 400);
        }
        String roundId = trim(request.getRoundId());
        Integer picked = request.getPickedIndex();
        if (roundId.isEmpty() || picked == null) {
            throw new JeecgBootException("先选一句再提交", 400);
        }
        GrammarDetectiveRoundModel round = rebuildDetective(roundId);
        if (picked < 1 || picked > round.getSentences().size()) {
            throw new JeecgBootException("请在给出的句子里选一句", 400);
        }

        boolean correct = picked == round.getWrongPosition();
        GrammarErrorVO error = round.getWrongErrors().get(0);

        GrammarDetectiveJudgeVO vo = new GrammarDetectiveJudgeVO();
        vo.setRoundId(round.getQuestionCode());
        vo.setCorrect(correct);
        vo.setPickedIndex(picked);
        vo.setWrongPosition(round.getWrongPosition());
        vo.setWrongSentence(round.getWrongSentence());
        vo.setRule(error.getRule());
        vo.setExample(error.getExample());
        vo.setCounterExample(error.getCounterExample());
        vo.setFix(error.getFix());
        vo.setJudgeSource(JudgeSource.RULE.getCode());

        String errorCode = null;
        if (!correct) {
            // 没找出来的错句进统一错题池——这个错误孩子还没掌握
            errorCode = reportToPool(childId, round.getWrongSentence(), round.getGrammarCode(),
                    error, round.getQuestionCode(), round.getWrongSentence());
            vo.setErrorCode(errorCode);
        }
        record(childId, GrammarGameType.DETECTIVE, round.getQuestionCode(), round.getGrammarCode(),
                pickedSentence(round, picked), correct, correct ? null : ErrorCause.CONCEPT_UNCLEAR,
                errorCode, request.getDurationMs());
        return vo;
    }

    // ------------------------------------------------------------------ 变形闯关

    @Override
    public GrammarTransformRoundVO transformNext() {
        String childId = currentChildId();
        List<StudyGrammarGameQuestion> candidates =
                questionMapper.selectByType(GrammarGameType.TRANSFORM, MAX_CANDIDATE);
        Set<String> passed = passedCodes(childId, GrammarGameType.TRANSFORM);

        GrammarTransformRoundModel fallback = null;
        for (StudyGrammarGameQuestion question : candidates) {
            GrammarTransformRoundModel round = roundBuilder.buildTransform(question);
            if (round == null) {
                continue;
            }
            if (fallback == null) {
                fallback = round;
            }
            if (!passed.contains(round.getQuestionCode())) {
                return toTransformRoundVO(round);
            }
        }
        if (fallback != null) {
            return toTransformRoundVO(fallback);
        }
        throw new JeecgBootException("语法变形闯关的题库还在准备中，先去背几个单词再来玩", 400);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GrammarTransformJudgeVO transformJudge(GrammarTransformJudgeRequest request) {
        String childId = currentChildId();
        if (request == null) {
            throw new JeecgBootException("参数不能为空", 400);
        }
        String roundId = trim(request.getRoundId());
        String sentence = trim(request.getSentence());
        if (roundId.isEmpty()) {
            throw new JeecgBootException("回合ID不能为空", 400);
        }
        if (sentence.isEmpty()) {
            throw new JeecgBootException("先写一句再提交", 400);
        }
        GrammarTransformRoundModel round = rebuildTransform(roundId);

        List<GrammarErrorVO> errors = errorDetector.detect(sentence);
        List<String> childTokens = GrammarGameText.tokens(sentence);
        List<String> targetContent = GrammarGameText.contentTokens(round.getTargetText());
        int hit = GrammarGameText.overlap(targetContent, GrammarGameText.contentTokens(sentence));
        int required = GrammarGameText.requiredOverlap(targetContent.size());

        boolean noGrammarError = errors.isEmpty();
        boolean longEnough = childTokens.size() >= 3;
        boolean onTopic = hit >= required;
        boolean correct = noGrammarError && longEnough && onTopic;

        GrammarTransformJudgeVO vo = new GrammarTransformJudgeVO();
        vo.setRoundId(round.getQuestionCode());
        vo.setCorrect(correct);
        vo.setJudgeSource(JudgeSource.RULE.getCode());

        String errorCode = null;
        if (correct) {
            vo.setReferenceText(round.getTargetText());
        } else {
            if (!noGrammarError) {
                GrammarErrorVO error = errors.get(0);
                vo.setRule(error.getRule());
                vo.setExample(error.getExample());
                vo.setCounterExample(error.getCounterExample());
                vo.setFix(error.getFix());
                vo.setHint(error.getRule());
            } else if (!longEnough) {
                vo.setHint(HINT_TOO_SHORT);
            } else {
                vo.setHint(HINT_OFF_TOPIC);
            }
            errorCode = reportToPool(childId, sentence, round.getGrammarCode(),
                    errors.isEmpty() ? null : errors.get(0), round.getQuestionCode(),
                    errors.isEmpty() ? round.getTargetText() : errors.get(0).getFix());
            vo.setErrorCode(errorCode);
        }
        record(childId, GrammarGameType.TRANSFORM, round.getQuestionCode(), round.getGrammarCode(),
                sentence, correct, correct ? null : ErrorCause.CONCEPT_UNCLEAR, errorCode,
                request.getDurationMs());
        return vo;
    }

    // ------------------------------------------------------------------ 收藏馆与进度

    @Override
    public GrammarGameCollectionResultVO collection(String errorCause) {
        String childId = currentChildId();
        String cause = trim(errorCause);
        if (!cause.isEmpty() && !ErrorCause.isValid(cause)) {
            throw new JeecgBootException("错因必须是五维之一", 400);
        }
        List<GrammarCollectionRow> rows = errorQueryMapper.selectGrammarSentences(childId);
        List<GrammarGameCollectionItemVO> items = new ArrayList<>();
        if (rows != null) {
            for (GrammarCollectionRow row : rows) {
                if (row == null) {
                    continue;
                }
                if (!cause.isEmpty() && !cause.equals(trim(row.getErrorCause()))) {
                    continue;
                }
                GrammarGameCollectionItemVO item = new GrammarGameCollectionItemVO();
                item.setErrorCode(row.getErrorCode());
                item.setOriginalSentence(row.getOriginalSentence());
                item.setCorrectedSentence(row.getCorrectedSentence());
                item.setErrorCause(row.getErrorCause());
                item.setJudgeSource(row.getJudgeSource());
                item.setArchived(row.getReviewStatus() != null && row.getReviewStatus() == 2);
                item.setNextReviewAt(row.getNextReviewAt());
                items.add(item);
            }
        }
        GrammarGameCollectionResultVO vo = new GrammarGameCollectionResultVO();
        vo.setItems(items);
        vo.setTotal(items.size());
        return vo;
    }

    @Override
    public GrammarGameProgressVO progress() {
        String childId = currentChildId();
        List<GrammarGameTypeProgressVO> items = new ArrayList<>();
        for (String type : GrammarGameType.ALL) {
            GrammarGameTypeProgressVO item = new GrammarGameTypeProgressVO();
            item.setGameType(type);
            item.setGameTypeName(GrammarGameType.nameOf(type));
            item.setTotalCount(questionMapper.countByType(type));
            item.setPassedCount(passedCodes(childId, type).size());
            item.setAnsweredCount(recordMapper.countAnswered(childId, type));
            item.setLastPlayedAt(recordMapper.selectLastPlayedAt(childId, type));
            items.add(item);
        }
        GrammarGameProgressVO vo = new GrammarGameProgressVO();
        vo.setItems(items);
        return vo;
    }

    // ------------------------------------------------------------------ 内部

    /**
     * 按回合ID 重新组装句子侦探回合（服务端唯一真源）。
     */
    private GrammarDetectiveRoundModel rebuildDetective(String roundId) {
        StudyGrammarGameQuestion question = questionMapper.selectByCode(roundId);
        if (question == null) {
            throw new JeecgBootException("这一关已经换了，重新取一次题吧", 400);
        }
        GrammarDetectiveRoundModel round = roundBuilder.buildDetective(question);
        if (round == null) {
            throw new JeecgBootException("这一关的题目还没准备好，换一关试试", 400);
        }
        return round;
    }

    /**
     * 按回合ID 重新组装变形闯关回合。
     */
    private GrammarTransformRoundModel rebuildTransform(String roundId) {
        StudyGrammarGameQuestion question = questionMapper.selectByCode(roundId);
        if (question == null) {
            throw new JeecgBootException("这一关已经换了，重新取一次题吧", 400);
        }
        GrammarTransformRoundModel round = roundBuilder.buildTransform(question);
        if (round == null) {
            throw new JeecgBootException("这一关的题目还没准备好，换一关试试", 400);
        }
        return round;
    }

    /**
     * 某孩子某玩法已通过的题目。
     */
    private Set<String> passedCodes(String childId, String gameType) {
        List<String> codes = recordMapper.selectPassedQuestionCodes(childId, gameType);
        return codes == null ? new HashSet<>() : new HashSet<>(codes);
    }

    /**
     * 取孩子指出的那一句（入池用）。
     */
    private String pickedSentence(GrammarDetectiveRoundModel round, int picked) {
        for (GrammarDetectiveRoundModel.DetectiveSentence sentence : round.getSentences()) {
            if (sentence.getPosition() == picked) {
                return sentence.getText();
            }
        }
        return round.getWrongSentence();
    }

    /**
     * 错句入统一错题池（不自建错句表，F-GRM-06）。
     *
     * @param childId      孩子ID
     * @param rawAnswer    孩子的作答 / 出错的句子
     * @param grammarCode  关联语法点业务ID
     * @param error        规则检出的错误；没有规则错误时传 null
     * @param questionCode 题目业务ID（进内容ID，保证同一题只入一次）
     * @param corrected    改正后的句子
     * @return 错题业务ID
     */
    private String reportToPool(String childId, String rawAnswer, String grammarCode,
                                GrammarErrorVO error, String questionCode, String corrected) {
        StudyErrorReportCommand command = new StudyErrorReportCommand();
        command.setChildId(childId);
        command.setSourceModule(SOURCE_MODULE);
        command.setContentType(CONTENT_TYPE);
        command.setContentId(contentIdOf(questionCode, rawAnswer));
        command.setKnowledgePointCode(knowledgePointOf(grammarCode));
        command.setSubjectCode(SUBJECT_CODE);
        command.setErrorCause(ErrorCause.CONCEPT_UNCLEAR);
        command.setRawAnswer(rawAnswer);
        command.setCorrectAnswer(corrected == null || corrected.trim().isEmpty()
                ? (error == null ? rawAnswer : error.getFix()) : corrected);
        command.setJudgeSource(JudgeSource.RULE.getCode());
        command.setCopyrightType(COPYRIGHT_SELF);
        return errorPoolService.report(command);
    }

    /**
     * 内容ID = 题目业务ID + 句子的短摘要（同一题同一句只入一次）。
     */
    private String contentIdOf(String questionCode, String rawAnswer) {
        String code = questionCode == null || questionCode.trim().isEmpty() ? "GAM" : questionCode.trim();
        return code + "#" + Integer.toHexString(rawAnswer.hashCode() & 0x7fffffff);
    }

    /**
     * 语法点对应的考点业务ID（错题池按考点追溯用）。
     */
    private String knowledgePointOf(String grammarCode) {
        String code = trim(grammarCode);
        if (code.isEmpty()) {
            return null;
        }
        StudyGrammarPoint point = grammarPointMapper.selectByCode(code);
        return point == null ? null : point.getKnowledgePointCode();
    }

    /**
     * 写一条闯关记录（行为表，按孩子隔离）。
     */
    private void record(String childId, String gameType, String questionCode, String grammarCode,
                        String submitted, boolean correct, String errorCause, String errorCode,
                        Integer durationMs) {
        Date now = new Date();
        StudyGrammarGameRecord record = new StudyGrammarGameRecord();
        record.setId(IdWorker.get32UUID());
        record.setChildId(childId);
        record.setGameType(gameType);
        record.setQuestionCode(questionCode);
        record.setGrammarCode(grammarCode);
        record.setSubmitted(truncate(submitted));
        record.setCorrect(correct ? 1 : 0);
        record.setJudgeSource(JudgeSource.RULE.getCode());
        record.setErrorCause(errorCause);
        record.setErrorCode(errorCode);
        record.setDurationMs(durationMs);
        record.setDelFlag(0);
        record.setCreateBy(scopeService.currentUserId());
        record.setCreateTime(now);
        record.setUpdateBy(scopeService.currentUserId());
        record.setUpdateTime(now);
        recordMapper.insertRecord(record);
    }

    private GrammarDetectiveRoundVO toDetectiveRoundVO(GrammarDetectiveRoundModel round) {
        GrammarDetectiveRoundVO vo = new GrammarDetectiveRoundVO();
        vo.setRoundId(round.getQuestionCode());
        vo.setGrammarCode(round.getGrammarCode());
        List<GrammarDetectiveSentenceVO> sentences = new ArrayList<>();
        for (GrammarDetectiveRoundModel.DetectiveSentence sentence : round.getSentences()) {
            GrammarDetectiveSentenceVO item = new GrammarDetectiveSentenceVO();
            item.setPosition(sentence.getPosition());
            item.setText(sentence.getText());
            item.setSentenceCode(sentence.getSentenceCode());
            sentences.add(item);
        }
        vo.setSentences(sentences);
        vo.setTotal(sentences.size());
        return vo;
    }

    private GrammarTransformRoundVO toTransformRoundVO(GrammarTransformRoundModel round) {
        GrammarTransformRoundVO vo = new GrammarTransformRoundVO();
        vo.setRoundId(round.getQuestionCode());
        vo.setGrammarCode(round.getGrammarCode());
        vo.setMeaning(round.getMeaning());
        vo.setPrompt(PROMPT_TRANSFORM);
        vo.setTargetSentenceCode(round.getTargetSentenceCode());
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

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > MAX_SUBMITTED ? value.substring(0, MAX_SUBMITTED) : value;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

}
