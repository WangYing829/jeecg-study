package org.jeecg.modules.study.math.service.impl;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.study.common.constant.StudyErrorCode;
import org.jeecg.modules.study.common.enums.JudgeSource;
import org.jeecg.modules.study.common.exception.StudyForbiddenException;
import org.jeecg.modules.study.common.record.StudyRecordTableGuard;
import org.jeecg.modules.study.error.constant.ErrorCause;
import org.jeecg.modules.study.error.service.StudyErrorPoolService;
import org.jeecg.modules.study.error.vo.StudyErrorReportCommand;
import org.jeecg.modules.study.idseq.service.StudyIdSequenceService;
import org.jeecg.modules.study.judge.engine.JudgeRequest;
import org.jeecg.modules.study.judge.engine.JudgeResult;
import org.jeecg.modules.study.judge.service.JudgeEngine;
import org.jeecg.modules.study.math.constant.MathConstants;
import org.jeecg.modules.study.math.engine.MotherVariantRuleEngine;
import org.jeecg.modules.study.math.engine.NarrativeKeywordExtractor;
import org.jeecg.modules.study.math.entity.StudyMathRecord;
import org.jeecg.modules.study.math.entity.StudyMotherQuestion;
import org.jeecg.modules.study.math.entity.StudyQuestionVariant;
import org.jeecg.modules.study.math.enums.MotherSection;
import org.jeecg.modules.study.math.enums.VariantLayer;
import org.jeecg.modules.study.math.mapper.StudyMathKnowledgePointMapper;
import org.jeecg.modules.study.math.mapper.StudyMathRecordMapper;
import org.jeecg.modules.study.math.mapper.StudyMotherQuestionMapper;
import org.jeecg.modules.study.math.mapper.StudyQuestionVariantMapper;
import org.jeecg.modules.study.math.service.StudyMathService;
import org.jeecg.modules.study.math.vo.ConditionTypeVO;
import org.jeecg.modules.study.math.vo.GeneratedVariantVO;
import org.jeecg.modules.study.math.vo.MathProgressVO;
import org.jeecg.modules.study.math.vo.MotherDetailVO;
import org.jeecg.modules.study.math.vo.MotherListItemVO;
import org.jeecg.modules.study.math.vo.MotherPassCommand;
import org.jeecg.modules.study.math.vo.MotherPassResultVO;
import org.jeecg.modules.study.math.vo.MotherSaveCommand;
import org.jeecg.modules.study.math.vo.MotherStepVO;
import org.jeecg.modules.study.math.vo.NarrativeBriefVO;
import org.jeecg.modules.study.math.vo.NarrativeCommand;
import org.jeecg.modules.study.math.vo.NarrativeResultVO;
import org.jeecg.modules.study.math.vo.StepTagVO;
import org.jeecg.modules.study.math.vo.VariantAnswerCommand;
import org.jeecg.modules.study.math.vo.VariantAnswerResultVO;
import org.jeecg.modules.study.math.vo.VariantGenerateResultVO;
import org.jeecg.modules.study.math.vo.VariantResultCommand;
import org.jeecg.modules.study.math.vo.VariantVO;
import org.jeecg.modules.study.scope.service.ChildScopeService;

/**
 * 数学母题服务实现（F-MTH-01…F-MTH-06）。
 *
 * <p><b>数据范围</b>：母题是内容表（全局共享），变式与作答记录是行为表（按 child_id 隔离）。
 * 孩子身份一律取会话里的自己，家长身份必须与关系表求交集，越权统一 4010。</p>
 *
 * <p><b>红线</b>：变式答案只参与服务端判定，任何响应体都不下发（R-01）；
 * 判定与答案生成不依赖任何 AI 客户端（R-02）。</p>
 *
 * @author jeecg-study
 */
@Service
@RequiredArgsConstructor
public class StudyMathServiceImpl implements StudyMathService {

    /** 步骤标签 JSON 的字段：会话 ID */
    private static final String META_SESSION = "sessionId";

    /** 步骤标签 JSON 的字段：判定依据 */
    private static final String META_BASIS = "basis";

    /** 步骤标签 JSON 的字段：容差 */
    private static final String META_TOLERANCE = "tolerance";

    /** 步骤标签 JSON 的字段：步骤错因打标 */
    private static final String META_TAGS = "tags";

    /** 版权类型：自编 */
    private static final String COPYRIGHT_SELF = "SELF";

