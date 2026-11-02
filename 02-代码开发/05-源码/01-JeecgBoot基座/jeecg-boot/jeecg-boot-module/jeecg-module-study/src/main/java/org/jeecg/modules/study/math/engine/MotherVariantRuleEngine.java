package org.jeecg.modules.study.math.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.study.math.constant.MathConstants;
import org.jeecg.modules.study.math.entity.StudyMotherQuestion;
import org.jeecg.modules.study.math.enums.VariantLayer;
import org.jeecg.modules.study.math.vo.ConditionTypeVO;
import org.jeecg.modules.study.math.vo.GeneratedVariantVO;
import org.jeecg.modules.study.math.vo.MotherStepVO;

/**
 * 三层变式规则引擎（F-MTH-02 / TC-04-03 / TC-04-04）。
 *
 * <p><b>答案一律由确定性规则算出</b>：母题第 4 段的算式 + 替换后的参数，
 * 没有任何 AI 客户端参与（R-02）。同一个母题必然算出同样三条变式、同样三个答案。</p>
 *
 * <p>三层的改写规则：</p>
 * <ul>
 *   <li><b>第 1 层 换数字</b>：已知条件的数值整体替换，表层说法不变；</li>
 *   <li><b>第 2 层 换情境</b>：已知条件换成另一种说法（{@code variants}）并替换数值；</li>
 *   <li><b>第 3 层 逆向</b>：已知结果求条件 —— 题干给结果，答案要反解出某个已知条件；
 *       若关系式对任何条件都不满足仿射，退化为「求第一步的中间结果」。</li>
 * </ul>
 *
 * @author jeecg-study
 */
@Component
@RequiredArgsConstructor
public class MotherVariantRuleEngine {

    /** 答案保留位数 */
    private static final int ANSWER_SCALE = 6;

    /** 数值型 JSON 解析器 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 生成三层变式。
     *
     * @param mother 母题
     * @return 三条变式草稿（顺序：换数字 → 换情境 → 逆向）
     */
    public List<GeneratedVariantVO> generate(StudyMotherQuestion mother) {
        List<GeneratedVariantVO> list = new ArrayList<>();
        for (VariantLayer layer : VariantLayer.ordered()) {
            list.add(generateLayer(mother, layer.getLayer()));
        }
        return list;
    }

    /**
     * 生成某一层的变式。
     *
     * @param mother 母题
     * @param layer  层级 1/2/3
     * @return 变式草稿
     */
    public GeneratedVariantVO generateLayer(StudyMotherQuestion mother, int layer) {
        if (mother == null) {
            throw new JeecgBootException("母题不能为空", 400);
        }
        List<ConditionTypeVO> conditions = parseConditions(mother.getConditionTypes());
        List<MotherStepVO> steps = parseSteps(mother.getSteps());

        // 1. 可计算的已知条件（带 key 且带数值）
        List<ConditionTypeVO> numeric = new ArrayList<>();
        for (ConditionTypeVO condition : conditions) {
            if (condition.getKey() != null && !condition.getKey().trim().isEmpty()
                    && condition.getValue() != null) {
                numeric.add(condition);
            }
        }
        if (numeric.isEmpty()) {
            throw new JeecgBootException("母题第2段「已知条件类型」缺少可计算的数值，无法生成变式", 400);
        }

        // 2. 关系算式：优先用最后一步，退化为第一步
        MotherStepVO relation = lastStepWithFormula(steps);
        if (relation == null) {
            relation = firstStepWithFormula(steps);
        }
        if (relation == null) {
            throw new JeecgBootException("母题第4段「解题步骤链」缺少算式，无法生成变式", 400);
        }

        // 3. 参数替换（确定性：种子来自母题业务ID + 参数名 + 层级）
        Map<String, BigDecimal> params = new LinkedHashMap<>();
        Map<String, String> surfaces = new LinkedHashMap<>();
        for (ConditionTypeVO condition : numeric) {
            String key = condition.getKey().trim();
            params.put(key, condition.getValue().add(delta(mother, key, layer)));
            surfaces.put(key, surfaceLabel(mother, condition, layer));
        }

        // 4. 按算式求值
        BigDecimal result;
        try {
            result = ExpressionEngine.evaluate(relation.getFormula(), params);
        } catch (RuntimeException e) {
            throw new JeecgBootException("母题第4段的算式无法计算：" + e.getMessage(), 400);
        }

        // 5. 分层出题
        String answer;
        String targetLabel = null;
        if (layer == VariantLayer.REVERSE.getLayer()) {
            ConditionTypeVO target = reverseTarget(relation.getFormula(), numeric, params);
            if (target != null) {
                String key = target.getKey().trim();
                answer = format(params.get(key));
                targetLabel = target.getLabel() == null || target.getLabel().trim().isEmpty()
                        ? key : target.getLabel();
            } else {
                // 兜底：关系式无法反解时，改为求第一步的中间结果
                MotherStepVO first = firstStepWithFormula(steps);
                BigDecimal firstValue = result;
                if (first != null) {
                    try {
                        firstValue = ExpressionEngine.evaluate(first.getFormula(), params);
                    } catch (RuntimeException ignored) {
                        firstValue = result;
                    }
                }
                answer = format(firstValue);
                targetLabel = "第一步的中间结果";
            }
        } else {
            answer = format(result);
        }

        // 6. 组装
        String resultLabel = relation.getTitle() == null || relation.getTitle().trim().isEmpty()
                ? "结果" : relation.getTitle().trim();

        GeneratedVariantVO vo = new GeneratedVariantVO();
        vo.setLayer(layer);
        vo.setLayerName(VariantLayer.of(layer) == null ? "" : VariantLayer.of(layer).getLayerName());
        vo.setStem(buildStem(mother, layer, numeric, params, surfaces, resultLabel, result, targetLabel));
        vo.setAnswer(answer);
        vo.setAnswerTolerance(MathConstants.DEFAULT_TOLERANCE);
        vo.setGeneratedBy(MathConstants.GENERATED_BY_RULE);

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("layer", layer);
        snapshot.put("relation", relation.getFormula());
        snapshot.put("params", params);
        snapshot.put("surfaces", surfaces);
        snapshot.put("resultLabel", resultLabel);
        snapshot.put("targetLabel", targetLabel);
        vo.setParamsSnapshot(snapshot);
        return vo;
    }

