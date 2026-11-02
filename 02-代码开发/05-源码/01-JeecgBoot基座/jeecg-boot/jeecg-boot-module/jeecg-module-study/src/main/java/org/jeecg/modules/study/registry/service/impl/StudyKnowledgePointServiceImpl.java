package org.jeecg.modules.study.registry.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;

import lombok.RequiredArgsConstructor;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.study.common.constant.StudyErrorCode;
import org.jeecg.modules.study.idseq.service.StudyIdSequenceService;
import org.jeecg.modules.study.registry.entity.StudyKnowledgePoint;
import org.jeecg.modules.study.registry.mapper.StudyContentReferenceMapper;
import org.jeecg.modules.study.registry.mapper.StudyKnowledgePointMapper;
import org.jeecg.modules.study.registry.service.StudyKnowledgePointService;
import org.jeecg.modules.study.registry.vo.KnowledgePointContentStatVO;
import org.jeecg.modules.study.registry.vo.KnowledgePointCountVO;
import org.jeecg.modules.study.registry.vo.KnowledgePointImportNode;
import org.jeecg.modules.study.registry.vo.KnowledgePointImportRequest;
import org.jeecg.modules.study.registry.vo.KnowledgePointTreeVO;
import org.jeecg.modules.study.registry.vo.KnowledgePointUpdateRequest;
import org.jeecg.modules.study.registry.vo.KnowledgePointVO;
import org.jeecg.modules.study.registry.vo.KnowledgePointVersionVO;
import org.jeecg.modules.study.registry.vo.MotherQuestionOptionVO;

/**
 * 坐标树（考点树）服务实现。
 *
 * <p><b>四层结构</b>：1考纲 → 2学科 → 3单元 → 4考点；
 * {@code path} 是物化路径，存的是祖先链（不含自身），形如 {@code /id1/id2/id3/}。</p>
 *
 * <p><b>版本化</b>：导入新版本时，该学科旧节点置 {@code status=0} + {@code invalid_at=当天}，
 * <b>记录不删</b>；新节点 {@code version} 递增、{@code point_code} 重新分配且永不复用。
 * 这样历史错题指向的旧 {@code point_code} 依然能解析出坐标名（TC-01-13）。</p>
 *
 * @author jeecg-study
 */
@Service
@RequiredArgsConstructor
public class StudyKnowledgePointServiceImpl implements StudyKnowledgePointService {

    /** 内容统计槽位：题目 */
    private static final int SLOT_QUESTION = 0;

    /** 内容统计槽位：单词 */
    private static final int SLOT_WORD = 1;

    /** 内容统计槽位：母题 */
    private static final int SLOT_MOTHER = 2;

    /** 内容统计槽位：语法点 */
    private static final int SLOT_GRAMMAR = 3;

    /** 内容统计槽位：写作 */
    private static final int SLOT_WRITING = 4;

    /** 五类内容统计槽位总数 */
    private static final int SLOT_COUNT = 5;

    private final StudyKnowledgePointMapper kpMapper;
    private final StudyContentReferenceMapper contentMapper;
    private final StudyIdSequenceService idSequence;

