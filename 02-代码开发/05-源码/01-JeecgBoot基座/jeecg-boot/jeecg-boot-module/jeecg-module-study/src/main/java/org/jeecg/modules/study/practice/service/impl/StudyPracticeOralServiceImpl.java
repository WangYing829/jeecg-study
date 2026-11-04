package org.jeecg.modules.study.practice.service.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.study.common.exception.StudyForbiddenException;
import org.jeecg.modules.study.math.constant.MathConstants;
import org.jeecg.modules.study.math.service.StudyMathService;
import org.jeecg.modules.study.math.vo.MathProgressVO;
import org.jeecg.modules.study.math.vo.NarrativeCommand;
import org.jeecg.modules.study.math.vo.NarrativeResultVO;
import org.jeecg.modules.study.practice.entity.StudyQuestion;
import org.jeecg.modules.study.practice.mapper.StudyPracticeQuestionMapper;
import org.jeecg.modules.study.practice.service.StudyPracticeOralService;
import org.jeecg.modules.study.practice.vo.PracticeOralCommand;
import org.jeecg.modules.study.practice.vo.PracticeOralResultVO;
import org.jeecg.modules.study.scope.service.ChildScopeService;

/**
 * 口述关键关系服务实现（F-MTH-03）。
 *
 * <p><b>隔离口径</b>：口述是<b>孩子自己的学习行为</b>，
 * 身份来自会话（{@code study_child.user_id} 实时反查），不接受前端传 childId。</p>
 *
 * <p><b>判据来源</b>：母题第 3 段「关键关系」。母题域已经有同一套判定
 * （{@code StudyMathService#submitNarrative}），本服务直接复用，
 * 避免「练习域一套口径、母题域另一套口径」。</p>
 *
 * <p><b>依赖方向</b>：练习域 → 母题域（单向）。母题域不依赖练习域，
 * 因此不存在循环依赖。</p>
 *
 * @author jeecg-study
 */
@Service
@RequiredArgsConstructor
public class StudyPracticeOralServiceImpl implements StudyPracticeOralService {

    private final ChildScopeService scopeService;
    private final StudyPracticeQuestionMapper questionMapper;
    private final StudyMathService mathService;

    @Override
    public PracticeOralResultVO submitOral(String contentId, PracticeOralCommand command) {
        String code = contentId == null ? "" : contentId.trim();
        if (code.isEmpty()) {
            throw new JeecgBootException("内容业务ID不能为空", 400);
        }
        // 口述是孩子自己的学习行为：身份只认会话里的自己
        String childId = scopeService.ownChildId();
        if (childId == null || childId.trim().isEmpty()) {
            throw new StudyForbiddenException("当前登录身份不是孩子，无权提交口述");
        }

        StudyQuestion question = questionMapper.selectByQuestionCode(code);
        if (question == null) {
            throw new JeecgBootException("内容不存在：" + code, 400);
        }

        PracticeOralResultVO vo = new PracticeOralResultVO();
        vo.setContentId(code);

        String motherCode = resolveMotherCode(question);
        vo.setMotherCode(motherCode);
        if (motherCode == null) {
            // 没有母题就没有「关键关系」判据：只给追问，不判对
            vo.setFollowUpQuestion(MathConstants.FOLLOW_UP_QUESTION);
            return vo;
        }

        String speechText = command == null ? null : command.getSpeechText();
        NarrativeResultVO narrative;
        try {
            narrative = mathService.submitNarrative(motherCode,
                    new NarrativeCommand().setNarrative(speechText));
        } catch (JeecgBootException e) {
            // 母题缺失或结构不完整：不当成致命错误，只给追问
            vo.setFollowUpQuestion(MathConstants.FOLLOW_UP_QUESTION);
            return vo;
        }

        vo.setPassed(Boolean.TRUE.equals(narrative.isNarrativePassed()));
        vo.setMatchedKeywords(safeList(narrative.getHitKeywords()));
        vo.setMissingKeywords(safeList(narrative.getMissingKeywords()));
        vo.setFollowUpQuestion(narrative.getFollowUpQuestion());
        vo.setMotherPassed(isMotherMastered(childId, motherCode));
        return vo;
    }

    // ------------------------------------------------------------------ 内部

    /**
     * 找这道题对应的母题：先看题目自己挂的母题，再退到「考点挂的母题」。
     */
    private String resolveMotherCode(StudyQuestion question) {
        String mother = question.getMotherQuestionCode();
        if (mother != null && !mother.trim().isEmpty()) {
            return mother.trim();
        }
        String pointCode = question.getKnowledgePointCode();
        if (pointCode == null || pointCode.trim().isEmpty()) {
            return null;
        }
        String byPoint = questionMapper.selectMotherCodeByPointCode(pointCode.trim());
        return byPoint == null || byPoint.trim().isEmpty() ? null : byPoint.trim();
    }

    /**
     * 母题是否已过关（变式全对口径）。
     *
     * <p>口述结果本身不落库，正式的过关判定走母题域的过关接口，
     * 所以这里只能给「变式已全对」这一半的口径。</p>
     */
    private boolean isMotherMastered(String childId, String motherCode) {
        try {
            MathProgressVO progress = mathService.progress(childId);
            List<String> mastered = progress == null ? null : progress.getMasteredMotherCodes();
            return mastered != null && mastered.contains(motherCode);
        } catch (Exception e) {
            return false;
        }
    }

    private List<String> safeList(List<String> values) {
        return values == null ? new ArrayList<>() : values;
    }
}