    // ------------------------------------------------------------------ 内部：题干

    private String buildStem(StudyMotherQuestion mother, int layer, List<ConditionTypeVO> conditions,
                             Map<String, BigDecimal> params, Map<String, String> surfaces,
                             String resultLabel, BigDecimal result, String targetLabel) {
        StringBuilder sb = new StringBuilder();
        String title = mother.getTitle() == null ? "" : mother.getTitle();
        sb.append(title).append("（").append(VariantLayer.of(layer).getLayerName()).append("）：");
        List<String> parts = new ArrayList<>();
        for (ConditionTypeVO condition : conditions) {
            String key = condition.getKey().trim();
            BigDecimal value = params.get(key);
            if (value == null) {
                continue;
            }
            String surface = surfaces.get(key);
            parts.add(surface + " " + format(value));
        }
        sb.append(String.join("，", parts)).append("。");
        if (layer == VariantLayer.REVERSE.getLayer()) {
            sb.append("已知结果是 ").append(format(result)).append("，").append(targetLabel).append("是多少？");
        } else {
            sb.append(resultLabel).append("是多少？");
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ 内部：参数与表层

    /**
     * 数值增量：种子来自母题业务ID + 参数名 + 层级，保证可复现。
     */
    private BigDecimal delta(StudyMotherQuestion mother, String key, int layer) {
        String salt = layer == VariantLayer.NUMBER.getLayer() ? "N1"
                : (layer == VariantLayer.CONTEXT.getLayer() ? "C2" : "R3");
        int seed = (mother.getMotherCode() + "|" + key + "|" + salt).hashCode() & 0x7fffffff;
        int base = layer == VariantLayer.NUMBER.getLayer() ? 3
                : (layer == VariantLayer.CONTEXT.getLayer() ? 21 : 5);
        int range = layer == VariantLayer.NUMBER.getLayer() ? 17
                : (layer == VariantLayer.CONTEXT.getLayer() ? 23 : 11);
        return BigDecimal.valueOf(base + (seed % range));
    }

    /**
     * 表层说法：第 2 层换情境时换成另一种说法，其余层保持原说法。
     */
    private String surfaceLabel(StudyMotherQuestion mother, ConditionTypeVO condition, int layer) {
        String label = condition.getLabel() == null || condition.getLabel().trim().isEmpty()
                ? condition.getKey() : condition.getLabel().trim();
        if (layer != VariantLayer.CONTEXT.getLayer()
                || condition.getVariants() == null || condition.getVariants().isEmpty()) {
            return label;
        }
        List<String> variants = condition.getVariants();
        int seed = (mother.getMotherCode() + "|SURF|" + condition.getKey()).hashCode() & 0x7fffffff;
        int index = seed % variants.size();
        String candidate = variants.get(index);
        if (candidate == null || candidate.trim().isEmpty() || candidate.trim().equals(label)) {
            index = (index + 1) % variants.size();
            candidate = variants.get(index);
        }
        if (candidate == null || candidate.trim().isEmpty()) {
            return label;
        }
        return candidate.trim();
    }

    /**
     * 逆向层的目标条件：第一个「关系式对它仿射」的条件。
     *
     * @return 目标条件；没有则返回 null
     */
    private ConditionTypeVO reverseTarget(String formula, List<ConditionTypeVO> numeric,
                                          Map<String, BigDecimal> params) {
        for (ConditionTypeVO condition : numeric) {
            String key = condition.getKey().trim();
            AffineForm form;
            try {
                form = ExpressionEngine.affine(formula, key, params);
            } catch (RuntimeException e) {
                continue;
            }
            if (form != null && !form.isConstant()) {
                return condition;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ 内部：解析

    private List<ConditionTypeVO> parseConditions(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ConditionTypeVO>>() {
            });
        } catch (Exception e) {
            throw new JeecgBootException("母题第2段「已知条件类型」格式不合法", 400);
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
            throw new JeecgBootException("母题第4段「解题步骤链」格式不合法", 400);
        }
    }

    private MotherStepVO lastStepWithFormula(List<MotherStepVO> steps) {
        for (int i = steps.size() - 1; i >= 0; i--) {
            MotherStepVO step = steps.get(i);
            if (step.getFormula() != null && !step.getFormula().trim().isEmpty()) {
                return step;
            }
        }
        return null;
    }

    private MotherStepVO firstStepWithFormula(List<MotherStepVO> steps) {
        for (MotherStepVO step : steps) {
            if (step.getFormula() != null && !step.getFormula().trim().isEmpty()) {
                return step;
            }
        }
        return null;
    }

    /**
     * 数值格式化：去掉多余的零，保留最多 6 位小数。
     */
    private String format(BigDecimal value) {
        if (value == null) {
            return "";
        }
        return value.setScale(ANSWER_SCALE, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
