package org.jeecg.modules.study.writing.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.study.common.constant.StudyErrorCode;
import org.jeecg.modules.study.common.enums.JudgeSource;
import org.jeecg.modules.study.common.exception.StudyForbiddenException;
import org.jeecg.modules.study.error.constant.ErrorCause;
import org.jeecg.modules.study.error.service.StudyErrorPoolService;
import org.jeecg.modules.study.error.vo.StudyErrorReportCommand;
import org.jeecg.modules.study.scope.service.ChildScopeService;
import org.jeecg.modules.study.writing.constant.WritingConstants;
import org.jeecg.modules.study.writing.constant.WritingDimension;
import org.jeecg.modules.study.writing.entity.StudyWriting;
import org.jeecg.modules.study.writing.enums.WritingLayer;
import org.jeecg.modules.study.writing.mapper.StudyWritingFeedbackMapper;
import org.jeecg.modules.study.writing.mapper.StudyWritingMapper;
import org.jeecg.modules.study.writing.service.StudyWritingService;
import org.jeecg.modules.study.writing.vo.WritingDiffVO;
import org.jeecg.modules.study.writing.vo.WritingDissectionLayerVO;
import org.jeecg.modules.study.writing.vo.WritingDissectionListVO;
import org.jeecg.modules.study.writing.vo.WritingDissectionVO;
import org.jeecg.modules.study.writing.vo.WritingDraftRequest;
import org.jeecg.modules.study.writing.vo.WritingDraftResultVO;
import org.jeecg.modules.study.writing.vo.WritingOutlineBody;
import org.jeecg.modules.study.writing.vo.WritingOutlineFeedbackVO;
import org.jeecg.modules.study.writing.vo.WritingOutlineRecord;
import org.jeecg.modules.study.writing.vo.WritingOutlineRequest;
import org.jeecg.modules.study.writing.vo.WritingOutlineResultVO;

/**
 * 语文写作域服务实现（V4）。
 *
 * <p><b>数据范围</b>：孩子端身份来自会话（{@code scopeService.ownChildId()}），
 * <b>不接受前端传来的 childId</b>；所有按孩子查的查询都带 {@code child_id} 条件。</p>
 *
 * <p><b>R-02</b>：写作域<b>没有 AI 客户端</b>，批改差异全部由确定性规则算出，
 * {@code source} 恒为 {@code rule}；病句入统一错题池，不自建错句表。</p>
 *
 * <p><b>R-04</b>：批改结果只有「维度 / 说明 / 建议」，没有分数、没有等级、
 * 没有横向比较；家长的反馈存在独立的层里，孩子的接口一律读不到。</p>
 *
 * @author jeecg-study
 */
@Service
@RequiredArgsConstructor
public class StudyWritingServiceImpl implements StudyWritingService {

    /** 连接词（判定衔接是否够用） */
    private static final List<String> CONNECTORS = Arrays.asList(
            "因为", "所以", "但是", "可是", "虽然", "尽管", "而且", "然后", "接着", "于是",
            "不过", "后来", "这时", "最终", "首先", "其次", "最后", "并且", "即使", "不但");

    /** 细节词（判定选材是否具体） */
    private static final List<String> DETAIL_WORDS = Arrays.asList(
            "清晨", "傍晚", "黄昏", "路灯", "台阶", "墙角", "课桌", "书包", "铅笔", "橡皮",
            "黑板", "操场", "铃声", "走廊", "窗台", "树影", "汗珠", "额头", "手心", "掌心",
            "指尖", "袖口", "瓷碗", "汽水", "冰棍", "门框", "砖缝", "蚂蚁", "蜻蜓", "蝉鸣",
            "月光", "雨伞", "围巾", "草稿", "稿纸", "墨水", "印章", "铃铛", "挎包", "水壶",
            "毛巾", "拖鞋", "沙发", "窗帘", "蚊香", "竹席", "搪瓷", "田径", "篮球", "跳绳",
            "毽子", "沙包", "跳棋", "弹珠", "陀螺", "风筝", "蝌蚪");

