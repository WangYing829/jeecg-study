package org.jeecg.modules.study.word.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;

import lombok.RequiredArgsConstructor;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.study.common.constant.StudyConstants;
import org.jeecg.modules.study.common.constant.StudyErrorCode;
import org.jeecg.modules.study.common.enums.JudgeSource;
import org.jeecg.modules.study.common.exception.StudyForbiddenException;
import org.jeecg.modules.study.error.constant.ErrorCause;
import org.jeecg.modules.study.error.service.StudyErrorPoolService;
import org.jeecg.modules.study.error.vo.ReviewQueueItemVO;
import org.jeecg.modules.study.error.vo.StudyErrorReportCommand;
import org.jeecg.modules.study.family.service.StudyChildService;
import org.jeecg.modules.study.judge.engine.JudgeRequest;
import org.jeecg.modules.study.judge.engine.JudgeResult;
import org.jeecg.modules.study.judge.engine.strategy.ExactMatchJudgeStrategy;
import org.jeecg.modules.study.judge.service.JudgeEngine;
import org.jeecg.modules.study.scope.service.ChildScopeService;
import org.jeecg.modules.study.word.constant.WordConstants;
import org.jeecg.modules.study.word.entity.StudyWord;
import org.jeecg.modules.study.word.enums.WordFeedback;
import org.jeecg.modules.study.word.enums.WordLineType;
import org.jeecg.modules.study.word.mapper.StudyWordMapper;
import org.jeecg.modules.study.word.mapper.StudyWordPracticeRecordMapper;
import org.jeecg.modules.study.word.service.StudyWordService;
import org.jeecg.modules.study.word.vo.WordDueVO;
import org.jeecg.modules.study.word.vo.WordFeedbackOptionVO;
import org.jeecg.modules.study.word.vo.WordFeedbackRequest;
import org.jeecg.modules.study.word.vo.WordFeedbackVO;
import org.jeecg.modules.study.word.vo.WordKnownTestRequest;
import org.jeecg.modules.study.word.vo.WordKnownTestVO;
import org.jeecg.modules.study.word.vo.WordListItemVO;
import org.jeecg.modules.study.word.vo.WordListVO;
import org.jeecg.modules.study.word.vo.WordPracticeRequest;
import org.jeecg.modules.study.word.vo.WordPracticeResultVO;
import org.jeecg.modules.study.word.vo.WordPracticeVO;
import org.jeecg.modules.study.word.vo.WordStageVO;
import org.jeecg.modules.study.word.vo.WordStatsVO;
import org.jeecg.modules.study.word.vo.WordUnitCompleteVO;
import org.jeecg.modules.study.word.vo.WordUnitRequest;
import org.jeecg.modules.study.word.vo.WordUnitVO;

/**
 * 背单词服务实现。
 *
 * <p>要点：</p>
 * <ul>
 *   <li><b>红线 R-01</b>：取词 / 词表 / 下一词一律<b>不含拼写</b>，
 *       例句与短文里的目标词也挖空；正确拼写只在判定接口（孩子作答之后）回传。</li>
 *   <li><b>红线 R-02</b>：拼写判定走 {@code JudgeEngine} 的 EXACT 策略，
 *       判定来源恒为 rule。</li>
 *   <li><b>红线 R-04</b>：响应字段与文案不含排名 / 百分位 / 等级，也不做家庭内对照。</li>
 *   <li>熟词（{@code known_flag=1}）不进队列，但过滤量在词表与统计里可见。</li>
 *   <li>课内背错的词写入统一错题池并置 {@code upgrade_flag=1}，
 *       进课外线优先复习项。</li>
 * </ul>
 *
 * @author jeecg-study
 */
@Service
@RequiredArgsConstructor
public class StudyWordServiceImpl implements StudyWordService {

    /** 作答记录分表名后缀格式 */
    private static final DateTimeFormatter RECORD_TABLE_SUFFIX = DateTimeFormatter.ofPattern("yyyyMM");

    /** 作答记录分表名前缀 */
    private static final String RECORD_TABLE_PREFIX = "study_practice_record_";

