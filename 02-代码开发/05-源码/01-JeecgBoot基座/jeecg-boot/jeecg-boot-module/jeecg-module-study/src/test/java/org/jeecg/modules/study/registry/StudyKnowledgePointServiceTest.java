package org.jeecg.modules.study.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.study.common.constant.StudyErrorCode;
import org.jeecg.modules.study.registry.service.StudyKnowledgePointService;
import org.jeecg.modules.study.registry.vo.KnowledgePointContentStatVO;
import org.jeecg.modules.study.registry.vo.KnowledgePointImportNode;
import org.jeecg.modules.study.registry.vo.KnowledgePointImportRequest;
import org.jeecg.modules.study.registry.vo.KnowledgePointTreeVO;
import org.jeecg.modules.study.registry.vo.KnowledgePointUpdateRequest;
import org.jeecg.modules.study.registry.vo.KnowledgePointVO;
import org.jeecg.modules.study.registry.vo.KnowledgePointVersionVO;
import org.jeecg.modules.study.registry.vo.MotherQuestionOptionVO;
import org.jeecg.modules.study.support.StudyTestSupport;

/**
 * 坐标树（考点树）自动化测试。
 *
 * <p>覆盖测试用例 TC-01-08 … TC-01-14 与 TC-08-23。</p>
 *
 * <p>坐标树是<b>全局内容表</b>（无 child_id），测试数据统一以 {@code UT} 前缀命名并在
 * {@code @AfterEach} 清理；为验证「下挂内容统计」，测试会向五张内容表各插入 1 条
 * 挂在本用例专属考点下的临时数据（生产代码对这些表<b>只读</b>）。</p>
 *
 * @author jeecg-study
 */
class StudyKnowledgePointServiceTest extends StudyTestSupport {

    @Autowired
    private StudyKnowledgePointService knowledgePointService;