    @Override
    public KnowledgePointTreeVO tree(String subjectCode) {
        List<StudyKnowledgePoint> all = kpMapper.selectValidAll();
        KnowledgePointTreeVO tree = new KnowledgePointTreeVO();
        if (all == null || all.isEmpty()) {
            return tree;
        }

        Map<String, StudyKnowledgePoint> byId = new LinkedHashMap<>();
        for (StudyKnowledgePoint node : all) {
            byId.put(node.getId(), node);
        }

        // 按学科过滤时保留祖先链，避免子节点变成孤儿
        List<StudyKnowledgePoint> kept = new ArrayList<>();
        if (subjectCode != null && !subjectCode.trim().isEmpty()) {
            String subject = subjectCode.trim().toUpperCase();
            Map<String, Boolean> keep = new HashMap<>();
            for (StudyKnowledgePoint node : all) {
                if (subject.equalsIgnoreCase(node.getSubjectCode())) {
                    markAncestors(node, byId, keep);
                }
            }
            for (StudyKnowledgePoint node : all) {
                if (Boolean.TRUE.equals(keep.get(node.getId()))) {
                    kept.add(node);
                }
            }
        } else {
            kept.addAll(all);
        }

        Map<String, int[]> counts = loadCounts();
        Map<String, String> parentCodes = new HashMap<>();
        for (StudyKnowledgePoint node : kept) {
            StudyKnowledgePoint parent = node.getParentId() == null ? null : byId.get(node.getParentId());
            parentCodes.put(node.getId(), parent == null ? null : parent.getPointCode());
        }

        Map<String, KnowledgePointVO> voMap = new LinkedHashMap<>();
        for (StudyKnowledgePoint node : kept) {
            voMap.put(node.getId(), toVO(node, counts, parentCodes));
        }

        List<KnowledgePointVO> roots = new ArrayList<>();
        for (StudyKnowledgePoint node : kept) {
            KnowledgePointVO vo = voMap.get(node.getId());
            KnowledgePointVO parent = node.getParentId() == null ? null : voMap.get(node.getParentId());
            if (parent == null) {
                roots.add(vo);
            } else {
                parent.getChildren().add(vo);
            }
        }

        tree.setNodes(roots);
        tree.setVersion(currentVersion(kept));
        Date updatedAt = null;
        for (StudyKnowledgePoint node : kept) {
            Date time = node.getUpdateTime() != null ? node.getUpdateTime() : node.getCreateTime();
            if (time != null && (updatedAt == null || time.after(updatedAt))) {
                updatedAt = time;
            }
        }
        tree.setUpdatedAt(updatedAt);
        return tree;
    }

    @Override
    public KnowledgePointVO detail(String code) {
        StudyKnowledgePoint node = requireNode(code);
        Map<String, int[]> counts = loadCounts();
        return toVO(node, counts, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgePointVO updateAttributes(String code, KnowledgePointUpdateRequest request) {
        StudyKnowledgePoint node = requireNode(code);
        if (node.getStatus() == null || node.getStatus() != 1) {
            throw new JeecgBootException("该考点已失效，不能维护属性", StudyErrorCode.CONTEXT_NOT_CONFIGURED);
        }
        if (request == null) {
            throw new JeecgBootException("请求体不能为空", 400);
        }

        String motherCode = request.getMotherQuestionCode() == null ? null : request.getMotherQuestionCode().trim();
        if (motherCode != null && !motherCode.isEmpty()) {
            // 母题下拉只列存在母题；指向不存在的母题 → 4004，且不落库（TC-01-10）
            if (contentMapper.countMotherQuestion(motherCode) <= 0) {
                throw new JeecgBootException("母题不存在：" + motherCode, StudyErrorCode.CONTEXT_NOT_CONFIGURED);
            }
        } else {
            motherCode = null;
        }

        String questionType = request.getQuestionType() == null ? null : request.getQuestionType().trim();
        BigDecimal scoreWeight = request.getScoreWeight();
        if (scoreWeight != null && scoreWeight.signum() < 0) {
            throw new JeecgBootException("分值权重不能为负", 400);
        }
        String errorTypes = joinErrorTypes(request.getErrorTypes());

        kpMapper.updateAttributes(node.getId(), questionType, scoreWeight, errorTypes, motherCode);
        return detail(code);
    }

    @Override
    public KnowledgePointContentStatVO contentStat(String code) {
        StudyKnowledgePoint node = requireNode(code);
        return buildStat(loadCounts().get(node.getPointCode()));
    }

    @Override
    public List<KnowledgePointVersionVO> versions() {
        List<KnowledgePointVersionVO> stats = kpMapper.selectVersionStats();
        return stats == null ? new ArrayList<>() : stats;
    }

    @Override
    public List<KnowledgePointVO> history(String code) {
        StudyKnowledgePoint node = requireNode(code);
        List<StudyKnowledgePoint> sameSubject = kpMapper.selectBySubjectIncludingInvalid(node.getSubjectCode());
        List<StudyKnowledgePoint> lineage = new ArrayList<>();
        if (sameSubject != null) {
            for (StudyKnowledgePoint item : sameSubject) {
                if (equalsName(item.getName(), node.getName())
                        && equalsLevel(item.getLevel(), node.getLevel())) {
                    lineage.add(item);
                }
            }
        }
        Collections.sort(lineage, (a, b) -> Integer.compare(versionSeq(a.getVersion()), versionSeq(b.getVersion())));
        Map<String, int[]> counts = loadCounts();
        List<KnowledgePointVO> list = new ArrayList<>();
        for (StudyKnowledgePoint item : lineage) {
            list.add(toVO(item, counts, null));
        }
        return list;
    }

    @Override
    public List<KnowledgePointVO> search(String keyword, String subjectCode) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return new ArrayList<>();
        }
        String kw = keyword.trim();
        String subject = subjectCode == null ? null : subjectCode.trim().toUpperCase();
        List<StudyKnowledgePoint> all = subject == null
                ? kpMapper.selectValidAll() : kpMapper.selectBySubjectIncludingInvalid(subject);
        List<StudyKnowledgePoint> matched = new ArrayList<>();
        if (all != null) {
            for (StudyKnowledgePoint node : all) {
                if (node.getStatus() != null && node.getStatus() == 1
                        && (node.getName() != null && node.getName().contains(kw)
                        || node.getPointCode() != null && node.getPointCode().contains(kw))) {
                    matched.add(node);
                }
            }
        }
        Map<String, int[]> counts = loadCounts();
        List<KnowledgePointVO> list = new ArrayList<>();
        for (StudyKnowledgePoint node : matched) {
            list.add(toVO(node, counts, null));
        }
        return list;
    }

