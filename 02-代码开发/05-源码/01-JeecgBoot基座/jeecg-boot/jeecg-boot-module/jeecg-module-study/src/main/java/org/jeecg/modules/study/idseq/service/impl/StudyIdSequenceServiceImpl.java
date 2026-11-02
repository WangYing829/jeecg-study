package org.jeecg.modules.study.idseq.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;

import lombok.RequiredArgsConstructor;

import org.jeecg.modules.study.idseq.entity.StudyIdSequence;
import org.jeecg.modules.study.idseq.mapper.StudyIdSequenceMapper;
import org.jeecg.modules.study.idseq.service.StudyIdSequenceService;

/**
 * 统一业务ID 分配器实现。
 *
 * <p>并发安全：整段分配放在一个事务里，先用 {@code SELECT ... FOR UPDATE} 锁住序列行，
 * 再自增并写回；不同前缀互不影响。</p>
 *
 * @author jeecg-study
 */
@Service
@RequiredArgsConstructor
public class StudyIdSequenceServiceImpl implements StudyIdSequenceService {

    /** 序号位宽 */
    private static final int SEQ_WIDTH = 6;

    /** 默认步长 */
    private static final int DEFAULT_STEP = 1;

    private final StudyIdSequenceMapper sequenceMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public String next(String prefix) {
        return allocate(prefix, null);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public String next(String prefix, String segment) {
        return allocate(prefix, segment);
    }

    private String allocate(String prefix, String segment) {
        if (prefix == null || prefix.trim().isEmpty()) {
            throw new IllegalArgumentException("ID 前缀不能为空");
        }
        String normalized = prefix.trim().toUpperCase();

        StudyIdSequence row = sequenceMapper.selectForUpdate(normalized);
        if (row == null) {
            row = new StudyIdSequence();
            row.setId(IdWorker.get32UUID());
            row.setPrefix(normalized);
            row.setNextValue((long) DEFAULT_STEP);
            row.setStep(DEFAULT_STEP);
            row.setDelFlag(0);
            sequenceMapper.insert(row);
            row = sequenceMapper.selectForUpdate(normalized);
        }

        long value = row.getNextValue() == null ? 1L : row.getNextValue();
        row.setNextValue(value + (row.getStep() == null ? DEFAULT_STEP : row.getStep()));
        sequenceMapper.updateById(row);

        StringBuilder sb = new StringBuilder(normalized);
        if (segment != null && !segment.trim().isEmpty()) {
            sb.append('-').append(segment.trim().toUpperCase());
        }
        sb.append('-').append(String.format("%0" + SEQ_WIDTH + "d", value));
        return sb.toString();
    }
}