    /** 同一词同一档 10 秒内的重复反馈只生效一次（《接口设计说明书》§12.1） */
    private static final long FEEDBACK_IDEMPOTENT_SECONDS = 10L;

    /** 默认每页条数 */
    private static final int DEFAULT_PAGE_SIZE = 50;

    /** 最大每页条数 */
    private static final int MAX_PAGE_SIZE = 200;

    private final StudyWordMapper wordMapper;

    private final StudyWordPracticeRecordMapper recordMapper;

    private final ChildScopeService scopeService;

    private final StudyChildService childService;

    private final StudyErrorPoolService errorPoolService;

    private final JudgeEngine judgeEngine;

    // ------------------------------------------------------------------ 词表

    @Override
    public List<WordUnitVO> listUnits(String lineType, String textbookVersionId) {
        WordLineType line = requireLineType(lineType);
        List<WordUnitVO> units = new ArrayList<>();
        if (line == WordLineType.OUT) {
            // 课外线没有教材单元，返回一个虚拟单元承载词表
            WordUnitVO unit = new WordUnitVO();
            unit.setLineType(line.getCode());
            unit.setUnitCode("");
            unit.setUnitOrder(1);
            unit.setTextbookVersionId(null);
            unit.setWordCount(wordMapper.countByLine(line.getCode()));
            units.add(unit);
            return units;
        }
        if (textbookVersionId == null || textbookVersionId.trim().isEmpty()) {
            throw new JeecgBootException("课内单元必须指定教材版本", 400);
        }
        List<WordUnitVO> stats = wordMapper.selectUnitStats(textbookVersionId.trim());
        if (stats == null) {
            return units;
        }
        for (WordUnitVO unit : stats) {
            unit.setLineType(line.getCode());
            unit.setTextbookVersionId(textbookVersionId.trim());
            units.add(unit);
        }
        return units;
    }

    @Override
    public WordListVO listWords(String lineType, String textbookVersionId, String unitCode,
                                Integer pageNo, Integer pageSize) {
        WordLineType line = requireLineType(lineType);
        int limit = normalizePageSize(pageSize);
        int offset = normalizeOffset(pageNo, limit);

        boolean unitScoped = line == WordLineType.IN
                && unitCode != null && !unitCode.trim().isEmpty();
        String versionId = textbookVersionId == null ? null : textbookVersionId.trim();
        String unit = unitCode == null ? null : unitCode.trim();

        List<StudyWord> words = unitScoped
                ? wordMapper.selectByUnit(versionId, unit, limit, offset)
                : wordMapper.selectByLine(line.getCode(), limit, offset);

        int total = unitScoped
                ? wordMapper.countByUnit(versionId, unit)
                : wordMapper.countByLine(line.getCode());
        int filtered = wordMapper.countKnownWords(line.getCode(), unitScoped ? versionId : null,
                unitScoped ? unit : null);

        WordListVO vo = new WordListVO();
        vo.setLineType(line.getCode());
        vo.setTextbookVersionId(versionId);
        vo.setUnitCode(unitScoped ? unit : null);
        vo.setTotal(total);
        vo.setFilteredKnownCount(filtered);
        vo.setItems(toListItems(words, null));
        return vo;
    }

    // ------------------------------------------------------------------ 取词

    @Override
    public WordPracticeVO nextWord(String lineType, String textbookVersionId, String unitCode) {
        WordLineType line = requireLineType(lineType);
        List<StudyWord> candidates;
        boolean unitScoped = line == WordLineType.IN
                && unitCode != null && !unitCode.trim().isEmpty();
        if (unitScoped) {
            candidates = wordMapper.selectByUnit(
                    textbookVersionId == null ? null : textbookVersionId.trim(),
                    unitCode.trim(), MAX_PAGE_SIZE, 0);
        } else {
            candidates = wordMapper.selectDueWords(line.getCode(), today());
        }
        StudyWord target = pickNext(candidates);
        if (target == null) {
            throw new JeecgBootException("该单元暂时没有待学的单词", 404);
        }
        return toPracticeVO(target, candidates);
    }