    @Override
    public List<MotherQuestionOptionVO> motherQuestionOptions(String subjectCode) {
        String subject = subjectCode == null ? null : subjectCode.trim().toUpperCase();
        List<MotherQuestionOptionVO> list = contentMapper.selectMotherQuestions(subject);
        return list == null ? new ArrayList<>() : list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<KnowledgePointVO> importTree(KnowledgePointImportRequest request) {
        if (request == null || request.getNodes() == null || request.getNodes().isEmpty()) {
            throw new JeecgBootException("导入内容为空", 400);
        }
        String version = normalizeVersion(request.getVersion());
        List<KnowledgePointImportNode> nodes = new ArrayList<>(request.getNodes());

        String subjectCode = request.getSubjectCode() == null ? null : request.getSubjectCode().trim().toUpperCase();
        if (subjectCode == null || subjectCode.isEmpty()) {
            for (KnowledgePointImportNode node : nodes) {
                if (node.getSubjectCode() != null && !node.getSubjectCode().trim().isEmpty()) {
                    subjectCode = node.getSubjectCode().trim().toUpperCase();
                    break;
                }
            }
        }
        if (subjectCode == null || subjectCode.isEmpty()) {
            throw new JeecgBootException("学科编码不能为空", 400);
        }

        // 父节点必须先于子节点落库
        Collections.sort(nodes, (a, b) -> Integer.compare(levelOf(a), levelOf(b)));

        // 幂等：同版本同父同名同级的节点复用
        List<StudyKnowledgePoint> existing = kpMapper.selectBySubjectIncludingInvalid(subjectCode);
        Map<String, StudyKnowledgePoint> existingIndex = new HashMap<>();
        Map<String, StudyKnowledgePoint> existingById = new HashMap<>();
        Map<String, String> existingNamePaths = new HashMap<>();
        if (existing != null) {
            for (StudyKnowledgePoint item : existing) {
                existingIndex.put(indexKey(item.getVersion(), item.getName(), item.getLevel(), item.getParentId()), item);
                existingById.put(item.getId(), item);
            }
            for (StudyKnowledgePoint item : existing) {
                existingNamePaths.put(item.getId(), namePath(item, existingById, existingNamePaths));
            }
        }

        Map<String, StudyKnowledgePoint> byRefKey = new LinkedHashMap<>();
        Map<String, String> importedNamePaths = new LinkedHashMap<>();
        for (KnowledgePointImportNode node : nodes) {
            StudyKnowledgePoint parent = null;
            if (node.getParentRefKey() != null && !node.getParentRefKey().trim().isEmpty()) {
                parent = byRefKey.get(node.getParentRefKey().trim());
                if (parent == null) {
                    throw new JeecgBootException("父节点不存在或层级顺序错误：" + node.getParentRefKey(), 400);
                }
            }
            String name = node.getName() == null ? null : node.getName().trim();
            if (name == null || name.isEmpty()) {
                throw new JeecgBootException("节点名称不能为空", 400);
            }
            int level = levelOf(node);
            String parentId = parent == null ? null : parent.getId();

            StudyKnowledgePoint reused = existingIndex.get(indexKey(version, name, level, parentId));
            if (reused != null) {
                byRefKey.put(refKeyOf(node), reused);
                importedNamePaths.put(reused.getId(), existingNamePaths.get(reused.getId()));
                continue;
            }

            StudyKnowledgePoint entity = new StudyKnowledgePoint();
            entity.setId(IdWorker.get32UUID());
            entity.setPointCode(idSequence.next("KPT", subjectCode));
            entity.setParentId(parentId);
            entity.setLevel(level);
            entity.setSubjectCode(subjectCode);
            entity.setName(name);
            entity.setPath(parent == null ? "/" : parent.getPath() + parent.getId() + "/");
            entity.setQuestionType(node.getQuestionType());
            entity.setScoreWeight(node.getScoreWeight());
            entity.setErrorTypes(joinErrorTypes(node.getErrorTypes()));
            entity.setMotherQuestionCode(node.getMotherQuestionCode());
            entity.setVersion(version);
            entity.setEffectiveFrom(today());
            entity.setInvalidAt(null);
            entity.setStatus(1);
            entity.setSortNo(node.getSortNo() == null ? 0 : node.getSortNo());
            entity.setDelFlag(0);
            kpMapper.insert(entity);

            existingIndex.put(indexKey(version, name, level, parentId), entity);
            byRefKey.put(refKeyOf(node), entity);
            importedNamePaths.put(entity.getId(),
                    parent == null ? name : importedNamePaths.get(parent.getId()) + "/" + name);
        }

        // 生成新版本：被本次导入替换掉的旧节点只失效、不删除（TC-01-12 / TC-08-23）
        invalidateReplaced(existing, existingNamePaths, importedNamePaths, version);

        Map<String, int[]> counts = loadCounts();
        List<KnowledgePointVO> result = new ArrayList<>();
        for (StudyKnowledgePoint node : byRefKey.values()) {
            result.add(toVO(node, counts, null));
        }
        return result;
    }

    // ------------------------------------------------------------------ 内部

    /**
     * 取节点（<b>含已失效</b>）；不存在返回 4004 类业务错误。
     */
    private StudyKnowledgePoint requireNode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new JeecgBootException("考点编码不能为空", StudyErrorCode.CONTEXT_NOT_CONFIGURED);
        }
        StudyKnowledgePoint node = kpMapper.selectByPointCode(code.trim());
        if (node == null) {
            throw new JeecgBootException("考点不存在：" + code, StudyErrorCode.CONTEXT_NOT_CONFIGURED);
        }
        return node;
    }

    /**
     * 一次性加载五类内容数量（避免逐节点查库）。
     *
     * @return 考点业务ID → 五类数量
     */
    private Map<String, int[]> loadCounts() {
        Map<String, int[]> counts = new HashMap<>();
        mergeCounts(counts, contentMapper.countQuestions(), SLOT_QUESTION);
        mergeCounts(counts, contentMapper.countWords(), SLOT_WORD);
        mergeCounts(counts, contentMapper.countMotherQuestions(), SLOT_MOTHER);
        mergeCounts(counts, contentMapper.countGrammarPoints(), SLOT_GRAMMAR);
        mergeCounts(counts, contentMapper.countWritings(), SLOT_WRITING);
        return counts;
    }

    private void mergeCounts(Map<String, int[]> target, List<KnowledgePointCountVO> rows, int slot) {
        if (rows == null) {
            return;
        }
        for (KnowledgePointCountVO row : rows) {
            if (row.getKnowledgePointCode() == null) {
                continue;
            }
            int[] slotValues = target.computeIfAbsent(row.getKnowledgePointCode(), key -> new int[SLOT_COUNT]);
            slotValues[slot] += row.getTotal() == null ? 0 : row.getTotal();
        }
    }

    /**
     * 组装五类统计：题目 / 单词 / 母题 / 语法点 / 写作。
     */
    private KnowledgePointContentStatVO buildStat(int[] slotValues) {
        KnowledgePointContentStatVO stat = new KnowledgePointContentStatVO();
        if (slotValues != null) {
            stat.setQuestion(slotValues[SLOT_QUESTION]);
            stat.setWord(slotValues[SLOT_WORD]);
            stat.setMother(slotValues[SLOT_MOTHER]);
            stat.setGrammar(slotValues[SLOT_GRAMMAR]);
            stat.setWriting(slotValues[SLOT_WRITING]);
        }
        return stat;
    }

    private KnowledgePointVO toVO(StudyKnowledgePoint node, Map<String, int[]> counts,
                                 Map<String, String> parentCodeById) {
        KnowledgePointVO vo = new KnowledgePointVO();
        vo.setId(node.getId());
        vo.setCode(node.getPointCode());
        vo.setLevel(node.getLevel());
        vo.setSubjectCode(node.getSubjectCode());
        vo.setName(node.getName());
        vo.setPath(node.getPath());
        vo.setQuestionType(node.getQuestionType());
        vo.setScoreWeight(node.getScoreWeight());
        vo.setErrorTypes(splitErrorTypes(node.getErrorTypes()));
        vo.setMotherQuestionCode(node.getMotherQuestionCode());
        vo.setVersion(node.getVersion());
        vo.setEffectiveFrom(node.getEffectiveFrom());
        vo.setInvalidAt(node.getInvalidAt());
        vo.setStatus(node.getStatus());
        vo.setSortNo(node.getSortNo());
        vo.setChildCount(buildStat(counts == null ? null : counts.get(node.getPointCode())));
        vo.setParentCode(resolveParentCode(node, parentCodeById));
        return vo;
    }

    /**
     * 解析父节点业务ID；传入映射时走内存，否则回库查一次。
     */
    private String resolveParentCode(StudyKnowledgePoint node, Map<String, String> parentCodeById) {
        if (node.getParentId() == null) {
            return null;
        }
        if (parentCodeById != null) {
            return parentCodeById.get(node.getParentId());
        }
        StudyKnowledgePoint parent = kpMapper.selectById(node.getParentId());
        return parent == null ? null : parent.getPointCode();
    }

    /**
     * 标记某节点及其全部祖先为「保留」。
     */
    private void markAncestors(StudyKnowledgePoint node, Map<String, StudyKnowledgePoint> byId,
                               Map<String, Boolean> keep) {
        StudyKnowledgePoint current = node;
        int guard = 0;
        while (current != null && guard++ < 16) {
            keep.put(current.getId(), Boolean.TRUE);
            if (current.getParentId() == null) {
                return;
            }
            current = byId.get(current.getParentId());
        }
    }

    private String currentVersion(List<StudyKnowledgePoint> nodes) {
        String version = null;
        int best = -1;
        for (StudyKnowledgePoint node : nodes) {
            int seq = versionSeq(node.getVersion());
            if (seq > best) {
                best = seq;
                version = node.getVersion();
            }
        }
        return version;
    }

    /**
     * 节点的名字路径（用节点名而不是 ID 串起来，跨版本稳定）。
     *
     * <p>例：{@code 江苏省小学数学考纲/行程问题/相遇问题}。</p>
     */
    private String namePath(StudyKnowledgePoint node, Map<String, StudyKnowledgePoint> byId,
                            Map<String, String> memo) {
        String cached = memo.get(node.getId());
        if (cached != null) {
            return cached;
        }
        String path;
        if (node.getParentId() == null) {
            path = node.getName();
        } else {
            StudyKnowledgePoint parent = byId.get(node.getParentId());
            path = parent == null ? node.getName() : namePath(parent, byId, memo) + "/" + node.getName();
        }
        memo.put(node.getId(), path);
        return path;
    }

    /**
     * 失效「被本次导入替换掉」的旧节点。
     *
     * <p>判定口径：同一学科、名字路径相同、版本号比新版本旧、当前仍现行。
     * 只置 {@code status=0} 与 {@code invalid_at}，<b>不删除记录</b>（TC-01-12 / TC-08-23）。</p>
     */
    private void invalidateReplaced(List<StudyKnowledgePoint> existing,
                                    Map<String, String> existingNamePaths,
                                    Map<String, String> importedNamePaths,
                                    String newVersion) {
        if (existing == null || existing.isEmpty() || importedNamePaths.isEmpty()) {
            return;
        }
        Set<String> replacedPaths = new HashSet<>(importedNamePaths.values());
        int newSeq = versionSeq(newVersion);
        List<String> toInvalidate = new ArrayList<>();
        for (StudyKnowledgePoint item : existing) {
            if (item.getStatus() == null || item.getStatus() != 1) {
                continue;
            }
            if (versionSeq(item.getVersion()) >= newSeq) {
                continue;
            }
            String path = existingNamePaths.get(item.getId());
            if (path != null && replacedPaths.contains(path)) {
                toInvalidate.add(item.getId());
            }
        }
        if (!toInvalidate.isEmpty()) {
            kpMapper.invalidateByIds(toInvalidate, today());
        }
    }

    /**
     * 版本序号：{@code v2} → 2；无法解析按 0。
     */
    private int versionSeq(String version) {
        if (version == null) {
            return 0;
        }
        String digits = version.trim().replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String normalizeVersion(String version) {
        if (version == null || version.trim().isEmpty()) {
            return "v1";
        }
        String trimmed = version.trim().toLowerCase();
        return trimmed.startsWith("v") ? trimmed : "v" + trimmed;
    }

    private String indexKey(String version, String name, Integer level, String parentId) {
        return version + "|" + name + "|" + level + "|" + (parentId == null ? "" : parentId);
    }

    private String refKeyOf(KnowledgePointImportNode node) {
        return node.getRefKey() == null ? "" : node.getRefKey().trim();
    }

    private int levelOf(KnowledgePointImportNode node) {
        return node.getLevel() == null ? StudyKnowledgePoint.LEVEL_POINT : node.getLevel();
    }

    private boolean equalsName(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private boolean equalsLevel(Integer a, Integer b) {
        return a == null ? b == null : a.equals(b);
    }

    private String joinErrorTypes(List<String> errorTypes) {
        if (errorTypes == null || errorTypes.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (String type : errorTypes) {
            if (type == null || type.trim().isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(type.trim());
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private List<String> splitErrorTypes(String errorTypes) {
        List<String> list = new ArrayList<>();
        if (errorTypes == null || errorTypes.trim().isEmpty()) {
            return list;
        }
        for (String item : errorTypes.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                list.add(trimmed);
            }
        }
        return list;
    }

    /**
     * 当天零点（{@code date} 列只存日期）。
     */
    private Date today() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }
}