    /** 描写词（判定有没有手法） */
    private static final List<String> HAND_WORDS = Arrays.asList(
            "仿佛", "宛如", "犹如", "好像", "似乎", "金黄", "火红", "雪白", "碧绿", "漆黑",
            "昏黄", "暖烘烘", "凉飕飕", "亮晶晶", "静悄悄", "暖洋洋", "黑漆漆", "亮闪闪",
            "毛茸茸", "软绵绵", "沉甸甸", "冷冰冰");

    /** 句子切分符 */
    private static final String SENTENCE_SPLIT = "[。！？!?；;\\n]";

    /** 虚词与代词（判断结尾有没有回扣开头时排除） */
    private static final String FUNCTION_CHARS =
            "的了是在我你他她它们这那有和就都不也很到把被让给对从向与及或但因所以之其而且然后接着于是"
                    + "个们地得着过什么怎样时候样为以及于且若如若此呢吧啊呀吗嘛哦嗯个只又还再才更最很";

    private final StudyWritingMapper writingMapper;
    private final StudyWritingFeedbackMapper writingFeedbackMapper;
    private final StudyErrorPoolService errorPoolService;
    private final ChildScopeService scopeService;

    /** JSON 序列化（提纲落库快照） */
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ------------------------------------------------------------------ 拆解库

    @Override
    public WritingDissectionListVO listDissections(String genre, String stageCode) {
        List<StudyWriting> rows = writingMapper.selectLibrary(
                WritingLayer.libraryLayerCodes(), trim(genre), trim(stageCode));
        List<WritingDissectionVO> items = groupDissections(rows);
        WritingDissectionListVO vo = new WritingDissectionListVO();
        vo.setItems(items);
        vo.setTotal(items.size());
        return vo;
    }

    @Override
    public WritingDissectionVO getDissection(String code) {
        String target = trim(code);
        if (target.isEmpty()) {
            throw new JeecgBootException("拆解编码不能为空", 400);
        }
        StudyWriting anchor = writingMapper.selectLibraryByCode(target);
        String groupCode = anchor == null ? target : anchor.getReferenceCode();
        List<StudyWriting> rows = writingMapper.selectLibraryByGroup(groupCode);
        if (rows == null || rows.isEmpty()) {
            throw new JeecgBootException("拆解不存在：" + target, 400);
        }
        List<WritingDissectionVO> grouped = groupDissections(rows);
        if (grouped.isEmpty()) {
            throw new JeecgBootException("拆解不存在：" + target, 400);
        }
        return grouped.get(0);
    }

    // ------------------------------------------------------------------ 提纲

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WritingOutlineResultVO submitOutline(WritingOutlineRequest request) {
        String childId = currentChildId();
        if (request == null) {
            throw new JeecgBootException("参数不能为空", 400);
        }
        String writingCode = trim(request.getWritingCode());
        String topic = trim(request.getTopic());
        if (writingCode.isEmpty() || topic.isEmpty()) {
            throw new JeecgBootException("来源拆解与题目不能为空", 400);
        }
        StudyWriting anchor = writingMapper.selectLibraryByCode(writingCode);
        if (anchor == null) {
            throw new JeecgBootException("拆解不存在：" + writingCode, 400);
        }
        String referenceCode = trim(anchor.getReferenceCode());

        // 幂等：同一道题 + 同一个题目只落一行；已通过时原样重放上次的结论
        StudyWriting existed = writingFeedbackMapper.selectOutline(childId, referenceCode, topic);
        if (existed != null && existed.getStatus() != null && existed.getStatus() == 1) {
            return replayOutline(existed);
        }

        WritingOutlineRecord record = evaluateOutline(request.getOutline());
        String outlineCode = "WRT-OUTLINE-" + WritingConstants.digest12(
                childId + "|OUTLINE|" + referenceCode + "|" + topic);

        if (existed != null) {
            existed.setContent(writeJson(record));
            existed.setStatus(Boolean.TRUE.equals(record.getPassed()) ? 1 : 0);
            existed.setUpdateBy(scopeService.currentUserId());
            existed.setUpdateTime(new Date());
            writingFeedbackMapper.updateById(existed);
            return toOutlineResult(record);
        }

        StudyWriting row = new StudyWriting();
        row.setChildId(childId);
        row.setWritingCode(outlineCode);
        row.setLayer(WritingLayer.OUTLINE.getCode());
        row.setTitle(topic);
        row.setContent(writeJson(record));
        row.setSubjectCode(WritingConstants.SUBJECT_CODE);
        row.setGenre(trim(anchor.getGenre()));
        row.setStageCode(trim(anchor.getStageCode()));
        row.setKnowledgePointCode(trim(anchor.getKnowledgePointCode()));
        row.setReferenceCode(referenceCode);
        row.setCopyrightType(WritingConstants.COPYRIGHT_SELF);
        row.setStatus(Boolean.TRUE.equals(record.getPassed()) ? 1 : 0);
        row.setDelFlag(0);
        row.setCreateBy(scopeService.currentUserId());
        row.setCreateTime(new Date());
        try {
            writingFeedbackMapper.insert(row);
        } catch (DuplicateKeyException e) {
            // 并发重复提交：另一路已经把同一份提纲落下去了，按幂等重放
            StudyWriting raced = writingFeedbackMapper.selectOutline(childId, referenceCode, topic);
            if (raced != null) {
                return replayOutline(raced);
            }
            throw e;
        }
        return toOutlineResult(record);
    }