    // ------------------------------------------------------------------ 作答

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WordPracticeResultVO practice(WordPracticeRequest request) {
        if (request == null || request.getWordCode() == null || request.getWordCode().trim().isEmpty()) {
            throw new JeecgBootException("单词业务ID不能为空", 400);
        }
        String childId = resolveChildId(request.getChildCode());
        StudyWord word = requireWord(request.getWordCode());

        JudgeRequest judgeRequest = new JudgeRequest();
        judgeRequest.setContentType(ExactMatchJudgeStrategy.CONTENT_TYPE);
        judgeRequest.setExpectedAnswer(word.getSpelling());
        judgeRequest.setActualAnswer(request.getSpelling());
        JudgeResult judgeResult = judgeEngine.judge(judgeRequest);
        boolean correct = judgeResult.isCorrect();

        String errorCode = null;
        boolean upgraded = false;
        if (!correct) {
            // 答错必须先选错因，与统一错题池口径一致（TC-02-04）
            if (!ErrorCause.isValid(request.getErrorCause())) {
                throw new JeecgBootException("需要先选择错因", StudyErrorCode.MISSING_ERROR_CAUSE);
            }
            errorCode = reportToErrorPool(childId, word, request.getErrorCause().trim(),
                    request.getSpelling(), judgeResult.getJudgeSource());
            upgraded = markUpgradedIfTextbook(word, errorCode);
        }

        insertPracticeRecord(childId, WordConstants.CONTENT_TYPE_WORD, word.getWordCode(),
                request.getSpelling(), correct ? 1 : 0, judgeResult.getJudgeSource(),
                correct ? null : request.getErrorCause(), errorCode, request.getDurationMs());

        WordPracticeResultVO vo = new WordPracticeResultVO();
        vo.setWordCode(word.getWordCode());
        vo.setCorrect(correct);
        vo.setJudgeSource(judgeResult.getJudgeSource());
        vo.setBasis(judgeResult.getBasis());
        vo.setCorrectSpelling(word.getSpelling());
        vo.setErrorCode(errorCode);
        vo.setUpgraded(upgraded);
        vo.setMemoryStrength(word.getMemoryStrength());
        vo.setDecayLambda(word.getDecayLambda());
        vo.setNextReviewAt(word.getNextReviewAt());
        return vo;
    }

    // ------------------------------------------------------------------ 四档反馈

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WordFeedbackVO feedback(WordFeedbackRequest request) {
        if (request == null || request.getWordCode() == null || request.getWordCode().trim().isEmpty()) {
            throw new JeecgBootException("单词业务ID不能为空", 400);
        }
        WordFeedback feedback = WordFeedback.of(request.getFeedback());
        if (feedback == null) {
            throw new JeecgBootException("反馈档位必须是 认识 / 模糊 / 忘记 / 熟知", 400);
        }
        String childId = resolveChildId(request.getChildCode());
        StudyWord word = requireWord(request.getWordCode());

        if (isRepeatedFeedback(word, feedback)) {
            // 同一词同一档 10 秒内的重复反馈只生效一次：直接回当前状态，不重复入池、不重复记账
            return toFeedbackVO(word, feedback, null, false);
        }

        BigDecimal strength = feedback.nextStrength(word.getMemoryStrength());
        BigDecimal lambda = feedback.nextLambda(word.getDecayLambda());
        Date nextReviewAt = reviewDate(strength, lambda);
        wordMapper.updateForgettingParams(word.getWordCode(), strength, lambda,
                feedback.getCode(), nextReviewAt, scopeService.currentUserId());
        word.setMemoryStrength(strength);
        word.setDecayLambda(lambda);
        word.setNextReviewAt(nextReviewAt);
        word.setFeedbackCount((word.getFeedbackCount() == null ? 0 : word.getFeedbackCount()) + 1);
        word.setLastFeedback(feedback.getCode());

        String errorCode = null;
        boolean upgraded = false;
        if (feedback.isForgotten()) {
            // 「忘记」= 没记住，进统一错题池；错因缺省按「概念不清」处理
            String cause = ErrorCause.isValid(request.getErrorCause())
                    ? request.getErrorCause().trim() : ErrorCause.CONCEPT_UNCLEAR;
            errorCode = reportToErrorPool(childId, word, cause, null, JudgeSource.RULE.getCode());
            upgraded = markUpgradedIfTextbook(word, errorCode);
        }

        insertPracticeRecord(childId, WordConstants.CONTENT_TYPE_WORD, word.getWordCode(),
                null, feedback.isForgotten() ? 0 : 1, JudgeSource.RULE.getCode(),
                feedback.isForgotten() ? ErrorCause.CONCEPT_UNCLEAR : null, errorCode, null);

        return toFeedbackVO(word, feedback, errorCode, upgraded);
    }

