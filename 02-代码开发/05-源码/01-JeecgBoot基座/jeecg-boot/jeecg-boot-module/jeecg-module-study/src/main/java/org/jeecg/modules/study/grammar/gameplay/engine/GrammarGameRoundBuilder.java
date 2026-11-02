package org.jeecg.modules.study.grammar.gameplay.engine;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

import org.jeecg.modules.study.grammar.engine.GrammarErrorDetector;
import org.jeecg.modules.study.grammar.gameplay.entity.StudyGrammarGameQuestion;
import org.jeecg.modules.study.grammar.mapper.StudySentenceContextMapper;
import org.jeecg.modules.study.grammar.vo.GrammarContextRow;
import org.jeecg.modules.study.grammar.vo.GrammarErrorVO;

/**
 * 玩法回合组装器（F-GRM-04）。
 *
 * <p><b>唯一真源在服务端</b>：取题与判定都从这里重新组装同一回合，
 * 所以「哪一句是错的」不靠前端传，也不靠客户端记住。</p>
 *
 * <p><b>每回合都要过规则自检</b>（确定性，零 AI 依赖）：
 * <ul>
 *   <li>句子侦探：错句必须被规则命中，正例句必须一条都不命中；</li>
 *   <li>变形闯关：目标表达本身必须是规则判定为正确的句子。</li>
 * </ul>
 * 自检不通过的题直接跳过（题库内容变动时不会给出无解的关卡）。</p>
 *
 * @author jeecg-study
 */
@Service
@RequiredArgsConstructor
public class GrammarGameRoundBuilder {

    /** 一回合里的句子数 */
    private static final int ROUND_SIZE = 4;

    private final StudySentenceContextMapper sentenceMapper;

    private final GrammarErrorDetector errorDetector;

    /** JSON 解析器（忽略未知字段，题库可以随版本扩列） */
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * 组装一个句子侦探回合。
     *
     * @param question 题库中的一道题
     * @return 回合；题库内容有问题时返回 null（调用方跳过这一题）
     */
    public GrammarDetectiveRoundModel buildDetective(StudyGrammarGameQuestion question) {
        if (question == null) {
            return null;
        }
        String wrongSentence = trim(question.getWrongSentence());
        int wrongPosition = question.getWrongPosition() == null ? 0 : question.getWrongPosition();
        if (wrongSentence.isEmpty() || wrongPosition < 1 || wrongPosition > ROUND_SIZE) {
            return null;
        }

        List<String> codes = parseCodes(question.getCleanSentenceCodes());
        if (codes == null || codes.size() != ROUND_SIZE - 1) {
            return null;
        }
        List<GrammarContextRow> rows = sentenceMapper.selectByCodes(codes);
        if (rows == null || rows.size() != codes.size()) {
            return null;
        }

        // 正例句必须一条都不被规则命中
        for (GrammarContextRow row : rows) {
            String text = trim(row.getSentence());
            if (text.isEmpty()) {
                return null;
            }
            if (!errorDetector.detect(text).isEmpty()) {
                return null;
            }
        }

        // 错句必须被规则命中，否则这一关没有确定性答案
        List<GrammarErrorVO> wrongErrors = errorDetector.detect(wrongSentence);
        if (wrongErrors.isEmpty()) {
            return null;
        }

        GrammarDetectiveRoundModel model = new GrammarDetectiveRoundModel();
        model.setQuestionCode(question.getQuestionCode());
        model.setGrammarCode(question.getGrammarCode());
        model.setWrongPosition(wrongPosition);
        model.setWrongSentence(wrongSentence);
        model.setWrongErrors(wrongErrors);

        List<GrammarDetectiveRoundModel.DetectiveSentence> sentences = new ArrayList<>();
        int cleanIndex = 0;
        for (int position = 1; position <= ROUND_SIZE; position++) {
            GrammarDetectiveRoundModel.DetectiveSentence sentence = new GrammarDetectiveRoundModel.DetectiveSentence();
            sentence.setPosition(position);
            if (position == wrongPosition) {
                sentence.setText(wrongSentence);
            } else {
                GrammarContextRow row = rows.get(cleanIndex);
                cleanIndex++;
                sentence.setSentenceCode(row.getSentenceCode());
                sentence.setText(trim(row.getSentence()));
            }
            sentences.add(sentence);
        }
        model.setSentences(sentences);
        return model;
    }

    /**
     * 组装一个语法变形闯关回合。
     *
     * @param question 题库中的一道题
     * @return 回合；题库内容有问题时返回 null
     */
    public GrammarTransformRoundModel buildTransform(StudyGrammarGameQuestion question) {
        if (question == null) {
            return null;
        }
        String code = trim(question.getSentenceCode());
        if (code.isEmpty()) {
            return null;
        }
        List<GrammarContextRow> rows = sentenceMapper.selectByCodes(java.util.Collections.singletonList(code));
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        GrammarContextRow row = rows.get(0);
        String meaning = trim(row.getZhMeaning());
        String target = trim(row.getSentence());
        if (meaning.isEmpty() || target.isEmpty()) {
            return null;
        }
        // 目标表达本身必须是正确的句子
        if (!errorDetector.detect(target).isEmpty()) {
            return null;
        }

        GrammarTransformRoundModel model = new GrammarTransformRoundModel();
        model.setQuestionCode(question.getQuestionCode());
        model.setGrammarCode(question.getGrammarCode());
        model.setMeaning(meaning);
        model.setTargetText(target);
        model.setTargetSentenceCode(row.getSentenceCode());
        return model;
    }

    // ------------------------------------------------------------------ 内部

    /**
     * 解析题库里的正例句业务ID 列表。
     *
     * @param json JSON 数组文本
     * @return 业务ID 列表；解析不出来返回 null
     */
    private List<String> parseCodes(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            List<String> list = objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
            if (list == null || list.isEmpty()) {
                return null;
            }
            List<String> out = new ArrayList<>();
            for (String code : list) {
                String value = trim(code);
                if (!value.isEmpty()) {
                    out.add(value);
                }
            }
            return out;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

}