    // ------------------------------------------------------------------ 初稿

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WritingDraftResultVO submitDraft(WritingDraftRequest request) {
        String childId = currentChildId();
        if (request == null) {
            throw new JeecgBootException("参数不能为空", 400);
        }
        String writingCode = trim(request.getWritingCode());
        String topic = trim(request.getTopic());
        String content = request.getContent() == null ? "" : request.getContent().trim();
        if (writingCode.isEmpty() || topic.isEmpty()) {
            throw new JeecgBootException("来源拆解与题目不能为空", 400);
        }
        if (content.isEmpty()) {
            throw new JeecgBootException("作文内容不能为空", 400);
        }
        StudyWriting anchor = writingMapper.selectLibraryByCode(writingCode);
        if (anchor == null) {
            throw new JeecgBootException("拆解不存在：" + writingCode, 400);
        }
        String referenceCode = trim(anchor.getReferenceCode());

        // 三步训练流顺序强制：提纲没通过就不允许写初稿（4002），且不落库
        StudyWriting outline = writingFeedbackMapper.selectOutline(childId, referenceCode, topic);
        if (outline == null || outline.getStatus() == null || outline.getStatus() != 1) {
            throw new JeecgBootException(
                    "还没通过提纲这一步，先把开—中—末三段写完再写正文",
                    StudyErrorCode.PREREQUISITE_NOT_DONE);
        }

        List<WritingDiffVO> diffs = computeDiffs(content);
        String draftCode = "WRT-DRAFT-" + WritingConstants.digest12(
                childId + "|DRAFT|" + referenceCode + "|" + topic);

        // 幂等：同一道题 + 同一个题目只有一行初稿，重复提交只更新不新增
        StudyWriting existed = writingFeedbackMapper.selectDraftByCodeForChild(childId, draftCode);
        if (existed != null) {
            existed.setContent(content);
            existed.setUpdateBy(scopeService.currentUserId());
            existed.setUpdateTime(new Date());
            writingFeedbackMapper.updateById(existed);
        } else {
            StudyWriting row = new StudyWriting();
            row.setChildId(childId);
            row.setWritingCode(draftCode);
            row.setLayer(WritingLayer.DRAFT.getCode());
            row.setTitle(topic);
            row.setContent(content);
            row.setSubjectCode(WritingConstants.SUBJECT_CODE);
            row.setGenre(trim(anchor.getGenre()));
            row.setStageCode(trim(anchor.getStageCode()));
            row.setKnowledgePointCode(trim(anchor.getKnowledgePointCode()));
            row.setReferenceCode(referenceCode);
            row.setCopyrightType(WritingConstants.COPYRIGHT_SELF);
            row.setStatus(1);
            row.setDelFlag(0);
            row.setCreateBy(scopeService.currentUserId());
            row.setCreateTime(new Date());
            try {
                writingFeedbackMapper.insert(row);
            } catch (DuplicateKeyException e) {
                StudyWriting raced = writingFeedbackMapper.selectDraftByCodeForChild(childId, draftCode);
                if (raced == null) {
                    throw e;
                }
                raced.setContent(content);
                raced.setUpdateTime(new Date());
                writingFeedbackMapper.updateById(raced);
            }
        }

        // 病句进统一错题池（幂等：同一处病句只入一次）
        reportIllSentences(childId, draftCode, content, trim(anchor.getKnowledgePointCode()));

        WritingDraftResultVO vo = new WritingDraftResultVO();
        vo.setContentId(draftCode);
        vo.setDiffList(diffs);
        vo.setDiffCount(diffs.size());
        vo.setSource(WritingConstants.SOURCE_RULE);
        vo.setNote("对照同题材佳作给出差异，非评分");
        return vo;
    }

