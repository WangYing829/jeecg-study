package org.jeecg.modules.study.context.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;

import lombok.RequiredArgsConstructor;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.study.common.config.StudyProperties;
import org.jeecg.modules.study.common.constant.StudyErrorCode;
import org.jeecg.modules.study.common.exception.StudyForbiddenException;
import org.jeecg.modules.study.context.constant.StudyStageGrade;
import org.jeecg.modules.study.context.entity.StudyContext;
import org.jeecg.modules.study.context.entity.StudyTextbookVersion;
import org.jeecg.modules.study.context.mapper.StudyGlobalContextMapper;
import org.jeecg.modules.study.context.mapper.StudyTextbookVersionMapper;
import org.jeecg.modules.study.context.service.StudyContextService;
import org.jeecg.modules.study.context.service.StudyTextbookVersionService;
import org.jeecg.modules.study.context.vo.ContextSaveRequest;
import org.jeecg.modules.study.context.vo.ContextVO;
import org.jeecg.modules.study.family.entity.StudyChild;
import org.jeecg.modules.study.family.mapper.StudyChildMapper;
import org.jeecg.modules.study.idseq.service.StudyIdSequenceService;
import org.jeecg.modules.study.scope.service.ChildScopeService;

/**
 * 全局上下文服务实现。
 *
 * <p><b>隔离口径</b>：上下文表带 {@code child_id}，所有读写都强制拼 child_id。
 * 孩子身份只有自己；家长身份按关系表求交集，越权一律 4010。</p>
 *
 * <p><b>目标孩子的解析顺序</b>：显式 {@code childCode} → 孩子身份的自己 →
 * 家长可见孩子中按 ID 升序的第一个。家长有多个孩子时建议显式传 {@code childCode}。</p>
 *
 * @author jeecg-study
 */
@Service
@RequiredArgsConstructor
public class StudyContextServiceImpl implements StudyContextService {

    private final StudyGlobalContextMapper contextMapper;
    private final StudyTextbookVersionMapper textbookVersionMapper;
    private final StudyTextbookVersionService textbookVersionService;
    private final ChildScopeService scopeService;
    private final StudyChildMapper childMapper;
    private final StudyIdSequenceService idSequence;
    private final StudyProperties studyProperties;