    /**
     * 清理测试数据：坐标树节点 + 五张内容表的临时行 + 临时错题。
     */
    @AfterEach
    void cleanRegistryData() {
        try {
            jdbc.update("DELETE FROM study_error_pool WHERE error_code LIKE 'UTERR-%'");
            jdbc.update("DELETE FROM study_question WHERE question_code LIKE 'UTQST-%'");
            jdbc.update("DELETE FROM study_word WHERE word_code LIKE 'UTWRD-%'");
            jdbc.update("DELETE FROM study_mother_question WHERE mother_code LIKE 'UTMTH-%'");
            jdbc.update("DELETE FROM study_grammar_point WHERE grammar_code LIKE 'UTGRP-%'");
            jdbc.update("DELETE FROM study_writing WHERE writing_code LIKE 'UTWRT-%'");
            jdbc.update("DELETE FROM study_knowledge_point WHERE name LIKE 'UT%'");
        } catch (Exception e) {
            System.out.println("[StudyKnowledgePointServiceTest] 清理测试数据失败：" + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ TC-01-08

    @Test
    @DisplayName("TC-01-08 坐标树四层可展开：考纲 → 学科 → 单元 → 考点")
    void tree_fourLevelsExpandable() {
        String tag = newTag();
        importSampleTree("v1", "MA", tag);

        KnowledgePointTreeVO tree = knowledgePointService.tree(null);
        KnowledgePointVO outline = findByName(tree.getNodes(), tag + "考纲");
        assertThat(outline).isNotNull();
        assertThat(outline.getLevel()).isEqualTo(1);

        KnowledgePointVO subject = onlyChild(outline);
        assertThat(subject.getLevel()).isEqualTo(2);
        assertThat(subject.getSubjectCode()).isEqualTo("MA");

        KnowledgePointVO unit = onlyChild(subject);
        assertThat(unit.getLevel()).isEqualTo(3);

        KnowledgePointVO point = onlyChild(unit);
        assertThat(point.getLevel()).isEqualTo(4);
        assertThat(point.getCode()).startsWith("KPT-MA-");
        assertThat(point.getChildren()).isEmpty();

        // 物化路径 = 祖先链（不含自身）：/id1/id2/id3/
        assertThat(point.getPath()).isEqualTo("/" + outline.getId() + "/" + subject.getId() + "/" + unit.getId() + "/");
        assertThat(point.getPath().chars().filter(c -> c == '/').count()).isEqualTo(4L);

        // 按学科过滤时祖先链仍保留，根节点不丢
        KnowledgePointTreeVO filtered = knowledgePointService.tree("MA");
        assertThat(findByName(filtered.getNodes(), tag + "考纲")).isNotNull();
    }

    // ------------------------------------------------------------------ TC-01-09

    @Test
    @DisplayName("TC-01-09 考点四属性维护：题型/分值权重/常见错因/母题，母题下拉只列存在母题")
    void maintainFourAttributes() {
        String tag = newTag();
        KnowledgePointVO point = level4Of(importSampleTree("v1", "MA", tag));

        // 造一条真实存在的母题
        String motherCode = insertMother("MA", null);
        List<MotherQuestionOptionVO> options = knowledgePointService.motherQuestionOptions("MA");
        assertThat(options).extracting(MotherQuestionOptionVO::getMotherCode).contains(motherCode);

        KnowledgePointUpdateRequest request = new KnowledgePointUpdateRequest()
                .setQuestionType("应用题/画图题")
                .setScoreWeight(new BigDecimal("6.50"))
                .setErrorTypes(Arrays.asList("审题偏差", "方法不会"))
                .setMotherQuestionCode(motherCode);

        KnowledgePointVO updated = knowledgePointService.updateAttributes(point.getCode(), request);
        assertThat(updated.getQuestionType()).isEqualTo("应用题/画图题");
        assertThat(updated.getScoreWeight()).isEqualByComparingTo("6.50");
        assertThat(updated.getErrorTypes()).containsExactly("审题偏差", "方法不会");
        assertThat(updated.getMotherQuestionCode()).isEqualTo(motherCode);

        // 详情同样显示四属性
        KnowledgePointVO detail = knowledgePointService.detail(point.getCode());
        assertThat(detail.getQuestionType()).isEqualTo("应用题/画图题");
        assertThat(detail.getScoreWeight()).isEqualByComparingTo("6.50");
        assertThat(detail.getErrorTypes()).containsExactly("审题偏差", "方法不会");
        assertThat(detail.getMotherQuestionCode()).isEqualTo(motherCode);
    }

    // ------------------------------------------------------------------ TC-01-10

    @Test
    @DisplayName("TC-01-10 母题下拉校验不存在项：指向不存在母题返回 4004 且不落库")
    void invalidMotherCode_rejectedAndNotPersisted() {
        String tag = newTag();
        KnowledgePointVO point = level4Of(importSampleTree("v1", "MA", tag));
        String validMother = insertMother("MA", null);

        knowledgePointService.updateAttributes(point.getCode(), new KnowledgePointUpdateRequest()
                .setQuestionType("应用题")
                .setMotherQuestionCode(validMother));

        // 构造指向不存在母题的请求
        assertThatThrownBy(() -> knowledgePointService.updateAttributes(point.getCode(),
                new KnowledgePointUpdateRequest()
                        .setQuestionType("画图题")
                        .setMotherQuestionCode("UT-NOT-EXIST-" + unique())))
                .isInstanceOf(JeecgBootException.class)
                .satisfies(e -> assertThat(((JeecgBootException) e).getErrCode())
                        .isEqualTo(StudyErrorCode.CONTEXT_NOT_CONFIGURED));

        // 不落库：母题仍是原来的那个
        Map<String, Object> row = queryOne("SELECT question_type, mother_question_code FROM study_knowledge_point WHERE id = ?",
                point.getId());
        assertThat(row.get("mother_question_code")).isEqualTo(validMother);
        assertThat(row.get("question_type")).isEqualTo("应用题");
    }

    // ------------------------------------------------------------------ TC-01-11

    @Test
    @DisplayName("TC-01-11 考点下挂内容统计：题目/单词/母题/语法点/写作 五类数量")
    void contentStat_fiveTypes() {
        String tag = newTag();
        KnowledgePointVO point = level4Of(importSampleTree("v1", "MA", tag));
        String pointCode = point.getCode();

        insertQuestion(pointCode);
        insertWord(pointCode);
        insertMother("MA", pointCode);
        insertGrammar(pointCode);
        insertWriting(pointCode);

        KnowledgePointContentStatVO stat = knowledgePointService.contentStat(pointCode);
        assertThat(stat.getQuestion()).isEqualTo(1);
        assertThat(stat.getWord()).isEqualTo(1);
        assertThat(stat.getMother()).isEqualTo(1);
        assertThat(stat.getGrammar()).isEqualTo(1);
        assertThat(stat.getWriting()).isEqualTo(1);

        // 树上节点的 childCount 同样是五类
        KnowledgePointTreeVO tree = knowledgePointService.tree("MA");
        KnowledgePointVO inTree = findByName(tree.getNodes(), tag + "相遇问题");
        assertThat(inTree).isNotNull();
        assertThat(inTree.getChildCount().getQuestion()).isEqualTo(1);
        assertThat(inTree.getChildCount().getWriting()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ TC-01-12

    @Test
    @DisplayName("TC-01-12 坐标树版本化：旧节点失效不删除，新节点 version 递增")
    void newVersion_invalidatesOldNodesWithoutDeleting() {
        String tag = newTag();
        KnowledgePointVO oldPoint = level4Of(importSampleTree("v1", "MA", tag));
        KnowledgePointVO newPoint = level4Of(importSampleTree("v2", "MA", tag));

        assertThat(newPoint.getCode()).isNotEqualTo(oldPoint.getCode());
        assertThat(newPoint.getVersion()).isEqualTo("v2");
        assertThat(oldPoint.getVersion()).isEqualTo("v1");

        // 旧节点：status=0、invalid_at 有值、记录仍在
        Map<String, Object> old = queryOne(
                "SELECT status, invalid_at, version, del_flag FROM study_knowledge_point WHERE id = ?", oldPoint.getId());
        assertThat(old).isNotNull();
        assertThat(((Number) old.get("status")).intValue()).isZero();
        assertThat(old.get("invalid_at")).isNotNull();
        assertThat(old.get("version")).isEqualTo("v1");
        assertThat(((Number) old.get("del_flag")).intValue()).isZero();

        // 版本清单里同时有 v1 与 v2
        List<KnowledgePointVersionVO> versions = knowledgePointService.versions();
        assertThat(versions).extracting(KnowledgePointVersionVO::getVersion).contains("v1", "v2");

        // 历史版本可查
        List<KnowledgePointVO> history = knowledgePointService.history(newPoint.getCode());
        assertThat(history).hasSize(2);
        assertThat(history).extracting(KnowledgePointVO::getVersion).containsExactly("v1", "v2");
    }

    // ------------------------------------------------------------------ TC-01-13

    @Test
    @DisplayName("TC-01-13 历史错题仍可解析到旧坐标：旧节点可查且坐标名不空")
    void oldCoordinateStillResolves() {
        String tag = newTag();
        KnowledgePointVO oldPoint = level4Of(importSampleTree("v1", "MA", tag));
        importSampleTree("v2", "MA", tag);

        // 历史错题仍指向旧 point_code
        String errorCode = "UTERR-" + unique();
        jdbc.update("INSERT INTO study_error_pool (id, error_code, context_id, source_module, content_type, "
                        + "content_id, knowledge_point_code, error_cause) VALUES (?,?,?,?,?,?,?,?)",
                unique32(), errorCode, "UTCTX-" + unique(), "ERROR", "QUESTION",
                "UTCONTENT-" + unique(), oldPoint.getCode(), "CONCEPT");

        String referenced = (String) queryOne(
                "SELECT knowledge_point_code FROM study_error_pool WHERE error_code = ?", errorCode)
                .get("knowledge_point_code");
        assertThat(referenced).isEqualTo(oldPoint.getCode());

        // 旧坐标仍能解析出名称：不报错、不为空
        KnowledgePointVO resolved = knowledgePointService.detail(referenced);
        assertThat(resolved.getName()).isNotBlank();
        assertThat(resolved.getName()).isEqualTo(oldPoint.getName());
        assertThat(resolved.getStatus()).isZero();
        assertThat(resolved.getInvalidAt()).isNotNull();
    }

    // ------------------------------------------------------------------ TC-01-14

    @Test
    @DisplayName("TC-01-14 统一 ID 分配唯一且不复用：KPT-{学科}-{序号}，失效后不回收")
    void idAllocation_uniqueAndNotReused() {
        String tag = newTag();
        List<String> v1Codes = codesOf(importSampleTree("v1", "MA", tag));
        assertThat(v1Codes).allSatisfy(code -> assertThat(code).matches("^KPT-[A-Z]+-\\d{6}$"));
        assertThat(new HashSet<>(v1Codes)).hasSize(v1Codes.size());

        // 失效后重新导入，新编码不复用旧编码
        List<String> v2Codes = codesOf(importSampleTree("v2", "MA", tag));
        assertThat(v2Codes).allSatisfy(code -> assertThat(code).matches("^KPT-[A-Z]+-\\d{6}$"));
        assertThat(new HashSet<>(v2Codes)).hasSize(v2Codes.size());
        assertThat(v2Codes).doesNotContainAnyElementsOf(v1Codes);

        // 全部节点的编码全局唯一
        List<Map<String, Object>> rows = queryList("SELECT point_code FROM study_knowledge_point WHERE name LIKE 'UT%'");
        List<String> all = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            all.add((String) row.get("point_code"));
        }
        assertThat(new HashSet<>(all)).hasSize(all.size());
    }

    // ------------------------------------------------------------------ TC-08-23

    @Test
    @DisplayName("TC-08-23 坐标树版本化数据正确：旧节点 status=0、invalid_at 有值、记录未删")
    void versionedDataCorrectInDb() {
        String tag = newTag();
        KnowledgePointVO oldPoint = level4Of(importSampleTree("v1", "MA", tag));
        KnowledgePointVO newPoint = level4Of(importSampleTree("v2", "MA", tag));

        // DB 层面检查
        Map<String, Object> row = queryOne(
                "SELECT status, invalid_at, version, del_flag FROM study_knowledge_point WHERE point_code = ?",
                oldPoint.getCode());
        assertThat(row).isNotNull();
        assertThat(((Number) row.get("status")).intValue()).isEqualTo(0);
        assertThat(row.get("invalid_at")).isNotNull();
        assertThat(row.get("version")).isEqualTo("v1");

        // 记录未被删除（同一 point_code 仍然只有一行，且整棵树没有减少）
        assertThat(count("SELECT COUNT(*) FROM study_knowledge_point WHERE point_code = ?",
                oldPoint.getCode())).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM study_knowledge_point WHERE name LIKE ?", tag + "%"))
                .isEqualTo(8);

        // 失效节点不出现在现行树里（同名只剩新版本一个），但仍可按编码查到
        List<KnowledgePointVO> matched = collectByName(
                knowledgePointService.tree(null).getNodes(), tag + "相遇问题");
        assertThat(matched).hasSize(1);
        assertThat(matched.get(0).getCode()).isEqualTo(newPoint.getCode());
        assertThat(knowledgePointService.detail(oldPoint.getCode()).getName()).isEqualTo(oldPoint.getName());
    }

    // ------------------------------------------------------------------ 内部工具

    /**
     * 导入一棵四层样例树：考纲 → 学科 → 单元 → 考点。
     */
    private List<KnowledgePointVO> importSampleTree(String version, String subjectCode, String tag) {
        KnowledgePointImportRequest request = new KnowledgePointImportRequest()
                .setVersion(version)
                .setSubjectCode(subjectCode)
                .setNodes(Arrays.asList(
                        importNode(tag + "-outline", null, 1, subjectCode, tag + "考纲", 1),
                        importNode(tag + "-subject", tag + "-outline", 2, subjectCode, tag + "数学", 1),
                        importNode(tag + "-unit", tag + "-subject", 3, subjectCode, tag + "行程问题", 1),
                        importNode(tag + "-point", tag + "-unit", 4, subjectCode, tag + "相遇问题", 1)));
        return knowledgePointService.importTree(request);
    }

    private KnowledgePointImportNode importNode(String refKey, String parentRefKey, int level,
                                               String subjectCode, String name, int sortNo) {
        return new KnowledgePointImportNode()
                .setRefKey(refKey)
                .setParentRefKey(parentRefKey)
                .setLevel(level)
                .setSubjectCode(subjectCode)
                .setName(name)
                .setSortNo(sortNo);
    }

    private String newTag() {
        return "UT" + unique();
    }

    private KnowledgePointVO level4Of(List<KnowledgePointVO> nodes) {
        for (KnowledgePointVO node : nodes) {
            if (node.getLevel() != null && node.getLevel() == 4) {
                return node;
            }
        }
        throw new IllegalStateException("导入结果里没有第四层考点");
    }

    private List<String> codesOf(List<KnowledgePointVO> nodes) {
        List<String> codes = new ArrayList<>();
        for (KnowledgePointVO node : nodes) {
            codes.add(node.getCode());
        }
        return codes;
    }

    private KnowledgePointVO findByName(List<KnowledgePointVO> nodes, String name) {
        List<KnowledgePointVO> matched = collectByName(nodes, name);
        return matched.isEmpty() ? null : matched.get(0);
    }

    private List<KnowledgePointVO> collectByName(List<KnowledgePointVO> nodes, String name) {
        List<KnowledgePointVO> matched = new ArrayList<>();
        for (KnowledgePointVO node : nodes) {
            if (name.equals(node.getName())) {
                matched.add(node);
            }
            matched.addAll(collectByName(node.getChildren(), name));
        }
        return matched;
    }

    private KnowledgePointVO onlyChild(KnowledgePointVO node) {
        assertThat(node.getChildren()).hasSize(1);
        return node.getChildren().get(0);
    }

    private String insertQuestion(String pointCode) {
        String code = "UTQST-" + unique();
        jdbc.update("INSERT INTO study_question (id, question_code, question_type, subject_code, "
                        + "knowledge_point_code, stem, answer, difficulty, source_type, copyright_type, status, del_flag) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                unique32(), code, "SINGLE", "MA", pointCode, "UT题干", "A", 3, "SELF", "SELF", 1, 0);
        return code;
    }

    private String insertWord(String pointCode) {
        String code = "UTWRD-" + unique();
        jdbc.update("INSERT INTO study_word (id, word_code, spelling, meaning, line_type, "
                        + "knowledge_point_code, frequency, memory_strength, decay_lambda, feedback_count, "
                        + "known_flag, status, del_flag) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                unique32(), code, "utword", "UT释义", "OUT", pointCode, 1, 0, 2.0000, 0, 0, 1, 0);
        return code;
    }

    private String insertMother(String subjectCode, String pointCode) {
        String code = "UTMTH-" + unique();
        jdbc.update("INSERT INTO study_mother_question (id, mother_code, title, condition_types, key_relation, "
                        + "steps, pitfalls, subject_code, knowledge_point_code, pass_variant_count, variant_count, "
                        + "order_no, status, del_flag) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                unique32(), code, "UT题型", "[]", "UT关键关系", "[]", "[]", subjectCode, pointCode, 3, 0, 1, 1, 0);
        return code;
    }

    private String insertGrammar(String pointCode) {
        String code = "UTGRP-" + unique();
        jdbc.update("INSERT INTO study_grammar_point (id, grammar_code, title, rule, example, counter_example, "
                        + "subject_code, knowledge_point_code, status, del_flag) VALUES (?,?,?,?,?,?,?,?,?,?)",
                unique32(), code, "UT语法点", "UT规则", "UT例句", "UT反例", "EN", pointCode, 1, 0);
        return code;
    }

    private String insertWriting(String pointCode) {
        String code = "UTWRT-" + unique();
        jdbc.update("INSERT INTO study_writing (id, writing_code, layer, title, content, subject_code, "
                        + "knowledge_point_code, copyright_type, status, del_flag) VALUES (?,?,?,?,?,?,?,?,?,?)",
                unique32(), code, "HOOK", "UT作文", "UT正文", "CN", pointCode, "SELF", 1, 0);
        return code;
    }

    private String unique32() {
        return unique() + unique() + unique();
    }
}