    // ------------------------------------------------------------------ 内部：拆解库组装

    /**
     * 按拆解集编码把六层行组装成拆解对象。
     */
    private List<WritingDissectionVO> groupDissections(List<StudyWriting> rows) {
        Map<String, List<StudyWriting>> groups = new LinkedHashMap<>();
        if (rows != null) {
            for (StudyWriting row : rows) {
                if (row == null) {
                    continue;
                }
                String groupCode = trim(row.getReferenceCode());
                if (groupCode.isEmpty()) {
                    groupCode = trim(row.getWritingCode());
                }
                groups.computeIfAbsent(groupCode, key -> new ArrayList<>()).add(row);
            }
        }
        List<WritingDissectionVO> items = new ArrayList<>();
        for (Map.Entry<String, List<StudyWriting>> entry : groups.entrySet()) {
            WritingDissectionVO vo = new WritingDissectionVO();
            vo.setDissectionCode(entry.getKey());
            vo.setTitle("");
            vo.setLayers(new ArrayList<>());
            List<StudyWriting> rowsOfGroup = new ArrayList<>(entry.getValue());
            // 六层按固定顺序呈现：题目钩子 → 开篇三句 → 选材角度 → 结构图 → 手法例句 → 结尾回扣
            rowsOfGroup.sort((left, right) -> Integer.compare(layerOrder(left), layerOrder(right)));
            for (StudyWriting row : rowsOfGroup) {
                WritingDissectionLayerVO layer = new WritingDissectionLayerVO();
                WritingLayer enumLayer = WritingLayer.of(row.getLayer());
                layer.setLayer(row.getLayer());
                layer.setLayerName(enumLayer == null ? row.getLayer() : enumLayer.getLabel());
                layer.setTitle(trim(row.getTitle()));
                layer.setContent(row.getContent());
                layer.setTeachingPoint(row.getTeachingPoint());
                layer.setPromptQuestion(row.getPromptQuestion());
                vo.getLayers().add(layer);
                if (vo.getTitle().isEmpty()) {
                    vo.setTitle(trim(row.getTitle()));
                }
                if (vo.getGenre() == null || vo.getGenre().isEmpty()) {
                    vo.setGenre(trim(row.getGenre()));
                }
                if (vo.getStageCode() == null || vo.getStageCode().isEmpty()) {
                    vo.setStageCode(trim(row.getStageCode()));
                }
            }
            items.add(vo);
        }
        return items;
    }

    /**
     * 层的呈现顺序：拆解库六层按枚举顺序，未知层排最后。
     */
    private int layerOrder(StudyWriting row) {
        WritingLayer layer = row == null ? null : WritingLayer.of(row.getLayer());
        return layer == null ? 99 : layer.getOrder();
    }

    // ------------------------------------------------------------------ 内部：提纲判定

