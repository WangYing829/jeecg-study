package org.jeecg.modules.study.grammar.engine.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;

import org.springframework.stereotype.Service;

import org.jeecg.modules.study.grammar.engine.GrammarErrorDetector;
import org.jeecg.modules.study.grammar.engine.GrammarErrorRule;
import org.jeecg.modules.study.grammar.engine.GrammarErrorRuleCatalog;
import org.jeecg.modules.study.grammar.vo.GrammarErrorVO;

/**
 * 语法错误检测实现（确定性规则）。
 *
 * <p>检测完全由规则表驱动，<b>不依赖任何 AI 客户端</b>（R-02）；
 * 讲解内容由规则自带，并拼上孩子刚写的那一段，
 * 保证「讲解从孩子的真实错误出发」（TC-05-04）。</p>
 *
 * @author jeecg-study
 */
@Service
public class GrammarErrorDetectorImpl implements GrammarErrorDetector {

    /** 单条规则最多报几处，避免同一句里重复刷屏 */
    private static final int MAX_HITS_PER_RULE = 5;

    @Override
    public List<GrammarErrorVO> detect(String sentence) {
        List<GrammarErrorVO> result = new ArrayList<>();
        if (sentence == null || sentence.trim().isEmpty()) {
            return result;
        }
        for (GrammarErrorRule rule : GrammarErrorRuleCatalog.all()) {
            Matcher matcher = rule.getPattern().matcher(sentence);
            int hit = 0;
            while (matcher.find() && hit < MAX_HITS_PER_RULE) {
                result.add(toError(rule, sentence, matcher, hit));
                hit++;
            }
        }
        result.sort(Comparator.comparingInt(GrammarErrorVO::getIndex));
        return result;
    }

    // ------------------------------------------------------------------ 内部

    private GrammarErrorVO toError(GrammarErrorRule rule, String sentence, Matcher matcher, int hitIndex) {
        String fragment = matcher.group();

        GrammarErrorVO vo = new GrammarErrorVO();
        vo.setErrorId(rule.getRuleId() + "@" + shortHash(sentence) + "#" + hitIndex);
        vo.setIndex(matcher.start());
        vo.setLength(matcher.end() - matcher.start());
        vo.setFragment(fragment);
        vo.setGrammarCode(rule.getGrammarCode());
        // 讲解从孩子刚写的这一段出发
        vo.setRule(rule.getExplanation() + "（你写的是「" + fragment + "」）");
        vo.setExample(rule.getExample());
        vo.setCounterExample(rule.getCounterExample());
        vo.setFix(fixSentence(rule, sentence, matcher));
        return vo;
    }

    /**
     * 用规则里的修正器算出改正后的整句。
     */
    private String fixSentence(GrammarErrorRule rule, String sentence, Matcher matcher) {
        String fixedMatched = rule.getFixer().apply(matcher.group(), sentence);
        if (fixedMatched == null) {
            return null;
        }
        String fixed = sentence.substring(0, matcher.start()) + fixedMatched + sentence.substring(matcher.end());
        return fixed.replaceAll("\\s{2,}", " ").trim();
    }

    /**
     * 句子的短摘要，用作错误标记的一部分，保证同一句话的错误标记稳定可复现。
     */
    private String shortHash(String text) {
        return Integer.toHexString(text.hashCode() & 0x7fffffff);
    }
}