    @Override
    public ContextVO current(String childCode) {
        String childId = resolveChildId(childCode);
        if (childId == null) {
            return new ContextVO().setConfigured(false);
        }
        StudyContext context = contextMapper.selectCurrentByChildId(childId);
        if (context == null) {
            return new ContextVO().setChildId(childId).setConfigured(false);
        }
        return toVO(context);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContextVO save(ContextSaveRequest request, String childCode) {
        if (request == null) {
            throw new JeecgBootException("请求体不能为空", 400);
        }
        String childId = resolveChildId(childCode);
        if (childId == null) {
            throw new JeecgBootException("尚未建立孩子档案，无法配置上下文", StudyErrorCode.CONTEXT_NOT_CONFIGURED);
        }

        String stageCode = normalize(request.getStageCode());
        if (!StudyStageGrade.isValidStage(stageCode)) {
            throw new JeecgBootException("学段不合法：" + request.getStageCode(), StudyErrorCode.CONTEXT_NOT_CONFIGURED);
        }

        String gradeCode = normalize(request.getGradeCode());
        if (!StudyStageGrade.gradeInStage(stageCode, gradeCode)) {
            throw new JeecgBootException("年级 " + gradeCode + " 不属于学段 " + stageCode
                    + "（该学段可选 " + StudyStageGrade.gradesOf(stageCode) + "）", StudyErrorCode.CONTEXT_NOT_CONFIGURED);
        }

        String provinceCode = normalize(request.getProvinceCode());
        if (provinceCode.isEmpty()) {
            throw new JeecgBootException("省级行政区不能为空", StudyErrorCode.CONTEXT_NOT_CONFIGURED);
        }
        String provinceName = request.getProvinceName() == null || request.getProvinceName().trim().isEmpty()
                ? StudyStageGrade.provinceName(provinceCode) : request.getProvinceName().trim();

        String versionId = request.getTextbookVersionId() == null ? null : request.getTextbookVersionId().trim();
        if (versionId == null || versionId.isEmpty()) {
            throw new JeecgBootException("教材版本不能为空", StudyErrorCode.CONTEXT_NOT_CONFIGURED);
        }
        textbookVersionService.ensureSeed();
        StudyTextbookVersion version = textbookVersionMapper.selectById(versionId);
        if (version == null) {
            throw new JeecgBootException("教材版本不存在：" + versionId, StudyErrorCode.CONTEXT_NOT_CONFIGURED);
        }

        // 完全相同的当前上下文直接复用，不重复建档
        StudyContext current = contextMapper.selectCurrentByChildId(childId);
        if (current != null && sameKey(current, provinceCode, stageCode, gradeCode, versionId)) {
            return toVO(current);
        }

        // 旧上下文置为非当前（保留，不删除 —— 历史记录仍可按旧 context_id 查到）
        contextMapper.clearCurrentByChildId(childId);

        StudyContext context = new StudyContext();
        context.setId(IdWorker.get32UUID());
        context.setChildId(childId);
        context.setContextCode(idSequence.next("CTX"));
        context.setProvinceCode(provinceCode);
        context.setProvinceName(provinceName);
        context.setStageCode(stageCode);
        context.setGradeCode(gradeCode);
        context.setTextbookVersionId(versionId);
        context.setEyeIntervalMin(request.getEyeIntervalMin() == null || request.getEyeIntervalMin() <= 0
                ? studyProperties.getEyeIntervalMin() : request.getEyeIntervalMin());
        context.setIsCurrent(1);
        context.setEffectiveFrom(new Date());
        context.setDelFlag(0);
        contextMapper.insert(context);
        return toVO(context);
    }

    @Override
    public List<ContextVO> list(String childCode) {
        String childId = resolveChildId(childCode);
        if (childId == null) {
            return new ArrayList<>();
        }
        List<StudyContext> rows = contextMapper.selectByChildId(childId);
        List<ContextVO> list = new ArrayList<>();
        if (rows == null) {
            return list;
        }
        for (StudyContext row : rows) {
            list.add(toVO(row));
        }
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContextVO updateEyeInterval(String contextIdOrCode, Integer eyeIntervalMin) {
        if (eyeIntervalMin == null || eyeIntervalMin < 1 || eyeIntervalMin > 240) {
            throw new JeecgBootException("护眼提醒间隔必须是 1–240 分钟", 400);
        }
        StudyContext context = contextMapper.selectById(contextIdOrCode);
        if (context == null) {
            context = contextMapper.selectByContextCode(contextIdOrCode);
        }
        if (context == null) {
            throw new JeecgBootException("上下文不存在：" + contextIdOrCode, StudyErrorCode.CONTEXT_NOT_CONFIGURED);
        }
        // 越权一律 4010（不用 404，避免资源探测）
        scopeService.assertChildAccess(context.getChildId());

        context.setEyeIntervalMin(eyeIntervalMin);
        contextMapper.updateById(context);
        return toVO(context);
    }

    // ------------------------------------------------------------------ 内部

    /**
     * 解析当前操作的孩子 ID；解析不出返回 null（表示「还没有孩子」）。
     */
    private String resolveChildId(String childCode) {
        if (childCode != null && !childCode.trim().isEmpty()) {
            scopeService.assertChildCodeAccess(childCode.trim());
            StudyChild child = childMapper.selectByChildCode(childCode.trim());
            if (child == null) {
                throw new StudyForbiddenException();
            }
            return child.getId();
        }
        List<String> visible = scopeService.visibleChildIds();
        if (visible == null || visible.isEmpty()) {
            return null;
        }
        List<String> sorted = new ArrayList<>(visible);
        Collections.sort(sorted);
        return sorted.get(0);
    }

    /**
     * 判断两个上下文的配置键是否完全一致。
     */
    private boolean sameKey(StudyContext context, String provinceCode, String stageCode,
                            String gradeCode, String versionId) {
        return provinceCode.equals(context.getProvinceCode())
                && stageCode.equals(context.getStageCode())
                && gradeCode.equals(context.getGradeCode())
                && versionId.equals(context.getTextbookVersionId());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private ContextVO toVO(StudyContext context) {
        ContextVO vo = new ContextVO();
        vo.setContextId(context.getId());
        vo.setChildId(context.getChildId());
        vo.setContextCode(context.getContextCode());
        vo.setProvinceCode(context.getProvinceCode());
        vo.setProvinceName(context.getProvinceName());
        vo.setStageCode(context.getStageCode());
        vo.setStageName(StudyStageGrade.stageName(context.getStageCode()));
        vo.setGradeCode(context.getGradeCode());
        vo.setGradeName(StudyStageGrade.gradeName(context.getGradeCode()));
        vo.setTextbookVersionId(context.getTextbookVersionId());
        StudyTextbookVersion version = context.getTextbookVersionId() == null
                ? null : textbookVersionMapper.selectById(context.getTextbookVersionId());
        vo.setTextbookVersionName(version == null ? null : version.getVersionName());
        vo.setEyeIntervalMin(context.getEyeIntervalMin());
        vo.setEffectiveFrom(context.getEffectiveFrom());
        vo.setConfigured(true);
        return vo;
    }
}