    /**
     * 提纲通过判定（确定性规则）：开—中—末三段都写了，且正文至少两段。
     */
    private WritingOutlineRecord evaluateOutline(WritingOutlineBody outline) {
        WritingOutlineRecord record = new WritingOutlineRecord();
        List<WritingOutlineFeedbackVO> feedback = new ArrayList<>();
        boolean passed = true;

        String opening = outline == null ? "" : trim(outline.getOpening());
        String ending = outline == null ? "" : trim(outline.getEnding());
        List<String> body = outline == null ? null : outline.getBody();

        if (opening.isEmpty()) {
            passed = false;
            feedback.add(feedback("OPENING", "开头还空着，先用一句话把题目里最想让人记住的东西点出来。"));
        }
        if (body == null || body.size() < WritingConstants.MIN_OUTLINE_BODY) {
            passed = false;
            feedback.add(feedback("BODY", "正文至少写两段，先想清楚事情是怎么开始的、怎么变的。"));
        } else {
            int blank = 0;
            for (String part : body) {
                if (trim(part).isEmpty()) {
                    blank++;
                }
            }
            if (blank > 0) {
                passed = false;
                feedback.add(feedback("BODY", "正文里有 " + blank + " 段还是空的，把每段要写的那句话先补上。"));
            }
        }
        if (ending.isEmpty()) {
            passed = false;
            feedback.add(feedback("ENDING", "结尾还空着，想好最后一句要回到开头的哪个画面。"));
        }
        if (passed) {
            feedback.add(feedback("STRUCTURE", "提纲有明确的开—中—末三段，结构可用。"));
        }

        record.setPassed(passed);
        record.setNextStep(passed ? WritingConstants.NEXT_STEP_DRAFT : WritingConstants.NEXT_STEP_OUTLINE);
        record.setOpening(opening);
        record.setEnding(ending);
        record.setBody(body == null ? new ArrayList<>() : new ArrayList<>(body));
        record.setFeedback(feedback);
        return record;
    }

    private WritingOutlineFeedbackVO feedback(String type, String message) {
        WritingOutlineFeedbackVO vo = new WritingOutlineFeedbackVO();
        vo.setType(type);
        vo.setMessage(message);
        return vo;
    }

    /**
     * 幂等重放：把上次落库的提纲快照读回来，原样返回。
     */
    private WritingOutlineResultVO replayOutline(StudyWriting row) {
        WritingOutlineRecord record = readJson(row.getContent());
        if (record == null) {
            throw new JeecgBootException("提纲记录已损坏，请重新提交一次", 500);
        }
        return toOutlineResult(record);
    }

    private WritingOutlineResultVO toOutlineResult(WritingOutlineRecord record) {
        WritingOutlineResultVO vo = new WritingOutlineResultVO();
        vo.setPassed(Boolean.TRUE.equals(record.getPassed()));
        vo.setFeedback(record.getFeedback() == null ? new ArrayList<>() : record.getFeedback());
        vo.setNextStep(record.getNextStep());
        return vo;
    }

    private String writeJson(WritingOutlineRecord record) {
        try {
            return objectMapper.writeValueAsString(record);
        } catch (Exception e) {
            throw new JeecgBootException("提纲保存失败", 500);
        }
    }

