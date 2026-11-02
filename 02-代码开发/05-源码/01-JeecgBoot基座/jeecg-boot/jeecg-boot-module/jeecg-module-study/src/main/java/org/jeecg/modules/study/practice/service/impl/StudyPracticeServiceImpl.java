package org.jeecg.modules.study.practice.service.impl;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.study.common.constant.StudyErrorCode;
import org.jeecg.modules.study.common.enums.JudgeSource;
import org.jeecg.modules.study.common.exception.StudyForbiddenException;
import org.jeecg.modules.study.error.constant.ErrorCause;
import org.jeecg.modules.study.error.service.StudyErrorPoolService;
import org.jeecg.modules.study.error.vo.ErrorPoolItemVO;
import org.jeecg.modules.study.error.vo.StudyErrorReportCommand;
import org.jeecg.modules.study.judge.engine.JudgeRequest;
import org.jeecg.modules.study.judge.engine.JudgeResult;
import org.jeecg.modules.study.judge.service.JudgeEngine;
import org.jeecg.modules.study.practice.entity.StudyPracticeRecord;
import org.jeecg.modules.study.practice.entity.StudyQuestion;
import org.jeecg.modules.study.practice.mapper.StudyPracticeContextMapper;
import org.jeecg.modules.study.practice.mapper.StudyPracticeRecordMapper;
import org.jeecg.modules.study.practice.mapper.StudyPracticeQuestionMapper;
import org.jeecg.modules.study.practice.service.StudyPracticeService;
import org.jeecg.modules.study.practice.vo.AnswerCommand;
import org.jeecg.modules.study.practice.vo.InputSpecVO;
import org.jeecg.modules.study.practice.vo.AnswerResultVO;
import org.jeecg.modules.study.practice.vo.ExplanationSectionVO;
import org.jeecg.modules.study.practice.vo.ExplanationVO;
import org.jeecg.modules.study.practice.vo.ExplainVO;
import org.jeecg.modules.study.practice.vo.JudgeDetailVO;
import org.jeecg.modules.study.practice.vo.PracticeQuestionVO;
import org.jeecg.modules.study.practice.vo.PracticeSessionVO;
import org.jeecg.modules.study.practice.vo.SessionSummaryVO;
import org.jeecg.modules.study.practice.vo.StepTag;
import org.jeecg.modules.study.scope.service.ChildScopeService;

/**
 * 练习与判定实现（T-V0-09 / T-V0-10）。
 *
 * <p><b>取题不给答案</b>（R01-1）：{@link PracticeQuestionVO} 里没有 answer / analysis /
 * correctAnswer 字段，前端拿不到答案就无法在未作答时展示。</p>
 *
 * <p><b>作答幂等</b>（TC-08-06）：作答记录主键取「孩子 + 会话 + 内容」的确定性摘要，
 * 同一会话同一题重复提交只落一条记录、只入池一次、返回完全相同的结果。</p>
 *
 * <p><b>事务原子性</b>（TC-08-09 / TC-11-16）：作答记录与错题入池在同一个事务里，
 * 任一侧写失败整体回滚，不留半写状态。</p>
 *
 * @author jeecg-study
 */
@Service
@RequiredArgsConstructor
public class StudyPracticeServiceImpl implements StudyPracticeService {

    /** 练习类型：错题重做 */
    private static final String MODULE_WRONG = "wrong";

    /** 会话 ID 前缀 */
    private static final String SESSION_PREFIX = "PS-";

    /** 单次会话建议题量 */
    private static final int SESSION_QUESTION_COUNT = 10;

    /** 作答记录里步骤标签 JSON 的字段：会话 ID */
    private static final String META_SESSION = "sessionId";

    /** 作答记录里步骤标签 JSON 的字段：判定依据 */
    private static final String META_BASIS = "basis";

    /** 作答记录里步骤标签 JSON 的字段：实际容差 */
    private static final String META_TOLERANCE = "tolerance";

