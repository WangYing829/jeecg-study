package org.jeecg.modules.study.grammar.engine.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import org.jeecg.modules.study.grammar.constant.GrammarSource;
import org.jeecg.modules.study.grammar.constant.GrammarTransformKind;
import org.jeecg.modules.study.grammar.entity.StudyGrammarPoint;
import org.jeecg.modules.study.grammar.engine.GrammarTransformEngine;
import org.jeecg.modules.study.grammar.mapper.StudyGrammarPointMapper;
import org.jeecg.modules.study.grammar.vo.GrammarTransformResultVO;
import org.jeecg.modules.study.grammar.vo.GrammarTransformVO;

/**
 * 句子变形器实现。
 *
 * <p>产出全部来自<b>语料库</b>（{@code study_grammar_point.transform_variants}），
 * 不调用任何 AI 客户端（R-02）。语料未收录时返回空列表 + 提示，
 * <b>不猜、不编</b>。</p>
 *
 * @author jeecg-study
 */
@Service
@RequiredArgsConstructor
public class GrammarTransformEngineImpl implements GrammarTransformEngine {

    /** 语料里的一条中文意思及其五种表达 */
    @Data
    public static class LibraryEntry {
        /** 中文意思 */
        private String meaning;
        /** 语法点业务ID */
        private String grammarCode;
        /** 五种表达 */
        private List<LibraryVariant> variants;
    }

    /** 语料里的一种表达 */
    @Data
    public static class LibraryVariant {
        /** 形态标识 */
        private String type;
        /** 句子 */
        private String text;
        /** 使用场合 */
        private String usage;
    }

    private final StudyGrammarPointMapper grammarPointMapper;

    /** 语料解析器（忽略未知字段，语料可以随版本扩列） */
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Override
    public GrammarTransformResultVO transform(String zhMeaning, String grammarCode) {
        GrammarTransformResultVO vo = new GrammarTransformResultVO();
        vo.setMeaning(zhMeaning == null ? "" : zhMeaning.trim());
        vo.setVariants(new ArrayList<>());

        String meaning = normalize(vo.getMeaning());
        if (meaning.isEmpty()) {
            return degrade(vo, "先写下一个中文意思，我再给你五种说法。");
        }

        List<StudyGrammarPoint> rows = grammarPointMapper.selectTransformLibrary();
        if (rows == null) {
            rows = Collections.emptyList();
        }

        String code = grammarCode == null ? "" : grammarCode.trim();
        StudyGrammarPoint row = null;
        LibraryEntry entry = null;

        // 先在指定语法点里找
        if (!code.isEmpty()) {
            for (StudyGrammarPoint candidate : rows) {
                if (code.equals(candidate.getGrammarCode())) {
                    row = candidate;
                    break;
                }
            }
            if (row == null) {
                StudyGrammarPoint direct = grammarPointMapper.selectByCode(code);
                if (direct != null) {
                    row = direct;
                }
            }
            if (row != null) {
                entry = findEntry(row, meaning);
            }
        }

        // 再在全库里找
        if (entry == null) {
            for (StudyGrammarPoint candidate : rows) {
                LibraryEntry found = findEntry(candidate, meaning);
                if (found != null) {
                    row = candidate;
                    entry = found;
                    break;
                }
            }
        }

        if (entry == null) {
            return degrade(vo, "这句话还没有收进例句库，换个说法试试，或者先看看语法点讲解。");
        }

        vo.setVariants(buildVariants(entry, row));
        vo.setSource(GrammarSource.LIBRARY);
        vo.setExplainStatus("NORMAL");
        vo.setGrammarCode(entry.getGrammarCode() == null || entry.getGrammarCode().trim().isEmpty()
                ? (row == null ? null : row.getGrammarCode())
                : entry.getGrammarCode().trim());
        return vo;
    }

    // ------------------------------------------------------------------ 内部

    /**
     * 组装五种形态。缺哪种形态就用语法点的例句 / 反例兜底，
     * 保证输出<b>恒定是五态</b>（TC-05-01）。
     */
    private List<GrammarTransformVO> buildVariants(LibraryEntry entry, StudyGrammarPoint row) {
        Map<String, LibraryVariant> byType = new HashMap<>();
        if (entry.getVariants() != null) {
            for (LibraryVariant variant : entry.getVariants()) {
                if (variant != null && variant.getType() != null) {
                    byType.put(variant.getType().trim().toUpperCase(Locale.ROOT), variant);
                }
            }
        }
        List<GrammarTransformVO> variants = new ArrayList<>();
        for (String kind : GrammarTransformKind.ALL) {
            LibraryVariant variant = byType.get(kind);
            String text = variant == null ? null : trim(variant.getText());
            if (text == null || text.isEmpty()) {
                text = fallbackText(kind, row);
            }
            String usage = variant == null ? null : trim(variant.getUsage());
            if (usage == null || usage.isEmpty()) {
                usage = GrammarTransformKind.usageOf(kind);
            }
            boolean wrong = GrammarTransformKind.WRONG.equals(kind);
            if (wrong && !text.startsWith(GrammarTransformKind.WRONG_MARK)) {
                text = GrammarTransformKind.WRONG_MARK + text;
            }
            GrammarTransformVO item = new GrammarTransformVO();
            item.setKind(kind);
            item.setName(GrammarTransformKind.nameOf(kind));
            item.setText(text);
            item.setUsage(usage);
            item.setWrong(wrong);
            variants.add(item);
        }
        return variants;
    }

    private String fallbackText(String kind, StudyGrammarPoint row) {
        if (row == null) {
            return "";
        }
        if (GrammarTransformKind.WRONG.equals(kind)) {
            return row.getCounterExample() == null ? "" : row.getCounterExample();
        }
        return row.getExample() == null ? "" : row.getExample();
    }

    private GrammarTransformResultVO degrade(GrammarTransformResultVO vo, String hint) {
        vo.setSource(GrammarSource.UNAVAILABLE);
        vo.setExplainStatus("UNAVAILABLE");
        vo.setHint(hint);
        vo.setVariants(new ArrayList<>());
        return vo;
    }

    /**
     * 在一个语法点的语料里找中文意思匹配的那条。
     */
    private LibraryEntry findEntry(StudyGrammarPoint row, String meaning) {
        List<LibraryEntry> entries = parse(row == null ? null : row.getTransformVariants());
        if (entries == null) {
            return null;
        }
        for (LibraryEntry entry : entries) {
            if (entry == null || entry.getMeaning() == null) {
                continue;
            }
            if (normalize(entry.getMeaning()).equals(meaning)) {
                return entry;
            }
        }
        return null;
    }

    /**
     * 解析语料 JSON。数组与单对象两种写法都兼容。
     */
    private List<LibraryEntry> parse(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            List<LibraryEntry> entries = objectMapper.readValue(json, new TypeReference<List<LibraryEntry>>() {
            });
            return entries;
        } catch (Exception ignored) {
            // 不是数组就按单对象再试一次
        }
        try {
            LibraryEntry single = objectMapper.readValue(json, LibraryEntry.class);
            return single == null ? null : Collections.singletonList(single);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 中文意思归一化：去空白、去标点、转小写。
     */
    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("[\\s\\u3000]", "")
                .replaceAll("[，。？！、；：,.?!;:\"'()（）《》「」]", "")
                .toLowerCase(Locale.ROOT);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
