package org.jeecg.modules.study.context.service;

import java.util.List;

import org.jeecg.modules.study.context.vo.TextbookVersionVO;

/**
 * 教材版本字典服务。
 *
 * @author jeecg-study
 */
public interface StudyTextbookVersionService {

    /**
     * 教材版本下拉列表。
     *
     * <p>按出版社去重（人教 / 苏教 / 北师大 / 浙教 / 沪教 / 京教 / 鲁教 共 7 项 —— TC-01-07），
     * 按 {@code sort_no} 排序；指定学段时只返回该学段适用的版本。</p>
     *
     * @param stageCode 学段编码；可空，空则返回全部 7 项
     * @return 教材版本列表
     */
    List<TextbookVersionVO> list(String stageCode);

    /**
     * 保证教材版本字典已初始化（幂等）。
     *
     * <p>字典是全局内容表，缺行时补齐 7 家出版社的默认版本；
     * 供上下文保存前校验教材版本存在性时调用。</p>
     */
    void ensureSeed();
}
