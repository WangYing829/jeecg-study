package org.jeecg.modules.study.context.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;

import lombok.RequiredArgsConstructor;

import org.jeecg.modules.study.context.constant.StudyStageGrade;
import org.jeecg.modules.study.context.entity.StudyTextbookVersion;
import org.jeecg.modules.study.context.mapper.StudyTextbookVersionMapper;
import org.jeecg.modules.study.context.service.StudyTextbookVersionService;
import org.jeecg.modules.study.context.vo.TextbookVersionVO;

/**
 * 教材版本字典服务实现。
 *
 * <p>字典表是全局内容表，7 家出版社的默认版本由本服务<b>幂等补齐</b>：
 * 缺行才插，已存在则跳过。这样即使种子脚本没跑，下拉框也一定是 7 项齐全（TC-01-07）。</p>
 *
 * <p>同一出版社在多个学段各有一行（学段决定适用教材），下拉按出版社去重，
 * 优先取学段靠前（小学 → 初中 → 高中）的那一条。</p>
 *
 * @author jeecg-study
 */
@Service
@RequiredArgsConstructor
public class StudyTextbookVersionServiceImpl implements StudyTextbookVersionService {

    /** 教材版本默认字典：版本编码 / 名称 / 出版社 / 适用地区 / 学段 / 排序号 */
    private static final String[][] DEFAULT_VERSIONS = {
            {"TV-RENJIAO-PRIMARY", "人教版", "人教", "全国", "PRIMARY", "1"},
            {"TV-RENJIAO-JUNIOR", "人教版", "人教", "全国", "JUNIOR", "1"},
            {"TV-RENJIAO-SENIOR", "人教版", "人教", "全国", "SENIOR", "1"},
            {"TV-SUJIAO-PRIMARY", "苏教版", "苏教", "江苏", "PRIMARY", "2"},
            {"TV-SUJIAO-JUNIOR", "苏教版", "苏教", "江苏", "JUNIOR", "2"},
            {"TV-BEISHIDA-PRIMARY", "北师大版", "北师大", "全国", "PRIMARY", "3"},
            {"TV-ZHEJIAO-PRIMARY", "浙教版", "浙教", "浙江", "PRIMARY", "4"},
            {"TV-ZHEJIAO-JUNIOR", "浙教版", "浙教", "浙江", "JUNIOR", "4"},
            {"TV-HUJIAO-JUNIOR", "沪教版", "沪教", "上海", "JUNIOR", "5"},
            {"TV-HUJIAO-SENIOR", "沪教版", "沪教", "上海", "SENIOR", "5"},
            {"TV-JINGJIAO-JUNIOR", "京教版", "京教", "北京", "JUNIOR", "6"},
            {"TV-JINGJIAO-SENIOR", "京教版", "京教", "北京", "SENIOR", "6"},
            {"TV-LUJIAO-JUNIOR", "鲁教版", "鲁教", "山东", "JUNIOR", "7"},
            {"TV-LUJIAO-SENIOR", "鲁教版", "鲁教", "山东", "SENIOR", "7"},
    };

    private final StudyTextbookVersionMapper versionMapper;

    @Override
    public List<TextbookVersionVO> list(String stageCode) {
        ensureSeed();
        String stage = stageCode == null ? null : stageCode.trim().toUpperCase();
        List<StudyTextbookVersion> rows = versionMapper.selectByStage(stage);
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }

        // 按出版社去重：同一出版社多学段版本只保留一条
        Map<String, TextbookVersionVO> byPublisher = new LinkedHashMap<>();
        for (StudyTextbookVersion row : rows) {
            String key = row.getPublisher() == null ? row.getVersionName() : row.getPublisher();
            if (key == null) {
                continue;
            }
            TextbookVersionVO existed = byPublisher.get(key);
            if (existed == null || stageRank(row.getStageCode()) < stageRank(existed.getStageCode())) {
                byPublisher.put(key, toVO(row));
            }
        }
        return new ArrayList<>(byPublisher.values());
    }

    @Override
    public void ensureSeed() {
        for (String[] def : DEFAULT_VERSIONS) {
            StudyTextbookVersion existed = versionMapper.selectByVersionCode(def[0]);
            if (existed != null) {
                continue;
            }
            StudyTextbookVersion version = new StudyTextbookVersion();
            version.setId(IdWorker.get32UUID());
            version.setVersionCode(def[0]);
            version.setVersionName(def[1]);
            version.setPublisher(def[2]);
            version.setRegionScope(def[3]);
            version.setStageCode(def[4]);
            version.setSortNo(Integer.parseInt(def[5]));
            version.setDelFlag(0);
            try {
                versionMapper.insert(version);
            } catch (DuplicateKeyException e) {
                // 并发补种时另一线程已插入，忽略
            }
        }
    }

    /**
     * 学段排序权重：小学 1 → 初中 2 → 高中 3；未知按 9。
     */
    private int stageRank(String stageCode) {
        if (StudyStageGrade.STAGE_PRIMARY.equals(stageCode)) {
            return 1;
        }
        if (StudyStageGrade.STAGE_JUNIOR.equals(stageCode)) {
            return 2;
        }
        if (StudyStageGrade.STAGE_SENIOR.equals(stageCode)) {
            return 3;
        }
        return 9;
    }

    private TextbookVersionVO toVO(StudyTextbookVersion row) {
        TextbookVersionVO vo = new TextbookVersionVO();
        vo.setVersionId(row.getId());
        vo.setVersionCode(row.getVersionCode());
        vo.setVersionName(row.getVersionName());
        vo.setPublisher(row.getPublisher());
        vo.setRegionScope(row.getRegionScope());
        vo.setStageCode(row.getStageCode());
        vo.setSortNo(row.getSortNo());
        return vo;
    }
}
