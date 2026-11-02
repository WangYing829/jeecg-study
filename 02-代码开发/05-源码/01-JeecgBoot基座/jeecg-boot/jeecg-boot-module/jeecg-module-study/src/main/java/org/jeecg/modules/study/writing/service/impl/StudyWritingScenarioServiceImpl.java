package org.jeecg.modules.study.writing.service.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.study.writing.entity.StudyWritingScenario;
import org.jeecg.modules.study.writing.mapper.StudyWritingScenarioMapper;
import org.jeecg.modules.study.writing.service.StudyWritingScenarioService;
import org.jeecg.modules.study.writing.vo.WritingScenarioListVO;
import org.jeecg.modules.study.writing.vo.WritingScenarioThemeListVO;
import org.jeecg.modules.study.writing.vo.WritingScenarioThemeVO;
import org.jeecg.modules.study.writing.vo.WritingScenarioVO;

/**
 * 写作情境库服务实现（F-WRT-03）。
 *
 * <p><b>产品口径</b>：同一个主题下给<b>不同处境、不同年龄、不同细节</b>的情境，
 * <b>不给金句</b>；孩子必须写自己的经历。这里只做确定性的查询与字段拼装。</p>
 *
 * <p><b>数据范围</b>：情境库是<b>全局共享的内容表</b>（表里没有 child_id），
 * 本服务不读任何孩子数据，也不做按孩子的隔离。</p>
 *
 * <p><b>R-02</b>：写作域<b>没有 AI 客户端</b>，本实现只依赖确定性规则与 JSON 解析。</p>
 *
 * @author jeecg-study
 */
@Service
@RequiredArgsConstructor
public class StudyWritingScenarioServiceImpl implements StudyWritingScenarioService {

    private final StudyWritingScenarioMapper scenarioMapper;

    /** JSON 解析（细节线索是 JSON 数组文本） */
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public WritingScenarioThemeListVO listThemes(String stageCode) {
        List<WritingScenarioThemeVO> rows = scenarioMapper.selectThemes(trim(stageCode));
        WritingScenarioThemeListVO vo = new WritingScenarioThemeListVO();
        vo.setItems(rows == null ? new ArrayList<>() : rows);
        vo.setTotal(vo.getItems().size());
        return vo;
    }

    @Override
    public WritingScenarioListVO listScenarios(String themeCode, String stageCode) {
        List<StudyWritingScenario> rows = scenarioMapper.selectScenarios(trim(themeCode), trim(stageCode));
        List<WritingScenarioVO> items = new ArrayList<>();
        if (rows != null) {
            for (StudyWritingScenario row : rows) {
                if (row != null) {
                    items.add(toVO(row));
                }
            }
        }
        WritingScenarioListVO vo = new WritingScenarioListVO();
        vo.setItems(items);
        vo.setTotal(items.size());
        return vo;
    }

    @Override
    public WritingScenarioVO getScenario(String scenarioCode) {
        String code = trim(scenarioCode);
        if (code.isEmpty()) {
            throw new JeecgBootException("情境编码不能为空", 400);
        }
        StudyWritingScenario row = scenarioMapper.selectByCode(code);
        if (row == null) {
            throw new JeecgBootException("情境不存在：" + code, 400);
        }
        return toVO(row);
    }

    // ------------------------------------------------------------------ 内部

    /**
     * 实体 → 视图。
     */
    private WritingScenarioVO toVO(StudyWritingScenario row) {
        WritingScenarioVO vo = new WritingScenarioVO();
        vo.setScenarioCode(trim(row.getScenarioCode()));
        vo.setThemeCode(trim(row.getThemeCode()));
        vo.setThemeName(trim(row.getThemeName()));
        vo.setTitle(trim(row.getTitle()));
        vo.setSituationDesc(row.getSituationDesc());
        vo.setAgeStage(trim(row.getAgeStage()));
        vo.setAgeMin(row.getAgeMin());
        vo.setAgeMax(row.getAgeMax());
        vo.setDetailClues(parseClues(row.getDetailClue()));
        vo.setStageCode(trim(row.getStageCode()));
        vo.setGenre(trim(row.getGenre()));
        vo.setDifficulty(row.getDifficulty());
        vo.setPromptQuestion(row.getPromptQuestion());
        vo.setRestrictionNote(row.getRestrictionNote());
        vo.setForbiddenWords(row.getForbiddenWords());
        return vo;
    }

    /**
     * 解析细节线索：优先按 JSON 数组解析，失败时按分隔符切分。
     *
     * <p>只做确定性的切分，<b>不生成任何内容</b>（R-02）。</p>
     */
    private List<String> parseClues(String raw) {
        List<String> out = new ArrayList<>();
        String text = trim(raw);
        if (text.isEmpty()) {
            return out;
        }
        try {
            List<String> parsed = objectMapper.readValue(text, new TypeReference<List<String>>() {
            });
            if (parsed != null) {
                for (String item : parsed) {
                    String value = trim(item);
                    if (!value.isEmpty()) {
                        out.add(value);
                    }
                }
                return out;
            }
        } catch (Exception e) {
            // 不是 JSON 就走分隔符兜底，不抛错也不丢内容
        }
        String[] parts = text.split("；|;|、|，");
        for (String part : parts) {
            String value = trim(part);
            if (!value.isEmpty()) {
                out.add(value);
            }
        }
        return out;
    }

    /**
     * 去空白（null 视为空串）。
     */
    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
