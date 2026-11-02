package org.jeecg.modules.study.context.service;

import java.util.List;

import org.jeecg.modules.study.context.vo.ContextSaveRequest;
import org.jeecg.modules.study.context.vo.ContextVO;

/**
 * 全局上下文服务。
 *
 * <p>上下文按 {@code child_id} 隔离：一个孩子同时只有一个当前上下文，
 * 切换时旧上下文保留（历史记录按 context_id 归集，不混入新队列 —— TC-01-06）。</p>
 *
 * @author jeecg-study
 */
public interface StudyContextService {

    /**
     * 取当前上下文。**未配置过不报错**，返回 {@code configured=false}（TC-01-01）。
     *
     * @param childCode 孩子业务ID；可空，空则取当前身份唯一可见的孩子
     * @return 当前上下文；未配置时 configured=false
     */
    ContextVO current(String childCode);

    /**
     * 创建 / 切换当前上下文。
     *
     * <p>学段与年级必须匹配（否则 4004），教材版本必须存在（否则 4004）。</p>
     *
     * @param request   入参
     * @param childCode 孩子业务ID；可空，空则取当前身份唯一可见的孩子
     * @return 生效中的上下文
     */
    ContextVO save(ContextSaveRequest request, String childCode);

    /**
     * 历史上下文列表。
     *
     * @param childCode 孩子业务ID；可空，空则取当前身份唯一可见的孩子
     * @return 历史上下文列表，按生效时间倒序
     */
    List<ContextVO> list(String childCode);

    /**
     * 配置护眼提醒间隔。
     *
     * @param contextIdOrCode 上下文主键 ID 或业务ID
     * @param eyeIntervalMin  提醒间隔（分钟）
     * @return 更新后的上下文
     */
    ContextVO updateEyeInterval(String contextIdOrCode, Integer eyeIntervalMin);
}