    private final StudyMotherQuestionMapper motherMapper;
    private final StudyQuestionVariantMapper variantMapper;
    private final StudyMathRecordMapper recordMapper;
    private final StudyMathKnowledgePointMapper knowledgePointMapper;
    private final StudyIdSequenceService idSequence;
    private final ChildScopeService scopeService;
    private final JudgeEngine judgeEngine;
    private final StudyErrorPoolService errorPoolService;
    private final MotherVariantRuleEngine ruleEngine;

    /** 当月分表守卫（幂等建当月表；拿表名即确保存在） */
    private final StudyRecordTableGuard recordTableGuard;

    /** JSON 序列化 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ================================================================== 母题维护

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MotherDetailVO saveMother(MotherSaveCommand command) {
        validateFiveSections(command);
        String subject = subjectOf(command);
        String title = trim(command.getTitle());
        String pointCode = trim(command.getKnowledgePointCode());

        // 幂等：同一「学科 + 考点 + 题型名」视为同一道母题，只更新不新建
        StudyMotherQuestion existed = motherMapper.selectByIdentity(subject, pointCode, title);
        StudyMotherQuestion mother;
        if (existed != null) {
            mother = existed;
            applyCommand(mother, command, subject);
            motherMapper.updateById(mother);
        } else {
            mother = new StudyMotherQuestion();
            mother.setId(IdWorker.get32UUID());
            mother.setMotherCode(idSequence.next("MTH", subject));
            applyCommand(mother, command, subject);
            mother.setOrderNo(0);
            mother.setVariantCount(0);
            mother.setStatus(1);
            mother.setDelFlag(0);
            motherMapper.insert(mother);
        }
        return toDetail(mother);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MotherDetailVO updateMother(String motherCode, MotherSaveCommand command) {
        validateFiveSections(command);
        StudyMotherQuestion mother = requireMother(motherCode);
        applyCommand(mother, command, subjectOf(command));
        motherMapper.updateById(mother);
        return toDetail(mother);
    }

    @Override
    public MotherDetailVO motherDetail(String motherCode) {
        return toDetail(requireMother(motherCode));
    }

    // ================================================================== 闯关顺序与进度

    @Override
    public List<MotherListItemVO> motherList(String childId) {
        String child = resolveChildId(childId);
        List<StudyMotherQuestion> mothers = motherMapper.selectActiveBySubject(MathConstants.SUBJECT_MATH);
        Map<String, MotherStat> stats = statsOf(child);
        List<MotherListItemVO> list = new ArrayList<>();
        for (StudyMotherQuestion mother : mothers) {
            list.add(toListItem(mother, stats.get(mother.getMotherCode())));
        }
        // 排序口径：考纲权重降序 + 掌握度升序（连续错的关卡在同权重档内前移）
        list.sort(Comparator
                .comparing(MotherListItemVO::getOutlineWeight,
                        Comparator.nullsLast(Comparator.<BigDecimal>reverseOrder()))
                .thenComparing(MotherListItemVO::getMastery,
                        Comparator.nullsLast(Comparator.<BigDecimal>naturalOrder()))
                .thenComparing(MotherListItemVO::getOrderNo,
                        Comparator.nullsLast(Comparator.<Integer>naturalOrder()))
                .thenComparing(MotherListItemVO::getMotherCode,
                        Comparator.nullsLast(Comparator.<String>naturalOrder())));
        return list;
    }

    @Override
    public MathProgressVO progress(String childId) {
        String child = resolveChildId(childId);
        List<StudyMotherQuestion> mothers = motherMapper.selectActiveBySubject(MathConstants.SUBJECT_MATH);
        Map<String, MotherStat> stats = statsOf(child);
        List<String> mastered = new ArrayList<>();
        for (StudyMotherQuestion mother : mothers) {
            MotherStat stat = stats.get(mother.getMotherCode());
            int required = requiredVariantCount(mother);
            int correctVariants = stat == null ? 0 : stat.correctVariantCodes.size();
            if (correctVariants >= required) {
                mastered.add(mother.getMotherCode());
            }
        }
        MathProgressVO vo = new MathProgressVO();
        vo.setMasteredTypeCount(mastered.size());
        vo.setTotalTypeCount(mothers.size());
        // 进度以「母题」为单位：这类题我会了 N 类（不按题量计数）
        vo.setProgressText("这类题我会了 " + mastered.size() + " 类");
        vo.setMasteredMotherCodes(mastered);
        return vo;
    }

    // ================================================================== 变式

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VariantGenerateResultVO generateVariants(String motherCode, String childId) {
        String child = resolveChildId(childId);
        StudyMotherQuestion mother = requireMother(motherCode);

        List<StudyQuestionVariant> existed = variantMapper.selectByMotherAndChild(motherCode, child);
        Map<Integer, StudyQuestionVariant> byLayer = new LinkedHashMap<>();
        for (StudyQuestionVariant variant : existed) {
            if (variant.getChildId() != null && variant.getChildId().equals(child)) {
                byLayer.putIfAbsent(variant.getLayer(), variant);
            }
        }

        boolean duplicated = true;
        for (GeneratedVariantVO draft : ruleEngine.generate(mother)) {
            // 幂等键：孩子 + 母题 + 层级
            if (byLayer.containsKey(draft.getLayer())) {
                continue;
            }
            duplicated = false;
            StudyQuestionVariant variant = new StudyQuestionVariant();
            variant.setId(IdWorker.get32UUID());
            variant.setChildId(child);
            variant.setVariantCode(nextVariantCode(motherCode, draft.getLayer()));
            variant.setMotherCode(motherCode);
            variant.setLayer(draft.getLayer());
            variant.setParamsSnapshot(toJson(draft.getParamsSnapshot()));
            variant.setStem(draft.getStem());
            variant.setAnswer(draft.getAnswer());
            variant.setAnswerTolerance(draft.getAnswerTolerance());
            variant.setGeneratedBy(draft.getGeneratedBy());
            variant.setStatus(1);
            variant.setDelFlag(0);
            variantMapper.insert(variant);
        }
        motherMapper.updateVariantCount(motherCode, motherMapper.countVariants(motherCode));

        List<StudyQuestionVariant> variants = variantMapper.selectByMotherAndChild(motherCode, child);
        VariantGenerateResultVO vo = new VariantGenerateResultVO();
        vo.setMotherCode(motherCode);
        vo.setVariants(toVariantVOs(variants));
        vo.setPassVariantCount(requiredVariantCount(mother));
        vo.setDuplicated(duplicated);
        return vo;
    }

    @Override
    public List<VariantVO> listVariants(String motherCode, String childId) {
        String child = resolveChildId(childId);
        requireMother(motherCode);
        return toVariantVOs(variantMapper.selectByMotherAndChild(motherCode, child));
    }

    // ================================================================== 作答

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VariantAnswerResultVO answerVariant(String motherCode, String variantCode,
                                               VariantAnswerCommand command) {
        String childId = currentChildId();
        if (command == null) {
            throw new JeecgBootException("作答参数不能为空", 400);
        }
        StudyMotherQuestion mother = requireMother(motherCode);
        StudyQuestionVariant variant = requireVariant(motherCode, variantCode, childId);

        String actual = trim(command.getAnswer());
        if (actual.isEmpty()) {
            throw new JeecgBootException("答案不能为空", 400);
        }
        List<StepTagVO> tags = normalizeTags(command.getStepTags());

        // 判定（确定性规则，AI 只做解释层）
        JudgeResult judge = judgeVariant(variant, actual);
        boolean correct = judge.isCorrect();

        // 答错必须先选错因（4001）
        if (!correct && !ErrorCause.isValid(command.getErrorCause())) {
            throw new JeecgBootException("请先选择错因", StudyErrorCode.MISSING_ERROR_CAUSE);
        }

        // 幂等：同一孩子 + 同一变式 + 同一作答只落一条记录
        // （当月分表的存在性由 tableName() 内的守卫兜底，见 StudyRecordTableGuard）
        String recordId = recordId(childId, variant.getVariantCode(), actual, command.getErrorCause());
        StudyMathRecord existed = recordMapper.selectById(tableName(), recordId);
        if (existed != null) {
            return buildAnswerResult(variant, existed, tags, true);
        }

        StudyMathRecord record = new StudyMathRecord();
        record.setId(recordId);
        record.setChildId(childId);
        record.setContextId(childId);
        record.setSourceModule(MathConstants.SOURCE_MODULE);
        record.setContentType(MathConstants.CONTENT_TYPE_VARIANT);
        record.setContentId(variant.getVariantCode());
        record.setKnowledgePointCode(mother.getKnowledgePointCode());
        record.setAnswer(actual);
        record.setCorrect(correct ? 1 : 0);
        record.setJudgeSource(judge.getJudgeSource());
        record.setErrorCause(correct ? null : trim(command.getErrorCause()));
        record.setDurationMs(command.getDurationMs());
        record.setStepTags(buildStepTags(motherCode, judge, tags));
        record.setPracticeDate(Date.from(LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()));
        try {
            recordMapper.insertRecord(tableName(), record);
        } catch (DuplicateKeyException e) {
            StudyMathRecord raced = recordMapper.selectById(tableName(), recordId);
            if (raced != null) {
                return buildAnswerResult(variant, raced, tags, true);
            }
            throw e;
        }

        // 答错入统一错题池（各模块不建独立错题表）
        if (!correct) {
            String errorCode = errorPoolService.report(
                    buildReportCommand(childId, mother, variant, actual, command.getErrorCause(), judge));
            recordMapper.updateErrorCode(tableName(), recordId, errorCode);
            record.setErrorCode(errorCode);
        }
        return buildAnswerResult(variant, record, tags, false);
    }

    // ================================================================== 过关判定

    @Override
    public MotherPassResultVO submitPass(String motherCode, MotherPassCommand command) {
        String childId = currentChildId();
        StudyMotherQuestion mother = requireMother(motherCode);
        int required = requiredVariantCount(mother);

        List<StudyQuestionVariant> variants = variantMapper.selectByMotherAndChild(motherCode, childId);
        int ownVariants = 0;
        for (StudyQuestionVariant variant : variants) {
            if (variant.getChildId() != null && variant.getChildId().equals(childId)) {
                ownVariants++;
            }
        }
        if (ownVariants < required) {
            throw new JeecgBootException("变式不足，过关需要 " + required + " 道变式",
                    StudyErrorCode.VARIANT_NOT_ENOUGH);
        }

        int correctCount = 0;
        if (command != null && command.getVariantResults() != null) {
            for (VariantResultCommand result : command.getVariantResults()) {
                if (result == null) {
                    continue;
                }
                StudyQuestionVariant variant = findVariant(variants, result.getVariantCode(), childId);
                if (variant == null) {
                    continue;
                }
                if (judgeVariant(variant, trim(result.getAnswer())).isCorrect()) {
                    correctCount++;
                }
            }
        }
        boolean variantPassed = correctCount >= required;

        NarrativeResultVO narrative = evaluateNarrative(mother,
                command == null ? null : command.getNarrative());
        boolean passed = variantPassed && narrative.isNarrativePassed();

        MotherPassResultVO vo = new MotherPassResultVO();
        vo.setMotherCode(motherCode);
        vo.setPassed(passed);
        vo.setVariantPassed(variantPassed);
        vo.setVariantCorrectCount(correctCount);
        vo.setVariantRequiredCount(required);
        vo.setNarrativePassed(narrative.isNarrativePassed());
        vo.setHitKeywords(narrative.getHitKeywords());
        vo.setMissingKeywords(narrative.getMissingKeywords());
        vo.setFollowUpQuestion(narrative.getFollowUpQuestion());
        vo.setHint(passHint(variantPassed, correctCount, required, narrative.isNarrativePassed()));
        return vo;
    }

    @Override
    public NarrativeResultVO submitNarrative(String motherCode, NarrativeCommand command) {
        currentChildId();
        StudyMotherQuestion mother = requireMother(motherCode);
        return evaluateNarrative(mother, command == null ? null : command.getNarrative());
    }

    @Override
    public NarrativeBriefVO narrativeBrief(String motherCode) {
        StudyMotherQuestion mother = requireMother(motherCode);
        List<String> keywords = NarrativeKeywordExtractor.extract(mother.getKeyRelation());
        NarrativeBriefVO vo = new NarrativeBriefVO();
        vo.setMotherCode(motherCode);
        vo.setTitle(mother.getTitle());
        vo.setNarrativeRequired(true);
        vo.setKeyPointCount(keywords.size());
        vo.setPrompt("用你自己的话把这道题的关键关系讲一遍，我们一起看看讲清楚没有。");
        return vo;
    }

    @Override
    public List<StepTagVO> listStepTags(String motherCode, String childId) {
        String child = resolveChildId(childId);
        requireMother(motherCode);
        List<StepTagVO> result = new ArrayList<>();
        List<Map<String, Object>> rows = recordMapper.selectStepTagsByMother(
                tableName(), child, motherCode, MathConstants.SOURCE_MODULE);
        if (rows == null) {
            return result;
        }
        for (Map<String, Object> row : rows) {
            String json = stringValue(row.get("stepTags"));
            if (json == null || json.trim().isEmpty()) {
                continue;
            }
            try {
                Map<String, Object> meta = objectMapper.readValue(json, Map.class);
                Object tags = meta.get(META_TAGS);
                if (tags == null) {
                    continue;
                }
                List<StepTagVO> list = objectMapper.convertValue(tags, new TypeReference<List<StepTagVO>>() {
                });
                if (list != null) {
                    result.addAll(list);
                }
            } catch (Exception ignored) {
                // 脏数据不影响其它记录
            }
        }
        return result;
    }

    // ================================================================== 内部：五段校验

    /**
     * 五段齐全校验（F-MTH-01 / TC-04-01）。
     *
     * <p>缺任一段都返回 <b>4007</b>，并把缺哪一段写进提示语。</p>
     */
    private void validateFiveSections(MotherSaveCommand command) {
        if (command == null) {
            throw new JeecgBootException("母题参数不能为空", 400);
        }
        List<String> missing = new ArrayList<>();
        if (trim(command.getTitle()).isEmpty()) {
            missing.add(MotherSection.TITLE.getLabel());
        }
        if (command.getConditionTypes() == null || command.getConditionTypes().isEmpty()) {
            missing.add(MotherSection.CONDITION_TYPES.getLabel());
        }
        if (trim(command.getKeyRelation()).isEmpty()) {
            missing.add(MotherSection.KEY_RELATION.getLabel());
        }
        if (command.getSteps() == null || command.getSteps().isEmpty()) {
            missing.add(MotherSection.STEPS.getLabel());
        }
        if (command.getPitfalls() == null || command.getPitfalls().isEmpty()) {
            missing.add(MotherSection.PITFALLS.getLabel());
        }
        if (!missing.isEmpty()) {
            throw new JeecgBootException("母题五段不完整，缺少：" + String.join("、", missing),
                    StudyErrorCode.MOTHER_STRUCTURE_INCOMPLETE);
        }
    }