    /** 作答记录里步骤标签 JSON 的字段：步骤错因打标 */
    private static final String META_TAGS = "tags";

    private final StudyPracticeQuestionMapper questionMapper;
    private final StudyPracticeRecordMapper recordMapper;
    private final StudyPracticeContextMapper contextMapper;
    private final JudgeEngine judgeEngine;
    private final StudyErrorPoolService errorPoolService;
    private final ChildScopeService scopeService;

    /** JSON 序列化（步骤标签袋） */
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ------------------------------------------------------------------ 取题

    @Override
    public PracticeQuestionVO nextQuestion(String moduleType) {
        String childId = currentChildId();
        String type = normalizeType(moduleType);
        StudyQuestion question;
        if (MODULE_WRONG.equals(type)) {
            question = nextWrongQuestion(childId);
        } else {
            question = questionMapper.selectNextUnanswered(
                    tableName(), childId, questionTypeOf(type), subjectCodeOf(type));
            if (question == null) {
                question = questionMapper.selectFirst(questionTypeOf(type), subjectCodeOf(type));
            }
        }
        if (question == null) {
            throw new JeecgBootException("该类型暂时没有可用题目", 400);
        }
        return toQuestionVO(question, type, null);
    }

    // ------------------------------------------------------------------ 作答

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AnswerResultVO submitAnswer(AnswerCommand command) {
        if (command == null) {
            throw new JeecgBootException("作答参数不能为空", 400);
        }
        String childId = currentChildId();
        String sessionId = trim(command.getSessionId());
        String contentId = trim(command.getContentId());
        if (sessionId.isEmpty() || contentId.isEmpty()) {
            throw new JeecgBootException("会话ID与内容ID不能为空", 400);
        }
        StudyQuestion question = loadQuestion(contentId);
        if (question == null) {
            throw new JeecgBootException("题目不存在：" + contentId, 400);
        }

        // 幂等：同一会话同一题只判定一次、只落一条记录（TC-08-06）
        String recordId = recordId(childId, sessionId, contentId);
        StudyPracticeRecord existed = recordMapper.selectById(tableName(), recordId);
        if (existed != null) {
            return replayResult(existed, question);
        }

        // 判定（确定性规则，AI 只做解释层）
        JudgeRequest judgeRequest = buildJudgeRequest(question, command);
        JudgeResult judge = judgeEngine.judge(judgeRequest);
        if (!JudgeSource.isValid(judge.getJudgeSource())) {
            judge.setJudgeSource(JudgeSource.RULE.getCode());
        }
        if (judge.getBasis() == null || judge.getBasis().trim().isEmpty()) {
            judge.setBasis("按「" + judgeRequest.getContentType() + "」规则判定");
        }
        boolean correct = judge.isCorrect();

        // 答错必须先选错因（TC-02-04 / TC-11-03 → 4001）
        if (!correct && !ErrorCause.isValid(command.getErrorCause())) {
            throw new JeecgBootException("请先选择错因", StudyErrorCode.MISSING_ERROR_CAUSE);
        }

        // 先落作答记录，再入错题池：入池失败整笔回滚，不留半写状态（TC-08-09 / TC-11-16）
        StudyPracticeRecord record = new StudyPracticeRecord();
        record.setId(recordId);
        record.setChildId(childId);
        record.setContextId(resolveContextId(childId));
        record.setSourceModule(sourceModuleOf(question));
        record.setContentType(contentTypeOf(question));
        record.setContentId(question.getQuestionCode());
        record.setKnowledgePointCode(question.getKnowledgePointCode());
        record.setAnswer(command.getAnswer());
        record.setCorrect(correct ? 1 : 0);
        record.setJudgeSource(judge.getJudgeSource());
        record.setErrorCause(correct ? null : trim(command.getErrorCause()));
        record.setDurationMs(command.getDurationMs());
        record.setStepTags(buildStepTags(sessionId, judge, command.getStepTags()));
        record.setPracticeDate(toDate(LocalDate.now()));
        record.setDelFlag(0);
        try {
            recordMapper.insertRecord(tableName(), record);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 并发重复提交：另一个会话已经落了同一条记录，按幂等处理
            StudyPracticeRecord raced = recordMapper.selectById(tableName(), recordId);
            if (raced != null) {
                return replayResult(raced, question);
            }
            throw e;
        }

        // 答错入统一错题池（幂等：同一道题只入一次）
        String errorCode = null;
        if (!correct) {
            errorCode = errorPoolService.report(buildReportCommand(childId, question, command, judge));
            recordMapper.fillErrorCode(tableName(), recordId, errorCode);
        }

        return buildResult(question, judge, command.getAnswer(), correct, errorCode, childId, recordId, false);
    }

