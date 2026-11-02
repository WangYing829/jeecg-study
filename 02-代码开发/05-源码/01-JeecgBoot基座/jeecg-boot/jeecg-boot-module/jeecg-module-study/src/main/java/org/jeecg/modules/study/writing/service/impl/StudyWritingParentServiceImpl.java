package org.jeecg.modules.study.writing.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.study.common.exception.StudyForbiddenException;
import org.jeecg.modules.study.scope.service.ChildScopeService;
import org.jeecg.modules.study.writing.constant.WritingConstants;
import org.jeecg.modules.study.writing.entity.StudyWriting;
import org.jeecg.modules.study.writing.enums.WritingLayer;
import org.jeecg.modules.study.writing.mapper.StudyWritingFeedbackMapper;
import org.jeecg.modules.study.writing.service.StudyWritingParentService;
import org.jeecg.modules.study.writing.vo.WritingDraftBriefVO;
import org.jeecg.modules.study.writing.vo.WritingFeedbackListVO;
import org.jeecg.modules.study.writing.vo.WritingFeedbackRequest;
import org.jeecg.modules.study.writing.vo.WritingFeedbackVO;

/**
 * 家长端写作域服务实现（F-PAR-02 / F-WRT-06）。
 *
 * <p><b>数据范围</b>：家长只能对自己关联的孩子提交反馈、也只能看到自己关联孩子的反馈；
 * 可见孩子集合由 {@code scopeService.visibleChildIds()}（关系表求交集）给出，
 * 越权一律 4010（R-04 / ADR-018）。</p>
 *
 * <p><b>家长的反馈不进入孩子的练习数据流</b>：反馈单独存一层（{@code layer = FEEDBACK}），
 * 孩子的所有查询都不读这一层，因此孩子端的任何位置都不会出现家长反馈（TC-06-09）。</p>
 *
 * @author jeecg-study
 */
@Service
@RequiredArgsConstructor
public class StudyWritingParentServiceImpl implements StudyWritingParentService {

    private final StudyWritingFeedbackMapper writingFeedbackMapper;
    private final ChildScopeService scopeService;

    // ------------------------------------------------------------------ 提交反馈

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WritingFeedbackVO submitFeedback(WritingFeedbackRequest request) {
        if (request == null) {
            throw new JeecgBootException("参数不能为空", 400);
        }
        String targetType = trim(request.getTargetType());
        if (!WritingConstants.TARGET_TYPE_WRITING.equalsIgnoreCase(targetType)) {
            throw new JeecgBootException("暂不支持的反馈对象类型", 400);
        }
        String targetCode = trim(request.getTargetCode());
        if (targetCode.isEmpty()) {
            throw new JeecgBootException("反馈对象不能为空", 400);
        }
        String content = trim(request.getContent());
        if (content.isEmpty()) {
            throw new JeecgBootException("反馈内容不能为空", 400);
        }
        // 上限 100 字：超限直接拒绝，且不落库（TC-06-08）
        if (content.length() > WritingConstants.MAX_FEEDBACK_LENGTH) {
            throw new JeecgBootException(
                    "反馈最多 " + WritingConstants.MAX_FEEDBACK_LENGTH + " 字，请精简后再提交", 400);
        }

        // 反馈对象必须是「自己关联的孩子」的作文：孩子集合来自关系表求交集
        List<String> visibleChildIds = scopeService.visibleChildIds();
        if (visibleChildIds == null || visibleChildIds.isEmpty()) {
            throw new StudyForbiddenException("无权访问该孩子的数据");
        }
        StudyWriting draft = writingFeedbackMapper.selectDraftByCode(targetCode, visibleChildIds);
        if (draft == null) {
            // 不存在与「存在但无权」返回完全一致的响应，避免资源探测
            throw new StudyForbiddenException("反馈对象不存在或无权访问");
        }
        String childId = draft.getChildId();

        // 幂等：同一条反馈重复提交只落一行
        String feedbackCode = "WRT-FEEDBACK-" + WritingConstants.digest12(
                childId + "|FEEDBACK|" + targetCode + "|" + content);
        StudyWriting existed = writingFeedbackMapper.selectFeedbackByCode(childId, feedbackCode);
        if (existed != null) {
            return toFeedbackVO(existed);
        }

        StudyWriting row = new StudyWriting();
        row.setChildId(childId);
        row.setWritingCode(feedbackCode);
        row.setLayer(WritingLayer.FEEDBACK.getCode());
        row.setTitle(trim(draft.getTitle()));
        row.setContent(content);
        row.setSubjectCode(WritingConstants.SUBJECT_CODE);
        row.setReferenceCode(targetCode);
        row.setCopyrightType(WritingConstants.COPYRIGHT_SELF);
        row.setStatus(1);
        row.setDelFlag(0);
        row.setCreateBy(scopeService.currentUserId());
        row.setCreateTime(new Date());
        try {
            writingFeedbackMapper.insert(row);
        } catch (DuplicateKeyException e) {
            StudyWriting raced = writingFeedbackMapper.selectFeedbackByCode(childId, feedbackCode);
            if (raced != null) {
                return toFeedbackVO(raced);
            }
            throw e;
        }
        return toFeedbackVO(row);
    }