    private void applyCommand(StudyMotherQuestion mother, MotherSaveCommand command, String subject) {
        mother.setTitle(trim(command.getTitle()));
        mother.setConditionTypes(toJson(command.getConditionTypes()));
        mother.setKeyRelation(trim(command.getKeyRelation()));
        mother.setSteps(toJson(command.getSteps()));
        mother.setPitfalls(toJson(command.getPitfalls()));
        mother.setSubjectCode(subject);
        mother.setKnowledgePointCode(trim(command.getKnowledgePointCode()));
        mother.setPassVariantCount(command.getPassVariantCount() == null
                ? MathConstants.DEFAULT_PASS_VARIANT_COUNT : command.getPassVariantCount());
    }

    private String subjectOf(MotherSaveCommand command) {
        String subject = trim(command.getSubjectCode());
        return subject.isEmpty() ? MathConstants.SUBJECT_MATH : subject;
    }

    // ================================================================== 内部：口述判据

    /**
     * 口述判据：全部关键词命中才通过；否则只给追问与关键词反馈，<b>绝不给答案</b>。
     */
    private NarrativeResultVO evaluateNarrative(StudyMotherQuestion mother, String narrative) {
        List<String> keywords = NarrativeKeywordExtractor.extract(mother.getKeyRelation());
        List<String> hit = NarrativeKeywordExtractor.hit(keywords, narrative);
        List<String> missing = NarrativeKeywordExtractor.missing(keywords, narrative);
        boolean passed = !keywords.isEmpty() && hit.size() == keywords.size();

        NarrativeResultVO vo = new NarrativeResultVO();
        vo.setMotherCode(mother.getMotherCode());
        vo.setNarrativePassed(passed);
        vo.setHitKeywords(hit);
        vo.setMissingKeywords(missing);
        vo.setFollowUpQuestion(passed ? null : followUpQuestion(mother, keywords));
        return vo;
    }