    private WritingOutlineRecord readJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, WritingOutlineRecord.class);
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ 内部：对照式批改

    /**
     * 对照式批改：规则命中后按固定顺序排序，再截断到硬上限 3 条。
     */
    private List<WritingDiffVO> computeDiffs(String content) {
        Map<String, WritingDiffVO> candidates = new LinkedHashMap<>();
        String text = content == null ? "" : content;
        List<String> sentences = splitSentences(text);
        int length = effectiveLength(text);

        // 结构：结尾有没有回扣开头
        if (!echoOpening(sentences)) {
            candidates.put(WritingDimension.STRUCTURE, diff(WritingDimension.STRUCTURE, "结尾回扣偏弱",
                    "佳作在结尾用一句回到开头的画面，你的结尾停在总结上。",
                    "试试把开头出现过的那个画面放到最后一句。"));
        }

        // 选材：细节够不够具体
        if (countHits(text, DETAIL_WORDS) < WritingConstants.MIN_DETAIL_WORD_COUNT) {
            candidates.put(WritingDimension.MATERIAL, diff(WritingDimension.MATERIAL, "选材角度同质",
                    "佳作写的是只有当事人才知道的小细节，你写的内容换个人也成立。",
                    "换一个只有你才有的细节写进去。"));
        }

        // 手法：有没有把画面写出来
        if (countHits(text, HAND_WORDS) == 0) {
            candidates.put(WritingDimension.HAND, diff(WritingDimension.HAND, "缺少手法例句",
                    "全文是叙述，没有一处让人看见画面的描写。",
                    "在最紧张的那一段加一个特写镜头。"));
        }

        // 连接词：句与句之间有没有衔接
        if (countHits(text, CONNECTORS) < WritingConstants.MIN_CONNECTOR_COUNT) {
            candidates.put(WritingDimension.CONNECTOR, diff(WritingDimension.CONNECTOR, "句与句之间缺衔接",
                    "相邻的句子直接接在一起了，读起来像几段话拼在一起。",
                    "在转折和推进的地方补上连接词。"));
        }

        // 句长：句子是不是太长
        int maxSentence = 0;
        for (String sentence : sentences) {
            maxSentence = Math.max(maxSentence, effectiveLength(sentence));
        }
        if (maxSentence > WritingConstants.MAX_SENTENCE_LENGTH) {
            candidates.put(WritingDimension.SENTENCE_LENGTH, diff(WritingDimension.SENTENCE_LENGTH, "有句子过长",
                    "有句子一口气写太长，读到后半句容易走神。",
                    "把长句在意思转折的地方断成两句。"));
        }

        // 字数：篇幅够不够
        if (length < WritingConstants.MIN_DRAFT_LENGTH) {
            candidates.put(WritingDimension.LENGTH, diff(WritingDimension.LENGTH, "篇幅偏短",
                    "同样的题目，佳作把事情写得更细，你的正文还没有铺开。",
                    "把最想让人记住的那一段再写具体一点。"));
        } else if (length > WritingConstants.MAX_DRAFT_LENGTH) {
            candidates.put(WritingDimension.LENGTH, diff(WritingDimension.LENGTH, "篇幅偏长",
                    "同样的题目，佳作把篇幅留给了细节，你铺开得太多。",
                    "删掉重复交代的部分，把位置留给最关键的那一段。"));
        }

        List<WritingDiffVO> ordered = new ArrayList<>();
        for (String dimension : WritingDimension.PRIORITY) {
            WritingDiffVO candidate = candidates.get(dimension);
            if (candidate != null) {
                ordered.add(candidate);
            }
        }
        if (ordered.size() > WritingConstants.MAX_DIFF_COUNT) {
            return new ArrayList<>(ordered.subList(0, WritingConstants.MAX_DIFF_COUNT));
        }
        return ordered;
    }

    /**
     * 构造一条差异：维度 + 说明 + 建议（<b>不含分数与等级</b>）。
     */
    private WritingDiffVO diff(String dimension, String title, String detail, String suggestion) {
        WritingDiffVO vo = new WritingDiffVO();
        vo.setDimension(dimension);
        vo.setTitle(title);
        vo.setDetail(detail);
        vo.setSuggestion(suggestion);
        return vo;
    }

    /**
     * 结尾有没有回扣开头：首尾两句去掉虚词后是否还剩下共同的内容字。
     */
    private boolean echoOpening(List<String> sentences) {
        if (sentences.size() < 2) {
            return false;
        }
        Set<String> first = contentChars(sentences.get(0));
        Set<String> last = contentChars(sentences.get(sentences.size() - 1));
        if (first.size() < 4 || last.size() < 4) {
            return false;
        }
        for (String ch : first) {
            if (last.contains(ch)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> contentChars(String sentence) {
        Set<String> chars = new LinkedHashSet<>();
        if (sentence == null) {
            return chars;
        }
        for (int i = 0; i < sentence.length(); i++) {
            char ch = sentence.charAt(i);
            if (ch < 0x4e00 || ch > 0x9fa5) {
                continue;
            }
            if (FUNCTION_CHARS.indexOf(ch) >= 0) {
                continue;
            }
            chars.add(String.valueOf(ch));
        }
        return chars;
    }

    private int countHits(String text, List<String> words) {
        int hits = 0;
        for (String word : words) {
            int index = text.indexOf(word);
            while (index >= 0) {
                hits++;
                index = text.indexOf(word, index + word.length());
            }
        }
        return hits;
    }

    /**
     * 有效字数：去掉空白与标点后剩下的字符数。
     */
    private int effectiveLength(String text) {
        if (text == null) {
            return 0;
        }
        int length = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isWhitespace(ch)) {
                continue;
            }
            if (ch < 0x4e00 || ch > 0x9fa5) {
                continue;
            }
            length++;
        }
        return length;
    }

    private List<String> splitSentences(String text) {
        List<String> sentences = new ArrayList<>();
        if (text == null) {
            return sentences;
        }
        for (String part : text.split(SENTENCE_SPLIT)) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                sentences.add(trimmed);
            }
        }
        return sentences;
    }

    // ------------------------------------------------------------------ 内部：病句入池

    /**
     * 病句进统一错题池（F-WRT-05 / TC-06-07）。
     *
     * <p>内容ID = 初稿业务ID + 病句摘要，因此<b>同一处病句只入一次</b>；
     * 错因取五维之一，判定来源恒为 {@code rule}。</p>
     */
    private void reportIllSentences(String childId, String draftCode, String content,
                                    String knowledgePointCode) {
        for (IllSentence ill : detectIllSentences(content)) {
            StudyErrorReportCommand command = new StudyErrorReportCommand();
            command.setChildId(childId);
            command.setSourceModule(WritingConstants.SOURCE_MODULE);
            command.setContentType(WritingConstants.CONTENT_TYPE);
            command.setContentId(draftCode + "#" + WritingConstants.digest6(ill.getSentence()));
            command.setKnowledgePointCode(knowledgePointCode);
            command.setSubjectCode(WritingConstants.SUBJECT_CODE);
            command.setErrorCause(ill.getCause());
            command.setRawAnswer(ill.getSentence());
            command.setCorrectAnswer(ill.getFix());
            command.setJudgeSource(JudgeSource.RULE.getCode());
            command.setCopyrightType(WritingConstants.COPYRIGHT_SELF);
            errorPoolService.report(command);
        }
    }

    /**
     * 病句检出（确定性规则，一条句子只记一处问题）。
     */
    private List<IllSentence> detectIllSentences(String content) {
        List<IllSentence> found = new ArrayList<>();
        for (String sentence : splitSentences(content)) {
            IllSentence ill = detectIllSentence(sentence);
            if (ill != null) {
                found.add(ill);
            }
        }
        return found;
    }

    private IllSentence detectIllSentence(String sentence) {
        // 1) 重复的助词
        for (int i = 0; i < sentence.length() - 1; i++) {
            char ch = sentence.charAt(i);
            if ("的了在是就很也都又着".indexOf(ch) >= 0 && sentence.charAt(i + 1) == ch) {
                return new IllSentence(sentence,
                        ErrorCause.CONCEPT_UNCLEAR,
                        sentence.substring(0, i + 1) + sentence.substring(i + 2));
            }
        }
        // 2) 因为没有所以
        if (sentence.contains("因为") && !sentence.contains("所以")) {
            return new IllSentence(sentence,
                    ErrorCause.CONCEPT_UNCLEAR,
                    "用了「因为」就要接「所以」，把结果那半句补上。");
        }
        // 3) 虽然没有但是
        if (sentence.contains("虽然") && !sentence.contains("但是") && !sentence.contains("但")) {
            return new IllSentence(sentence,
                    ErrorCause.CONCEPT_UNCLEAR,
                    "用了「虽然」就要接「但是」，把转折那半句补上。");
        }
        // 4) 通过…使… 叠用，句子缺主语
        if (sentence.contains("通过") && sentence.contains("使")) {
            return new IllSentence(sentence,
                    ErrorCause.CONCEPT_UNCLEAR,
                    "「通过」和「使」叠在一起会缺主语，删掉其中一个。");
        }
        // 5) 不但没有而且
        if (sentence.contains("不但") && !sentence.contains("而且")) {
            return new IllSentence(sentence,
                    ErrorCause.CONCEPT_UNCLEAR,
                    "用了「不但」就要接「而且」，把递进那半句补上。");
        }
        return null;
    }

    /**
     * 病句条目。
     */
    private static final class IllSentence {

        /** 病句原文 */
        private final String sentence;

        /** 错因（五维之一） */
        private final String cause;

        /** 改正后的句子；改不了就放改正方向 */
        private final String fix;

        IllSentence(String sentence, String cause, String fix) {
            this.sentence = sentence;
            this.cause = cause;
            this.fix = fix;
        }

        String getSentence() {
            return sentence;
        }

        String getCause() {
            return cause;
        }

        String getFix() {
            return fix;
        }
    }

    // ------------------------------------------------------------------ 内部：身份

    /**
     * 当前登录孩子；家长等非孩子身份一律 4010。
     */
    private String currentChildId() {
        String childId = scopeService.ownChildId();
        if (childId == null || childId.trim().isEmpty()) {
            throw new StudyForbiddenException("当前登录身份不是孩子，无权访问学习数据");
        }
        return childId;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