    // ------------------------------------------------------------------ 反馈清单

    @Override
    public WritingFeedbackListVO listFeedbacks(String childCode) {
        List<String> visibleChildIds = scopeService.visibleChildIds();
        if (visibleChildIds == null || visibleChildIds.isEmpty()) {
            throw new StudyForbiddenException("无权访问该孩子的数据");
        }
        List<String> targetChildIds = visibleChildIds;
        String code = trim(childCode);
        if (!code.isEmpty()) {
            // 越权一律 4010（不用 404，避免资源探测）
            scopeService.assertChildCodeAccess(code);
            String childId = writingFeedbackMapper.selectChildIdByChildCode(code);
            if (childId == null || !visibleChildIds.contains(childId)) {
                throw new StudyForbiddenException("无权访问该孩子的数据");
            }
            targetChildIds = Collections.singletonList(childId);
        }
        List<StudyWriting> rows = writingFeedbackMapper.selectFeedbacks(targetChildIds);
        List<WritingFeedbackVO> items = new ArrayList<>();
        if (rows != null) {
            for (StudyWriting row : rows) {
                if (row != null) {
                    items.add(toFeedbackVO(row));
                }
            }
        }
        WritingFeedbackListVO vo = new WritingFeedbackListVO();
        vo.setItems(items);
        vo.setTotal(items.size());
        return vo;
    }

    // ------------------------------------------------------------------ 初稿清单

    @Override
    public List<WritingDraftBriefVO> listDrafts(String childCode) {
        // 可见孩子集合：关系表求交集后的结果
        List<String> visibleChildIds = scopeService.visibleChildIds();
        if (visibleChildIds == null || visibleChildIds.isEmpty()) {
            throw new StudyForbiddenException("无权访问该孩子的数据");
        }
        List<String> targetChildIds = visibleChildIds;
        String code = trim(childCode);
        if (!code.isEmpty()) {
            // 不存在与「存在但无权」返回完全一致的响应，避免资源探测
            scopeService.assertChildCodeAccess(code);
            String childId = writingFeedbackMapper.selectChildIdByChildCode(code);
            if (childId == null || !visibleChildIds.contains(childId)) {
                throw new StudyForbiddenException("无权访问该孩子的数据");
            }
            targetChildIds = Collections.singletonList(childId);
        }

        Map<String, String> childCodeById = childCodesOf(targetChildIds);
        List<StudyWriting> rows = writingFeedbackMapper.selectDraftsByChildIds(targetChildIds);
        List<WritingDraftBriefVO> items = new ArrayList<>();
        if (rows != null) {
            for (StudyWriting row : rows) {
                if (row == null) {
                    continue;
                }
                WritingDraftBriefVO vo = new WritingDraftBriefVO();
                vo.setWritingCode(row.getWritingCode());
                vo.setTitle(trim(row.getTitle()));
                vo.setCreateTime(row.getCreateTime());
                vo.setChildCode(childCodeById.get(trim(row.getChildId())));
                items.add(vo);
            }
        }
        return items;
    }

    /**
     * 孩子主键 ID → 孩子业务ID。
     */
    private Map<String, String> childCodesOf(List<String> childIds) {
        Map<String, String> codeById = new LinkedHashMap<>();
        if (childIds == null || childIds.isEmpty()) {
            return codeById;
        }
        List<Map<String, Object>> rows = writingFeedbackMapper.selectChildCodesByIds(childIds);
        if (rows == null) {
            return codeById;
        }
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            Object id = row.get("id");
            Object childCode = row.get("child_code");
            if (childCode == null) {
                childCode = row.get("childCode");
            }
            if (id != null && childCode != null) {
                codeById.put(String.valueOf(id), String.valueOf(childCode));
            }
        }
        return codeById;
    }

    // ------------------------------------------------------------------ 内部

    private WritingFeedbackVO toFeedbackVO(StudyWriting row) {
        WritingFeedbackVO vo = new WritingFeedbackVO();
        vo.setFeedbackCode(row.getWritingCode());
        vo.setTargetType(WritingConstants.TARGET_TYPE_WRITING);
        vo.setTargetCode(trim(row.getReferenceCode()));
        vo.setContent(row.getContent());
        vo.setCreateTime(row.getCreateTime());
        return vo;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