    // ------------------------------------------------------------------ 解析

    @Override
    public ExplanationVO explanation(String contentId, String sessionId) {
        String childId = currentChildId();
        String code = trim(contentId);
        if (code.isEmpty()) {
            throw new JeecgBootException("内容ID不能为空", 400);
        }
        // 未作答直接取解析 → 4003（R01-2）
        StudyPracticeRecord record = recordMapper.selectLatestByContent(tableName(), childId, code, trim(sessionId));
        if (record == null) {
            throw new JeecgBootException("还没作答，不能看解析", StudyErrorCode.PRACTICE_ORDER_VIOLATION);
        }
        StudyQuestion question = loadQuestion(code);

        ExplanationVO vo = new ExplanationVO();
        vo.setContentId(code);
        vo.setType("STEP_BY_STEP");
        List<ExplanationSectionVO> sections = new ArrayList<>();
        sections.add(section("QUESTION", "题干", question == null ? null : question.getStem()));
        sections.add(section("IDEA", "思路", question == null ? null : question.getAnalysis()));
        sections.add(section("STEP", "步骤", question == null ? null : question.getAnalysis()));
        sections.add(section("ANSWER", "答案", question == null ? null : question.getAnswer()));
        vo.setSections(sections);
        vo.setWrongPath(wrongPathOf(record.getErrorCause()));
        vo.setRelatedQuestionIds(question == null || question.getKnowledgePointCode() == null
                ? Collections.emptyList()
                : questionMapper.selectRelatedQuestionCodes(
                        question.getKnowledgePointCode(), question.getQuestionCode()));
        return vo;
    }

    // ------------------------------------------------------------------ 会话

    @Override
    public PracticeSessionVO createSession(String moduleType) {
        currentChildId();
        PracticeSessionVO vo = new PracticeSessionVO();
        vo.setSessionId(SESSION_PREFIX
                + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + UUID.randomUUID().toString().substring(0, 6));
        vo.setModuleType(normalizeType(moduleType));
        vo.setQuestionCount(SESSION_QUESTION_COUNT);
        vo.setCreatedAt(new Date());
        return vo;
    }

    @Override
    public SessionSummaryVO finishSession(String sessionId) {
        String childId = currentChildId();
        String session = trim(sessionId);
        if (session.isEmpty()) {
            throw new JeecgBootException("会话ID不能为空", 400);
        }
        Map<String, Object> summary = recordMapper.summarizeSession(tableName(), childId, session);
        SessionSummaryVO vo = new SessionSummaryVO();
        vo.setSessionId(session);
        int total = summary == null ? 0 : intValue(summary.get("total"));
        int correct = summary == null ? 0 : intValue(summary.get("correctCount"));
        vo.setTotal(total);
        vo.setCorrect(correct);
        vo.setWrong(total - correct);
        vo.setDurationMs(summary == null ? 0 : intValue(summary.get("durationMs")));
        return vo;
    }

    // ------------------------------------------------------------------ 内部：幂等重放

