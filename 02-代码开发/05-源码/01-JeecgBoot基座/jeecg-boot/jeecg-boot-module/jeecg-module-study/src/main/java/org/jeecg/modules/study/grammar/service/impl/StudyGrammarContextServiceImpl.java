package org.jeecg.modules.study.grammar.service.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.study.grammar.constant.GrammarContextRelation;
import org.jeecg.modules.study.grammar.constant.GrammarContextType;
import org.jeecg.modules.study.grammar.mapper.StudySentenceContextMapper;
import org.jeecg.modules.study.grammar.mapper.StudyWordContextMapper;
import org.jeecg.modules.study.grammar.service.StudyGrammarContextService;
import org.jeecg.modules.study.grammar.vo.GrammarContextItemVO;
import org.jeecg.modules.study.grammar.vo.GrammarContextResultVO;
import org.jeecg.modules.study.grammar.vo.GrammarContextRow;
import org.jeecg.modules.study.grammar.vo.GrammarWordHeaderRow;

/**
 * 例句语境库服务实现（F-GRM-05）。
 *
 * <p><b>数据范围</b>：语境库是<b>内容表</b>（全局共享，不含 {@code child_id}），
 * 因此这个接口不按孩子隔离；但例句<b>正文</b>来自共享语料，
 * 不存在「某个孩子的私有例句」这回事。</p>
 *
 * <p><b>跨域只读</b>：背单词域的 {@code study_word} 只读不改，
 * 词条的拼写、释义与它自己那条例句都从那里取（R-04 / ADR-024 跨域只读约定）。</p>
 *
 * @author jeecg-study
 */
@Service
@RequiredArgsConstructor
public class StudyGrammarContextServiceImpl implements StudyGrammarContextService {

    /** 默认返回条数 */
    private static final int DEFAULT_LIMIT = 20;

    /** 单次返回条数上限 */
    private static final int MAX_LIMIT = 50;

    private final StudySentenceContextMapper sentenceMapper;

    private final StudyWordContextMapper wordContextMapper;

    @Override
    public GrammarContextResultVO context(String wordCode, String grammarCode, String contextType, Integer limit) {
        String word = trim(wordCode);
        String grammar = trim(grammarCode);
        if (word.isEmpty() && grammar.isEmpty()) {
            throw new JeecgBootException("请给出要查的词条ID或语法点ID", 400);
        }
        String type = trim(contextType);
        if (!type.isEmpty() && !GrammarContextType.isValid(type)) {
            throw new JeecgBootException("语境类型只能是课文、真题、原声素材", 400);
        }
        int max = limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);

        List<GrammarContextRow> rows = word.isEmpty()
                ? sentenceMapper.selectByGrammarCode(grammar, type)
                : sentenceMapper.selectByWordCode(word, type);

        GrammarContextResultVO vo = new GrammarContextResultVO();
        vo.setWordCode(word.isEmpty() ? null : word);
        vo.setGrammarCode(word.isEmpty() ? grammar : null);

        if (!word.isEmpty()) {
            GrammarWordHeaderRow header = sentenceMapper.selectWordHeader(word);
            if (header != null) {
                vo.setSpelling(header.getSpelling());
                vo.setMeaning(header.getMeaning());
                vo.setWordExampleSentence(header.getContextSentence());
            }
            // 总数取关联表的条数：不受 limit 影响，才能与背单词域对上
            vo.setTotal(wordContextMapper.countByWordCode(word));
        }

        List<GrammarContextItemVO> items = new ArrayList<>();
        if (rows != null) {
            for (GrammarContextRow row : rows) {
                if (row == null || items.size() >= max) {
                    continue;
                }
                items.add(toItem(row));
            }
        }
        vo.setItems(items);
        if (vo.getTotal() == null) {
            vo.setTotal(items.size());
        }
        return vo;
    }

    // ------------------------------------------------------------------ 内部

    private GrammarContextItemVO toItem(GrammarContextRow row) {
        GrammarContextItemVO vo = new GrammarContextItemVO();
        vo.setSentenceCode(row.getSentenceCode());
        vo.setSentence(row.getSentence());
        vo.setZhMeaning(row.getZhMeaning());
        vo.setContextType(row.getContextType());
        vo.setContextTypeName(GrammarContextType.nameOf(row.getContextType()));
        vo.setRelationType(row.getRelationType());
        vo.setUsageNote(row.getUsageNote());
        vo.setSourceRef(row.getSourceRef());
        vo.setGrammarCode(row.getGrammarCode());
        return vo;
    }

    /**
     * 主例句是否与背单词域那一条是同一句（同一批例句的自证）。
     *
     * @param row 语境库行
     * @param wordExampleSentence 背单词域的例句正文
     * @return 是同一句返回 true
     */
    public static boolean isMainSentence(GrammarContextRow row, String wordExampleSentence) {
        if (row == null || wordExampleSentence == null || wordExampleSentence.trim().isEmpty()) {
            return false;
        }
        if (!GrammarContextRelation.MAIN.equals(row.getRelationType())) {
            return false;
        }
        return wordExampleSentence.trim().equals(row.getSentence() == null ? "" : row.getSentence().trim());
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

}