    private WordFeedbackVO toFeedbackVO(StudyWord word, WordFeedback feedback,
                                        String errorCode, boolean upgraded) {
        WordFeedbackVO vo = new WordFeedbackVO();
        vo.setWordCode(word.getWordCode());
        vo.setFeedback(feedback.getCode());
        vo.setFeedbackLabel(feedback.getLabel());
        vo.setMemoryStrength(word.getMemoryStrength());
        vo.setDecayLambda(word.getDecayLambda());
        vo.setFeedbackCount(word.getFeedbackCount());
        vo.setNextReviewAt(word.getNextReviewAt());
        vo.setLineType(word.getLineType());
        vo.setUpgraded(upgraded);
        vo.setErrorCode(errorCode);
        return vo;
    }

    @Override
    public List<WordFeedbackOptionVO> feedbackOptions() {
        List<WordFeedbackOptionVO> options = new ArrayList<>();
        for (WordFeedback feedback : WordFeedback.values()) {
            WordFeedbackOptionVO option = new WordFeedbackOptionVO();
            option.setFeedback(feedback.getCode());
            option.setLabel(feedback.getLabel());
            option.setForgotten(feedback.isForgotten());
            options.add(option);
        }
        return options;
    }

    // ------------------------------------------------------------------ 熟词

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WordKnownTestVO knownTest(WordKnownTestRequest request) {
        if (request == null) {
            throw new JeecgBootException("熟词测试入参不能为空", 400);
        }
        WordLineType line = requireLineType(request.getLineType());
        List<String> wordCodes = request.getWordCodes();
        int marked = 0;
        if (wordCodes != null && !wordCodes.isEmpty()) {
            marked = wordMapper.markKnown(wordCodes);
        }
        int filtered = wordMapper.countKnownWords(line.getCode(), null, null);
        int total = wordMapper.countByLine(line.getCode());

        WordKnownTestVO vo = new WordKnownTestVO();
        vo.setLineType(line.getCode());
        vo.setMarkedCount(marked);
        vo.setFilteredKnownCount(filtered);
        vo.setQueueWordCount(Math.max(total - filtered, 0));
        return vo;
    }

    // ------------------------------------------------------------------ 复习与统计

    @Override
    public WordDueVO dueWords(String lineType, String childCode) {
        WordLineType line = requireLineType(lineType);
        String childId = resolveChildId(childCode);
        List<StudyWord> due = wordMapper.selectDueWords(line.getCode(), today());
        List<String> upgradedCodes = wordMapper.selectUpgradedWordCodes(childId);

        List<WordListItemVO> upgradeItems = new ArrayList<>();
        if (upgradedCodes != null) {
            for (String wordCode : upgradedCodes) {
                StudyWord word = wordMapper.selectByWordCode(wordCode);
                if (word == null) {
                    continue;
                }
                // 课内词升级到课外线：课外线上优先给同词的课外行
                StudyWord target = word;
                if (WordLineType.OUT == line) {
                    StudyWord outWord = wordMapper.selectBySpellingAndLine(
                            word.getSpelling(), WordLineType.OUT.getCode());
                    if (outWord != null) {
                        target = outWord;
                    }
                }
                WordListItemVO item = toListItem(target, Boolean.TRUE);
                if (item != null) {
                    upgradeItems.add(item);
                }
            }
        }

        WordDueVO vo = new WordDueVO();
        vo.setLineType(line.getCode());
        vo.setTotalDue(due == null ? 0 : due.size());
        vo.setFilteredKnownCount(wordMapper.countKnownWords(line.getCode(), null, null));
        vo.setItems(toListItems(due, null));
        vo.setUpgradeItems(upgradeItems);
        return vo;
    }