    /**
     * 幂等重放：不再判定、不再写库，返回与首次提交完全一致的结果。
     */
    private AnswerResultVO replayResult(StudyPracticeRecord record, StudyQuestion question) {
        Map<String, Object> meta = parseMeta(record.getStepTags());
        boolean correct = record.getCorrect() != null && record.getCorrect() == 1;
        String basis = meta == null ? null : stringValue(meta.get(META_BASIS));
        if (basis == null || basis.trim().isEmpty()) {
            basis = "按「" + judgeContentType(question, null) + "」规则判定";
        }
        BigDecimal tolerance = meta == null ? null : decimalValue(meta.get(META_TOLERANCE));

        JudgeResult judge = new JudgeResult();
        judge.setCorrect(correct);
        judge.setJudgeSource(JudgeSource.isValid(record.getJudgeSource())
                ? record.getJudgeSource() : JudgeSource.RULE.getCode());
        judge.setBasis(basis);
        judge.setTolerance(tolerance);

        return buildResult(question, judge, record.getAnswer(), correct, record.getErrorCode(),
                record.getChildId(), record.getId(), true);
    }

    // ------------------------------------------------------------------ 内部：结果组装

    private AnswerResultVO buildResult(StudyQuestion question, JudgeResult judge, String actualAnswer,
                                        boolean correct, String errorCode, String childId,
                                        String recordId, boolean duplicated) {
        AnswerResultVO vo = new AnswerResultVO();
        vo.setContentId(question == null ? null : question.getQuestionCode());
        vo.setCorrect(correct);
        vo.setSource(judge.getJudgeSource());
        vo.setBasis(judge.getBasis());

        JudgeDetailVO detail = new JudgeDetailVO();
        detail.setExpected(question == null ? null : question.getAnswer());
        detail.setActual(actualAnswer);
        detail.setTolerance(judge.getTolerance());
        vo.setDetail(detail);

        String analysis = question == null ? null : question.getAnalysis();
        ExplainVO explain = new ExplainVO();
        explain.setType(analysis == null || analysis.trim().isEmpty() ? "DEGRADED" : "STEP_BY_STEP");
        explain.setText(analysis == null || analysis.trim().isEmpty()
                ? "这道题暂时没有讲解，先按自己的思路做一遍。" : analysis);
        vo.setExplain(explain);
        vo.setExplainStatus(explain.getType());

        vo.setErrorId(errorCode);
        vo.setRecordId(recordId);
        vo.setDuplicated(duplicated);
        if (errorCode != null && childId != null) {
            ErrorPoolItemVO item = findErrorItem(childId, errorCode);
            if (item != null) {
                vo.setNextReviewAt(item.getNextReviewAt());
                vo.setMasteryState(item.getMasteryState());
            }
        }
        return vo;
    }