    /**
     * 苏格拉底式追问：只提问，<b>不给答案、不复述关键关系</b>。
     */
    private String followUpQuestion(StudyMotherQuestion mother, List<String> keywords) {
        String title = mother.getTitle() == null ? "" : mother.getTitle().trim();
        if (!title.isEmpty() && !containsAny(title, keywords)) {
            return "「" + title + "」这道题里，哪几个量之间存在固定关系？";
        }
        return MathConstants.FOLLOW_UP_QUESTION;
    }

    private boolean containsAny(String text, List<String> keywords) {
        if (keywords == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isEmpty() && text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String passHint(boolean variantPassed, int correctCount, int required, boolean narrativePassed) {
        if (!variantPassed) {
            int left = required - correctCount;
            return "还有 " + (left > 0 ? left : 0) + " 道变式没做对，把它们都做对才能过关。";
        }
        if (!narrativePassed) {
            return "变式都做对了，再用自己的话把这道题的关键关系讲一遍。";
        }
        return "这一关过了。";
    }

    // ================================================================== 内部：作答记录

    /**
     * 当月作答记录分表名。
     *
     * <p>经 {@link StudyRecordTableGuard} 拿 —— 兜底（幂等建当月表）就挂在这里，
     * 所以本类不需要（也不该）再自己拼表名。</p>
     */
    private String tableName() {
        return recordTableGuard.currentTableName();
    }

    private String recordId(String childId, String variantCode, String answer, String errorCause) {
        String raw = childId + "|" + variantCode + "|" + answer + "|" + trim(errorCause);
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
            return IdWorker.get32UUID();
        }
    }

    private String buildStepTags(String motherCode, JudgeResult judge, List<StepTagVO> tags) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put(META_SESSION, "MATH-" + motherCode);
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

    private List<StepTagVO> normalizeTags(List<StepTagVO> tags) {
        List<StepTagVO> result = new ArrayList<>();
        if (tags == null) {
            return result;
        }
        for (StepTagVO tag : tags) {
            if (tag == null) {
                continue;
            }
            if (tag.getStepIndex() == null || tag.getStepIndex() <= 0) {
                throw new JeecgBootException("步骤序号不合法", StudyErrorCode.MISSING_ERROR_CAUSE);
            }
            if (!ErrorCause.isValid(tag.getErrorCause())) {
                throw new JeecgBootException("步骤错因必须是五维之一", StudyErrorCode.MISSING_ERROR_CAUSE);
            }
            result.add(new StepTagVO().setStepIndex(tag.getStepIndex()).setErrorCause(tag.getErrorCause().trim()));
        }
        return result;
    }

    private VariantAnswerResultVO buildAnswerResult(StudyQuestionVariant variant, StudyMathRecord record,
                                                     List<StepTagVO> tags, boolean duplicated) {
        VariantAnswerResultVO vo = new VariantAnswerResultVO();
        vo.setVariantCode(variant.getVariantCode());
        vo.setCorrect(record.getCorrect() != null && record.getCorrect() == 1);
        vo.setJudgeSource(JudgeSource.isValid(record.getJudgeSource())
                ? record.getJudgeSource() : JudgeSource.RULE.getCode());
        vo.setBasis(basisOf(record.getStepTags(), vo.getJudgeSource()));
        vo.setErrorCode(record.getErrorCode());
        vo.setStepTags(tags);
        vo.setDuplicated(duplicated);
        return vo;
    }

    private String basisOf(String stepTags, String judgeSource) {
        if (stepTags != null && !stepTags.trim().isEmpty()) {
            try {
                Map<String, Object> meta = objectMapper.readValue(stepTags, Map.class);
                Object basis = meta.get(META_BASIS);
                if (basis != null && !String.valueOf(basis).trim().isEmpty()) {
                    return String.valueOf(basis);
                }
            } catch (Exception ignored) {
                // 解析失败退回默认依据
            }
        }
        return "按「" + judgeSource + "」规则判定";
    }

    private StudyErrorReportCommand buildReportCommand(String childId, StudyMotherQuestion mother,
                                                        StudyQuestionVariant variant, String actual,
                                                        String errorCause, JudgeResult judge) {
        StudyErrorReportCommand report = new StudyErrorReportCommand();
        report.setChildId(childId);
        report.setSourceModule(MathConstants.SOURCE_MODULE);
        report.setContentType(MathConstants.CONTENT_TYPE_VARIANT);
        report.setContentId(variant.getVariantCode());
        report.setKnowledgePointCode(mother.getKnowledgePointCode());
        report.setSubjectCode(MathConstants.SUBJECT_MATH);
        report.setErrorCause(trim(errorCause));
        report.setRawAnswer(actual);
        report.setCorrectAnswer(variant.getAnswer());
        report.setJudgeSource(judge.getJudgeSource());
        report.setCopyrightType(COPYRIGHT_SELF);
        return report;
    }

    // ================================================================== 内部：判定

    private JudgeResult judgeVariant(StudyQuestionVariant variant, String actualAnswer) {
        JudgeRequest request = new JudgeRequest();
        request.setContentType(MathConstants.JUDGE_CONTENT_TYPE_NUMERIC);
        request.setExpectedAnswer(variant.getAnswer());
        request.setActualAnswer(actualAnswer);
        request.setTolerance(variant.getAnswerTolerance());
        JudgeResult judge = judgeEngine.judge(request);
        if (!JudgeSource.isValid(judge.getJudgeSource())) {
            judge.setJudgeSource(JudgeSource.RULE.getCode());
        }
        if (judge.getBasis() == null || judge.getBasis().trim().isEmpty()) {
            judge.setBasis("按数值容差规则判定");
        }
        return judge;
    }

    // ================================================================== 内部：数据范围

    /**
     * 解析目标孩子：孩子身份只认自己；家长身份必须与关系表求交集（越权 4010）。
     */
    private String resolveChildId(String requested) {
        String own = scopeService.ownChildId();
        if (own != null && !own.trim().isEmpty()) {
            return own;
        }
        String id = trim(requested);
        if (id.isEmpty()) {
            throw new StudyForbiddenException("请指定要访问的孩子");
        }
        scopeService.assertChildAccess(id);
        return id;
    }

    /**
     * 当前登录孩子；家长等非孩子身份一律 4010。
     */
    private String currentChildId() {
        String own = scopeService.ownChildId();
        if (own == null || own.trim().isEmpty()) {
            throw new StudyForbiddenException("当前登录身份不是孩子，无权提交学习数据");
        }
        return own;
    }

    // ================================================================== 内部：查询与组装

    private StudyMotherQuestion requireMother(String motherCode) {
        String code = trim(motherCode);
        if (code.isEmpty()) {
            throw new JeecgBootException("母题业务ID不能为空", 400);
        }
        StudyMotherQuestion mother = motherMapper.selectByMotherCode(code);
        if (mother == null) {
            throw new JeecgBootException("母题不存在：" + code, 400);
        }
        return mother;
    }

    private StudyQuestionVariant requireVariant(String motherCode, String variantCode, String childId) {
        String code = trim(variantCode);
        if (code.isEmpty()) {
            throw new JeecgBootException("变式业务ID不能为空", 400);
        }
        StudyQuestionVariant variant = variantMapper.selectByVariantCodeAnyChild(code);
        if (variant == null) {
            throw new JeecgBootException("变式不存在：" + code, 400);
        }
        // 别人的变式：越权一律 4010，不用「不存在」掩盖
        if (variant.getChildId() != null && !variant.getChildId().equals(childId)) {
            throw new StudyForbiddenException();
        }
        if (!motherCode.equals(variant.getMotherCode())) {
            throw new JeecgBootException("变式不属于该母题：" + code, 400);
        }
        return variant;
    }

    private StudyQuestionVariant findVariant(List<StudyQuestionVariant> variants, String variantCode, String childId) {
        String code = trim(variantCode);
        if (code.isEmpty()) {
            return null;
        }
        for (StudyQuestionVariant variant : variants) {
            if (code.equals(variant.getVariantCode())
                    && (variant.getChildId() == null || variant.getChildId().equals(childId))) {
                return variant;
            }
        }
        return null;
    }

    private int requiredVariantCount(StudyMotherQuestion mother) {
        Integer count = mother.getPassVariantCount();
        if (count == null || count <= 0) {
            return MathConstants.DEFAULT_PASS_VARIANT_COUNT;
        }
        return count;
    }

    private List<VariantVO> toVariantVOs(List<StudyQuestionVariant> variants) {
        List<VariantVO> list = new ArrayList<>();
        if (variants == null) {
            return list;
        }
        for (StudyQuestionVariant variant : variants) {
            VariantVO vo = new VariantVO();
            vo.setVariantCode(variant.getVariantCode());
            vo.setMotherCode(variant.getMotherCode());
            vo.setLayer(variant.getLayer());
            VariantLayer layer = VariantLayer.of(variant.getLayer());
            vo.setLayerName(layer == null ? "" : layer.getLayerName());
            vo.setStem(variant.getStem());
            vo.setGeneratedBy(variant.getGeneratedBy());
            vo.setAnswerTolerance(variant.getAnswerTolerance());
            vo.setParams(paramsOf(variant.getParamsSnapshot()));
            list.add(vo);
        }
        return list;
    }

    private Map<String, Object> paramsOf(String paramsSnapshot) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (paramsSnapshot == null || paramsSnapshot.trim().isEmpty()) {
            return params;
        }
        try {
            Map<String, Object> meta = objectMapper.readValue(paramsSnapshot, Map.class);
            Object snapshot = meta.get("params");
            if (snapshot instanceof Map) {
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) snapshot).entrySet()) {
                    params.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
        } catch (Exception ignored) {
            // 快照解析失败不阻塞展示
        }
        return params;
    }

    private MotherDetailVO toDetail(StudyMotherQuestion mother) {
        MotherDetailVO vo = new MotherDetailVO();
        vo.setMotherCode(mother.getMotherCode());
        vo.setTitle(mother.getTitle());
        vo.setConditionTypes(parseConditions(mother.getConditionTypes()));
        vo.setKeyRelation(mother.getKeyRelation());
        vo.setSteps(parseSteps(mother.getSteps()));
        vo.setPitfalls(parsePitfalls(mother.getPitfalls()));
        vo.setSubjectCode(mother.getSubjectCode());
        vo.setKnowledgePointCode(mother.getKnowledgePointCode());
        vo.setPassVariantCount(mother.getPassVariantCount());
        vo.setOrderNo(mother.getOrderNo());
        vo.setStatus(mother.getStatus());
        vo.setSectionOrder(MotherSection.orderedCodes());
        vo.setKeyRelationHighlighted(true);
        vo.setHighlightSection(MotherSection.KEY_RELATION.getCode());
        return vo;
    }

    private MotherListItemVO toListItem(StudyMotherQuestion mother, MotherStat stat) {
        MotherListItemVO vo = new MotherListItemVO();
        vo.setMotherCode(mother.getMotherCode());
        vo.setTitle(mother.getTitle());
        vo.setSubjectCode(mother.getSubjectCode());
        vo.setKnowledgePointCode(mother.getKnowledgePointCode());
        vo.setOutlineWeight(outlineWeight(mother));
        vo.setOrderNo(mother.getOrderNo());
        vo.setVariantCount(mother.getVariantCount());
        int attempts = stat == null ? 0 : stat.attempts;
        int errors = stat == null ? 0 : stat.errors;
        int consecutive = stat == null ? 0 : stat.streak;
        vo.setAttemptCount(attempts);
        vo.setErrorCount(errors);
        vo.setConsecutiveErrors(consecutive);
        vo.setMastery(masteryOf(stat));
        vo.setPassed(stat != null && stat.correctVariantCodes.size() >= requiredVariantCount(mother));
        return vo;
    }

    private BigDecimal outlineWeight(StudyMotherQuestion mother) {
        String pointCode = trim(mother.getKnowledgePointCode());
        if (pointCode.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal weight = knowledgePointMapper.selectOutlineWeight(pointCode);
        return weight == null ? BigDecimal.ZERO : weight;
    }

    /**
     * 掌握度：没练过取中性值；练过按（答对 − 连续错）/ 作答次数 计算，下限 0。
     *
     * <p>连续错的关卡掌握度最低，因此在同权重档内排最前（TC-04-10）。</p>
     */
    private BigDecimal masteryOf(MotherStat stat) {
        if (stat == null || stat.attempts == 0) {
            return MathConstants.MASTERY_NEUTRAL;
        }
        BigDecimal value = BigDecimal.valueOf(Math.max(0, stat.correct - stat.streak))
                .divide(BigDecimal.valueOf(stat.attempts), 4, java.math.RoundingMode.HALF_UP);
        return value;
    }

    /**
     * 汇总某个孩子在母题域的作答情况。
     */
    private Map<String, MotherStat> statsOf(String childId) {
        Map<String, MotherStat> stats = new LinkedHashMap<>();
        List<Map<String, Object>> rows = recordMapper.selectAttempts(
                tableName(), childId, MathConstants.SOURCE_MODULE);
        if (rows == null) {
            return stats;
        }
        for (Map<String, Object> row : rows) {
            String motherCode = stringValue(row.get("motherCode"));
            if (motherCode == null) {
                continue;
            }
            MotherStat stat = stats.computeIfAbsent(motherCode, key -> new MotherStat());
            stat.attempts++;
            int correct = intValue(row.get("correct"));
            String contentId = stringValue(row.get("contentId"));
            if (correct == 1) {
                stat.correct++;
                stat.streak = 0;
                if (contentId != null) {
                    stat.correctVariantCodes.add(contentId);
                }
            } else {
                stat.errors++;
                stat.streak++;
            }
        }
        return stats;
    }

    // ================================================================== 内部：工具

    private List<ConditionTypeVO> parseConditions(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ConditionTypeVO>>() {
            });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private List<MotherStepVO> parseSteps(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<MotherStepVO>>() {
            });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private List<String> parsePitfalls(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new JeecgBootException("内容序列化失败", 400);
        }
    }

    private String nextVariantCode(String motherCode, int layer) {
        String sequence = idSequence.next("QST-VAR");
        int index = sequence.lastIndexOf('-');
        String number = index >= 0 ? sequence.substring(index + 1) : sequence;
        String code = "QST-VAR-" + motherCode + "-" + layer + "-" + number;
        if (code.length() > 32) {
            code = "QST-VAR-" + motherCode + "-" + layer + "-" + number.substring(number.length() - 4);
        }
        return code;
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

    /**
     * 某个母题在该孩子身上的作答统计。
     */
    private static final class MotherStat {

        /** 作答次数 */
        private int attempts;

        /** 答对次数 */
        private int correct;

        /** 答错次数 */
        private int errors;

        /** 当前连续答错长度 */
        private int streak;

        /** 答对过的变式（用于「这一类题会不会了」统计） */
        private final Set<String> correctVariantCodes = new LinkedHashSet<>();
    }
}
