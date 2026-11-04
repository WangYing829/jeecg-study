package org.jeecg.modules.study.error.service.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.study.error.entity.StudyErrorPool;
import org.jeecg.modules.study.error.mapper.StudyErrorPoolMapper;
import org.jeecg.modules.study.error.mapper.StudyErrorQuestionMapper;
import org.jeecg.modules.study.error.service.StudyErrorManageService;
import org.jeecg.modules.study.error.service.StudyErrorVariantService;
import org.jeecg.modules.study.error.vo.ErrorVariantGenerateVO;
import org.jeecg.modules.study.error.vo.ErrorVariantListVO;
import org.jeecg.modules.study.error.vo.ErrorVariantVO;
import org.jeecg.modules.study.math.service.StudyMathService;
import org.jeecg.modules.study.math.vo.VariantGenerateResultVO;
import org.jeecg.modules.study.math.vo.VariantVO;
import org.jeecg.modules.study.scope.service.ChildScopeService;

/**
 * 错题变式生成服务实现（F-WRG-03）。
 *
 * <p><b>隔离口径</b>：错题先按业务ID取出来，再校验当前身份对它的孩子有访问权
 * （关系表求交集），越权一律 4010（不用 404，避免资源探测）。</p>
 *
 * <p><b>不给答案</b>：变式响应里只有题干与层级，答案只在服务端参与判定（R-01）。</p>
 *
 * @author jeecg-study
 */
@Service
@RequiredArgsConstructor
public class StudyErrorVariantServiceImpl implements StudyErrorVariantService {

    private final StudyErrorPoolMapper errorPoolMapper;
    private final StudyErrorQuestionMapper questionMapper;
    private final StudyErrorManageService errorManageService;
    private final StudyMathService mathService;
    private final ChildScopeService scopeService;

    @Override
    public ErrorVariantGenerateVO generateVariants(String errorCode) {
        String code = errorCode == null ? "" : errorCode.trim();
        if (code.isEmpty()) {
            throw new JeecgBootException("错题业务ID不能为空", 400);
        }
        StudyErrorPool error = errorPoolMapper.selectByErrorCode(code);
        if (error == null) {
            throw new JeecgBootException("错题不存在：" + code, 400);
        }
        // 数据范围：家长只能给自己关联的孩子生成变式，孩子只能给自己生成
        scopeService.assertChildAccess(error.getChildId());

        ErrorVariantGenerateVO vo = new ErrorVariantGenerateVO();
        vo.setErrorCode(error.getErrorCode());

        String motherCode = resolveMotherCode(error);
        vo.setMotherCode(motherCode);

        if (motherCode == null) {
            // 没有母题 → 降级为同知识点同题型的候选题（不产生新题）
            ErrorVariantListVO degraded = errorManageService.listVariants(code);
            vo.setDegradeType(degraded.getDegradeType());
            vo.setVariants(degraded.getVariants());
            vo.setVariantAvailable(degraded.getVariantAvailable());
            vo.setGenerated(Boolean.FALSE);
            vo.setMessage(degraded.getVariantAvailable()
                    ? "这道题没有挂母题，先用同知识点的同类题练一练"
                    : "暂时没有可用的同类题，这道题仍会留在复习队列里");
            return vo;
        }

        VariantGenerateResultVO generated = mathService.generateVariants(motherCode, error.getChildId());
        vo.setVariants(toErrorVariants(generated));
        vo.setVariantAvailable(vo.getVariants().size() > 0);
        vo.setDegradeType("MOTHER");
        vo.setGenerated(!generated.isDuplicated());
        vo.setMessage(generated.isDuplicated()
                ? "变式已经生成过了"
                : "已经按母题机制生成了三层变式");
        return vo;
    }

    // ------------------------------------------------------------------ 内部

    /**
     * 找这道错题对应的母题：先看内容本身挂的母题，再退到「考点挂的母题」。
     */
    private String resolveMotherCode(StudyErrorPool error) {
        if (error.getContentId() != null && !error.getContentId().trim().isEmpty()) {
            String mother = questionMapper.selectMotherCodeByQuestionCode(error.getContentId().trim());
            if (mother != null && !mother.trim().isEmpty()) {
                return mother.trim();
            }
        }
        if (error.getKnowledgePointCode() != null && !error.getKnowledgePointCode().trim().isEmpty()) {
            String mother = questionMapper.selectMotherCodeByPointCode(error.getKnowledgePointCode().trim());
            if (mother != null && !mother.trim().isEmpty()) {
                return mother.trim();
            }
        }
        return null;
    }

    /**
     * 母题域的变式 → 错题域的变式（只取题干与层级，不带答案）。
     */
    private List<ErrorVariantVO> toErrorVariants(VariantGenerateResultVO generated) {
        List<ErrorVariantVO> list = new ArrayList<>();
        if (generated == null || generated.getVariants() == null) {
            return list;
        }
        for (VariantVO variant : generated.getVariants()) {
            if (variant == null) {
                continue;
            }
            list.add(new ErrorVariantVO()
                    .setVariantCode(variant.getVariantCode())
                    .setQuestionCode(variant.getVariantCode())
                    .setMotherCode(variant.getMotherCode())
                    .setLayer(variant.getLayer())
                    .setLayerName(variant.getLayerName())
                    .setStem(variant.getStem())
                    .setSource("VARIANT"));
        }
        return list;
    }
}