    private ErrorPoolItemVO findErrorItem(String childId, String errorCode) {
        List<ErrorPoolItemVO> items = errorPoolService.listByChild(childId);
        if (items == null) {
            return null;
        }
        for (ErrorPoolItemVO item : items) {
            if (item != null && errorCode.equals(item.getErrorCode())) {
                return item;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ 内部：判定与入池

    private JudgeRequest buildJudgeRequest(StudyQuestion question, AnswerCommand command) {
        JudgeRequest request = new JudgeRequest();
        request.setContentType(judgeContentType(question, command));
        request.setExpectedAnswer(question.getAnswer());
        request.setActualAnswer(command.getAnswer());
        BigDecimal tolerance = command.getTolerance() != null ? command.getTolerance() : question.getAnswerTolerance();
        request.setTolerance(tolerance);
        return request;
    }

    private StudyErrorReportCommand buildReportCommand(String childId, StudyQuestion question,
                                                       AnswerCommand command, JudgeResult judge) {
        StudyErrorReportCommand report = new StudyErrorReportCommand();
        report.setChildId(childId);
        report.setSourceModule(sourceModuleOf(question));
        report.setContentType(contentTypeOf(question));
        report.setContentId(question.getQuestionCode());
        report.setKnowledgePointCode(question.getKnowledgePointCode());
        report.setSubjectCode(question.getSubjectCode());
        report.setErrorCause(trim(command.getErrorCause()));
        report.setRawAnswer(command.getAnswer());
        report.setCorrectAnswer(question.getAnswer());
        report.setJudgeSource(judge.getJudgeSource());
        report.setSourceRef(question.getSourceRef());
        report.setCopyrightType(question.getCopyrightType());
        return report;
    }

    // ------------------------------------------------------------------ 内部：取题

    private StudyQuestion nextWrongQuestion(String childId) {
        List<ErrorPoolItemVO> items = errorPoolService.listByChild(childId);
        if (items == null) {
            return null;
        }
        for (ErrorPoolItemVO item : items) {
            if (item == null || item.getContentId() == null) {
                continue;
            }
            StudyQuestion question = questionMapper.selectByQuestionCode(item.getContentId());
            if (question != null) {
                return question;
            }
        }
        return null;
    }

    private PracticeQuestionVO toQuestionVO(StudyQuestion question, String moduleType, String errorCode) {
        PracticeQuestionVO vo = new PracticeQuestionVO();
        vo.setContentId(question.getQuestionCode());
        vo.setModuleType(moduleType);
        vo.setQuestionType(question.getQuestionType());
        vo.setSubjectCode(question.getSubjectCode());
        vo.setKnowledgePointCode(question.getKnowledgePointCode());
        vo.setStem(question.getStem());
        vo.setOptions(question.getOptions());
        vo.setDifficulty(question.getDifficulty());
        vo.setSourceType(question.getSourceType());
        vo.setMotherQuestionCode(question.getMotherQuestionCode());
        vo.setVariantLayer(question.getVariantLayer());
        vo.setErrorCode(errorCode);

        InputSpecVO entry = new InputSpecVO();
        entry.setType(judgeContentType(question, null));
        entry.setPlaceholder(placeholderOf(question.getQuestionType()));
        vo.setInputSpec(entry);
        return vo;
    }

    // ------------------------------------------------------------------ 内部：工具

    /**
     * 题目题型 → 判定题型。
     */
    private String judgeContentType(StudyQuestion question, AnswerCommand command) {
        if (command != null && command.getContentType() != null && !command.getContentType().trim().isEmpty()) {
            return command.getContentType().trim();
        }
        String type = question == null || question.getQuestionType() == null
                ? "" : question.getQuestionType().trim().toUpperCase();
        switch (type) {
            case "NUMERIC":
                return "NUMERIC";
            case "MULTI_CHOICE":
                return "MULTI_CHOICE";
            default:
                return "EXACT";
        }
    }

    private String questionTypeOf(String moduleType) {
        return "word".equals(moduleType) ? "WORD" : null;
    }

    private String subjectCodeOf(String moduleType) {
        switch (moduleType) {
            case "math":
                return "MA";
            case "grammar":
                return "EN";
            case "writing":
                return "CN";
            default:
                return null;
        }
    }

    private String sourceModuleOf(StudyQuestion question) {
        String type = question.getQuestionType() == null ? "" : question.getQuestionType().trim().toUpperCase();
        if ("WORD".equals(type)) {
            return "WORD";
        }
        if ("GRAMMAR".equals(type)) {
            return "GRAMMAR";
        }
        String subject = question.getSubjectCode() == null ? "" : question.getSubjectCode().trim().toUpperCase();
        if ("MA".equals(subject)) {
            return "MATH";
        }
        if ("EN".equals(subject)) {
            return "GRAMMAR";
        }
        if ("CN".equals(subject)) {
            return "WRITING";
        }
        return "ERROR";
    }

    private String contentTypeOf(StudyQuestion question) {
        String type = question.getQuestionType() == null ? "" : question.getQuestionType().trim().toUpperCase();
        if ("WORD".equals(type)) {
            return "WORD";
        }
        if ("GRAMMAR".equals(type)) {
            return "SENTENCE";
        }
        String subject = question.getSubjectCode() == null ? "" : question.getSubjectCode().trim().toUpperCase();
        if ("CN".equals(subject)) {
            return "PARAGRAPH";
        }
        return "QUESTION";
    }

    private String placeholderOf(String questionType) {
        String type = questionType == null ? "" : questionType.trim().toUpperCase();
        if ("NUMERIC".equals(type)) {
            return "请输入数值";
        }
        if ("MULTI_CHOICE".equals(type)) {
            return "请选择选项";
        }
        return "请输入答案";
    }

    /**
     * 常见错误路径：<b>由规则从错因推导</b>，不是模型自由发挥。
     */
    private String wrongPathOf(String errorCause) {
        if (errorCause == null) {
            return null;
        }
        switch (errorCause.trim()) {
            case ErrorCause.CONCEPT_UNCLEAR:
                return "常见错误：概念本身没弄清楚，把公式套在了不适用的地方。";
            case ErrorCause.READING_DEVIATION:
                return "常见错误：题目条件没看全，把「一共」「至少」这类词看漏了。";
            case ErrorCause.CALCULATION_ERROR:
                return "常见错误：思路是对的，但计算或化简时算错了。";
            case ErrorCause.METHOD_UNKNOWN:
                return "常见错误：不知道该用哪种方法，凭感觉套了一个公式。";
            case ErrorCause.TIME_SHORTAGE:
                return "常见错误：方法是对的，但时间不够，来不及写完。";
            default:
                return null;
        }
    }

    private ExplanationSectionVO section(String key, String title, String text) {
        ExplanationSectionVO vo = new ExplanationSectionVO();
        vo.setKey(key);
        vo.setTitle(title);
        vo.setText(text);
        return vo;
    }

    private StudyQuestion loadQuestion(String contentId) {
        StudyQuestion question = questionMapper.selectByQuestionCode(contentId);
        if (question == null) {
            question = questionMapper.selectById(contentId);
        }
        return question;
    }

    /**
     * 作答记录主键：孩子 + 会话 + 内容的确定性摘要（幂等落库键）。
     */
    private String recordId(String childId, String sessionId, String contentId) {
        String raw = childId + "|" + sessionId + "|" + contentId;
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            return UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
        }
    }

    /**
     * 步骤标签 JSON：会话 ID + 判定依据 + 实际容差 + 步骤错因打标。
     *
     * <p>会话 ID 与判定依据放这里，是因为作答记录表没有独立的 session_id / basis 列，
     * 而幂等重放需要它们才能还原出与首次完全一致的结果。</p>
     */
    private String buildStepTags(String sessionId, JudgeResult judge, List<StepTag> tags) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put(META_SESSION, sessionId);
        meta.put(META_BASIS, judge.getBasis());
        if (judge.getTolerance() != null) {
            meta.put(META_TOLERANCE, judge.getTolerance());
        }
        if (tags != null && !tags.isEmpty()) {
            meta.put(META_TAGS, tags);
        }
        try {
            return objectMapper.writeValueAsString(meta);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> parseMeta(String stepTags) {
        if (stepTags == null || stepTags.trim().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(stepTags, Map.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 当前月份的分表名。
     */
    private String tableName() {
        return "study_practice_record_" + YearMonth.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
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

    /**
     * 解析孩子当前上下文；上下文域未落地时退化为孩子 ID。
     */
    private String resolveContextId(String childId) {
        String contextId = contextMapper.selectCurrentContextId(childId);
        return contextId == null || contextId.trim().isEmpty() ? childId : contextId;
    }

    private String normalizeType(String moduleType) {
        String type = moduleType == null ? "" : moduleType.trim().toLowerCase();
        switch (type) {
            case "word":
            case "math":
            case "grammar":
            case "writing":
            case MODULE_WRONG:
                return type;
            default:
                throw new JeecgBootException("不支持的练习类型：" + moduleType, 400);
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static int intValue(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static BigDecimal decimalValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString());
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Date toDate(LocalDate date) {
        return date == null ? null : Date.from(date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant());
    }
}