    @Override
    public WordStatsVO stats(String lineType, String childCode) {
        WordLineType line = requireLineType(lineType);
        String childId = resolveChildId(childCode);
        int total = wordMapper.countByLine(line.getCode());
        int filtered = wordMapper.countKnownWords(line.getCode(), null, null);
        List<StudyWord> due = wordMapper.selectDueWords(line.getCode(), today());
        List<ReviewQueueItemVO> queue = errorPoolService.todayQueue(childId);

        WordStatsVO vo = new WordStatsVO();
        vo.setLineType(line.getCode());
        vo.setTotalWords(total);
        vo.setQueueWords(Math.max(total - filtered, 0));
        vo.setFilteredKnownCount(filtered);
        vo.setLearnedWords(wordMapper.countLearnedWords(line.getCode()));
        vo.setDueWords(due == null ? 0 : due.size());
        vo.setReviewPending(queue == null ? 0 : queue.size());
        vo.setStreakDays(streakDays(childId));
        return vo;
    }

    // ------------------------------------------------------------------ 单元

    @Override
    public WordListVO enterUnit(WordUnitRequest request) {
        if (request == null || request.getUnitCode() == null || request.getUnitCode().trim().isEmpty()) {
            throw new JeecgBootException("单元编码不能为空", 400);
        }
        String childId = resolveChildId(request.getChildCode());
        assertNoReviewDebt(childId);
        return listWords(WordLineType.IN.getCode(), request.getTextbookVersionId(),
                request.getUnitCode(), 1, MAX_PAGE_SIZE);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WordUnitCompleteVO completeUnit(WordUnitRequest request) {
        if (request == null || request.getUnitCode() == null || request.getUnitCode().trim().isEmpty()) {
            throw new JeecgBootException("单元编码不能为空", 400);
        }
        String childId = resolveChildId(request.getChildCode());
        String unitCode = request.getUnitCode().trim();
        boolean pureMode = Boolean.TRUE.equals(request.getPureMode());

        insertPracticeRecord(childId, WordConstants.CONTENT_TYPE_UNIT, unitCode,
                null, 1, JudgeSource.RULE.getCode(), null, null, null);

        int streak = streakDays(childId);
        int learned = request.getTextbookVersionId() == null
                ? 0 : wordMapper.countByUnit(request.getTextbookVersionId().trim(), unitCode);

        WordUnitCompleteVO vo = new WordUnitCompleteVO();
        vo.setTextbookVersionId(request.getTextbookVersionId());
        vo.setUnitCode(unitCode);
        vo.setLearnedWordCount(learned);
        vo.setStreakDays(streak);
        vo.setPureMode(pureMode);
        vo.setGameEnabled(!pureMode);
        vo.setRewardVisible(!pureMode);
        vo.setStreakVisible(!pureMode);
        vo.setRewardText(pureMode ? "" : "单元 " + unitCode + " 闯关成功，连续学习 " + streak + " 天");
        return vo;
    }

    // ------------------------------------------------------------------ 内部

    /**
     * 解析目标孩子。
     *
     * <p>孩子身份取自己的 {@code child_id}；家长必须带 {@code childCode}，
     * 且必须与 {@code study_parent_child_rel} 的关联集合求交集——
     * 前端传的 {@code childId} 一律不直接采信（契约 §3.2）。</p>
     */
    private String resolveChildId(String childCode) {
        boolean hasCode = childCode != null && !childCode.trim().isEmpty();
        if (hasCode) {
            // 越权一律 4010
            scopeService.assertChildCodeAccess(childCode.trim());
        }
        if (scopeService.currentRoles().contains(StudyConstants.ROLE_KID)) {
            String own = scopeService.ownChildId();
            if (own == null) {
                throw new StudyForbiddenException("当前账号没有对应的孩子档案");
            }
            return own;
        }
        if (hasCode) {
            return childService.detail(childCode.trim()).getChildId();
        }
        List<String> visible = scopeService.visibleChildIds();
        if (visible == null || visible.isEmpty()) {
            throw new StudyForbiddenException();
        }
        if (visible.size() > 1) {
            throw new JeecgBootException("请先选择要查看的孩子", 400);
        }
        return visible.get(0);
    }

    /**
     * 复习欠账校验（PRD §8.3 坑二：只打卡不复习）。
     *
     * <p>当日复习队列未清空时，不允许进入新单元。</p>
     */
    private void assertNoReviewDebt(String childId) {
        List<ReviewQueueItemVO> queue = errorPoolService.todayQueue(childId);
        if (queue != null && !queue.isEmpty()) {
            throw new JeecgBootException("先把昨天的复习做完",
                    StudyErrorCode.PRACTICE_ORDER_VIOLATION);
        }
    }

    private StudyWord requireWord(String wordCode) {
        StudyWord word = wordMapper.selectByWordCode(wordCode.trim());
        if (word == null) {
            throw new JeecgBootException("单词不存在", 404);
        }
        return word;
    }

    private WordLineType requireLineType(String lineType) {
        WordLineType line = WordLineType.of(lineType);
        if (line == null) {
            throw new JeecgBootException("线别只能是 IN（课内）或 OUT（课外）", 400);
        }
        return line;
    }

    /**
     * 课内词背错时置跨线升级标记（F-WRD-05 / TC-03-07）。
     *
     * @return 是否触发升级
     */
    private boolean markUpgradedIfTextbook(StudyWord word, String errorCode) {
        if (errorCode == null || !WordLineType.IN.getCode().equals(word.getLineType())) {
            return false;
        }
        wordMapper.markErrorUpgraded(errorCode);
        return true;
    }

    private String reportToErrorPool(String childId, StudyWord word, String errorCause,
                                     String rawAnswer, String judgeSource) {
        StudyErrorReportCommand command = new StudyErrorReportCommand();
        command.setChildId(childId);
        command.setSourceModule(WordConstants.SOURCE_MODULE_WORD);
        command.setContentType(WordConstants.CONTENT_TYPE_WORD);
        command.setContentId(word.getWordCode());
        command.setKnowledgePointCode(word.getKnowledgePointCode());
        command.setSubjectCode(WordConstants.SUBJECT_CODE_EN);
        command.setErrorCause(errorCause);
        command.setRawAnswer(rawAnswer);
        command.setCorrectAnswer(word.getSpelling());
        command.setJudgeSource(judgeSource);
        command.setSourceRef(null);
        command.setCopyrightType(WordConstants.COPYRIGHT_TYPE_SELF);
        return errorPoolService.report(command);
    }

    /**
     * 写入一条作答记录（统一作答记录表，按月分表）。
     */
    private void insertPracticeRecord(String childId, String contentType, String contentId,
                                       String answer, int correct, String judgeSource,
                                       String errorCause, String errorCode, Integer durationMs) {
        recordMapper.insertRecord(recordTableName(), IdWorker.get32UUID(), childId,
                currentContextId(childId), WordConstants.SOURCE_MODULE_WORD, contentType,
                contentId, null, answer, correct, judgeSource, errorCause, errorCode,
                durationMs, sqlDate(LocalDate.now()), scopeService.currentUserId());
    }

    /**
     * 连续学习天数：从统一作答记录表的练习日期里按连续自然日推出。
     *
     * <p>作答记录按月分表，因此跨月时要把上个月表里的日期一并算进来；
     * 上月表尚未建表时忽略即可。</p>
     */
    private int streakDays(String childId) {
        List<Date> dates = new ArrayList<>(practiceDates(recordTableName(), childId));
        String previousTable = recordTableName(LocalDate.now().minusMonths(1));
        dates.addAll(practiceDates(previousTable, childId));
        if (dates.isEmpty()) {
            return 0;
        }
        dates.sort((left, right) -> right.compareTo(left));

        LocalDate cursor = LocalDate.now();
        int streak = 0;
        for (Date date : dates) {
            LocalDate day = Instant.ofEpochMilli(date.getTime())
                    .atZone(ZoneId.systemDefault()).toLocalDate();
            if (day.equals(cursor)) {
                streak++;
                cursor = cursor.minusDays(1);
            } else if (day.isBefore(cursor)) {
                break;
            }
        }
        return streak;
    }

    private List<Date> practiceDates(String tableName, String childId) {
        try {
            List<Date> dates = recordMapper.selectPracticeDates(tableName, childId);
            return dates == null ? new ArrayList<>() : dates;
        } catch (Exception e) {
            // 分表尚未建立时忽略：跨月首日可能出现上个月表还没建的情况
            return new ArrayList<>();
        }
    }

    /**
     * 同一词同一档 10 秒内的重复反馈判定（幂等）。
     */
    private boolean isRepeatedFeedback(StudyWord word, WordFeedback feedback) {
        if (word.getUpdateTime() == null || !feedback.getCode().equals(word.getLastFeedback())) {
            return false;
        }
        long elapsed = System.currentTimeMillis() - word.getUpdateTime().getTime();
        return elapsed >= 0 && elapsed < FEEDBACK_IDEMPOTENT_SECONDS * 1000L;
    }

    /**
     * 复习日期按遗忘参数重算：{@code 天数 = ceil(S × 基数 / λ)}，上限 60 天。
     *
     * <p>S 越高、λ 越低，间隔越长；「忘记」把 S 归零，间隔即为 0（当天再复习）。</p>
     */
    private Date reviewDate(BigDecimal strength, BigDecimal lambda) {
        BigDecimal lambdaBase = lambda == null || lambda.compareTo(BigDecimal.ZERO) <= 0
                ? WordConstants.DEFAULT_DECAY_LAMBDA : lambda;
        if (strength == null || strength.compareTo(BigDecimal.ZERO) <= 0) {
            return sqlDate(LocalDate.now());
        }
        BigDecimal raw = strength.multiply(BigDecimal.valueOf(WordConstants.INTERVAL_BASE_DAYS))
                .divide(lambdaBase, 0, RoundingMode.CEILING);
        int days = Math.min(raw.intValue(), WordConstants.INTERVAL_MAX_DAYS);
        return sqlDate(LocalDate.now().plusDays(days));
    }

    /**
     * 把目标词从文本里挖空，避免提前泄露拼写（R-01）。
     */
    private String mask(String text, String spelling) {
        if (text == null || text.trim().isEmpty() || spelling == null || spelling.trim().isEmpty()) {
            return text == null ? "" : text;
        }
        return Pattern.compile("(?i)\\b" + Pattern.quote(spelling.trim()) + "\\b")
                .matcher(text).replaceAll(WordConstants.BLANK_PLACEHOLDER);
    }

    /**
     * 拼一段短文用于填词：取同单元的例句（目标词已挖空），目标词的例句放在末尾。
     */
    private String buildPassage(StudyWord target, List<StudyWord> unitWords) {
        List<String> parts = new ArrayList<>();
        if (unitWords != null) {
            for (StudyWord word : unitWords) {
                if (word.getWordCode() != null && word.getWordCode().equals(target.getWordCode())) {
                    continue;
                }
                String sentence = word.getContextSentence();
                if (sentence == null || sentence.trim().isEmpty()) {
                    continue;
                }
                parts.add(mask(sentence, target.getSpelling()));
                if (parts.size() >= WordConstants.PASSAGE_SENTENCE_COUNT) {
                    break;
                }
            }
        }
        String own = target.getContextSentence();
        if (own != null && !own.trim().isEmpty()) {
            parts.add(mask(own, target.getSpelling()));
        }
        return String.join(" ", parts);
    }

    private WordStageVO stage(String code, String name, int layer, boolean imageRequired,
                              String prompt, String content) {
        WordStageVO stage = new WordStageVO();
        stage.setStageCode(code);
        stage.setStageName(name);
        stage.setLayer(layer);
        stage.setImageRequired(imageRequired);
        stage.setPrompt(prompt);
        stage.setContent(content == null ? "" : content);
        return stage;
    }

    private WordPracticeVO toPracticeVO(StudyWord word, List<StudyWord> unitWords) {
        WordPracticeVO vo = new WordPracticeVO();
        vo.setWordCode(word.getWordCode());
        vo.setLineType(word.getLineType());
        vo.setTextbookVersionId(word.getTextbookVersionId());
        vo.setUnitCode(word.getUnitCode());
        vo.setUnitOrder(word.getUnitOrder());
        vo.setMeaning(mask(word.getMeaning(), word.getSpelling()));
        vo.setPhonetic(word.getPhonetic());
        vo.setPos(word.getPos());
        vo.setContextSentence(mask(word.getContextSentence(), word.getSpelling()));
        // 存在不依赖图片的回忆环节：释义环节恒不依赖图片（PRD §8.3 坑一）
        vo.setRecallWithoutImage(Boolean.TRUE);
        List<WordStageVO> stages = new ArrayList<>();
        stages.add(stage(WordConstants.STAGE_MEANING, "释义", 1, false,
                "看释义，回忆并拼出这个词", vo.getMeaning()));
        stages.add(stage(WordConstants.STAGE_SENTENCE, "造句", 2, false,
                "用这个词造一个句子", vo.getContextSentence()));
        stages.add(stage(WordConstants.STAGE_PASSAGE, "短文填词", 3, false,
                "在短文里把空补上", buildPassage(word, unitWords)));
        vo.setStages(stages);
        vo.setMemoryStrength(word.getMemoryStrength());
        vo.setDecayLambda(word.getDecayLambda());
        vo.setNextReviewAt(word.getNextReviewAt());
        return vo;
    }

    private List<WordListItemVO> toListItems(List<StudyWord> words, Boolean upgradePriority) {
        List<WordListItemVO> items = new ArrayList<>();
        if (words == null) {
            return items;
        }
        for (StudyWord word : words) {
            WordListItemVO item = toListItem(word, upgradePriority);
            if (item != null) {
                items.add(item);
            }
        }
        return items;
    }

    private WordListItemVO toListItem(StudyWord word, Boolean upgradePriority) {
        if (word == null) {
            return null;
        }
        WordListItemVO item = new WordListItemVO();
        item.setWordCode(word.getWordCode());
        item.setLineType(word.getLineType());
        item.setTextbookVersionId(word.getTextbookVersionId());
        item.setUnitCode(word.getUnitCode());
        item.setUnitOrder(word.getUnitOrder());
        item.setMeaning(mask(word.getMeaning(), word.getSpelling()));
        item.setPhonetic(word.getPhonetic());
        item.setPos(word.getPos());
        item.setContextSentence(mask(word.getContextSentence(), word.getSpelling()));
        item.setFrequency(word.getFrequency());
        item.setMemoryStrength(word.getMemoryStrength());
        item.setDecayLambda(word.getDecayLambda());
        item.setFeedbackCount(word.getFeedbackCount());
        item.setLastFeedback(word.getLastFeedback());
        item.setUpgradePriority(upgradePriority);
        item.setNextReviewAt(word.getNextReviewAt());
        return item;
    }

    private StudyWord pickNext(List<StudyWord> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        Date today = today();
        for (StudyWord word : candidates) {
            if (word.getNextReviewAt() == null || !word.getNextReviewAt().after(today)) {
                return word;
            }
        }
        return candidates.get(0);
    }

    private Date today() {
        return sqlDate(LocalDate.now());
    }

    /**
     * {@code DATE} 列的取值用 {@link java.sql.Date}（本地零点，无时区漂移）。
     */
    private Date sqlDate(LocalDate localDate) {
        return java.sql.Date.valueOf(localDate);
    }

    private String recordTableName() {
        return recordTableName(LocalDate.now());
    }

    private String recordTableName(LocalDate date) {
        return RECORD_TABLE_PREFIX + date.format(RECORD_TABLE_SUFFIX);
    }

    private String currentContextId(String childId) {
        String contextId = wordMapper.selectCurrentContextId(childId);
        return contextId == null ? "" : contextId;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private int normalizeOffset(Integer pageNo, int pageSize) {
        if (pageNo == null || pageNo <= 1) {
            return 0;
        }
        return (pageNo - 1) * pageSize;
    }
}
