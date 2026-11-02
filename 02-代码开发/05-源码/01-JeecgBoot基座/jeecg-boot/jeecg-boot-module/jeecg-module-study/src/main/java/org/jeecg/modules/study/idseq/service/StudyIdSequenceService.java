package org.jeecg.modules.study.idseq.service;

/**
 * 统一业务ID 分配器（T-V0-05）。
 *
 * <p>ID 形如 {@code CHD-000001}、{@code KPT-MA-000417}。
 * <b>只增不复用</b>：序列一旦被使用过就永远往后走，失效内容也不回收序号，
 * 避免「历史记录引用到一个含义已变的 ID」。</p>
 *
 * @author jeecg-study
 */
public interface StudyIdSequenceService {

    /**
     * 分配一个业务ID，形如 {@code CHD-000001}。
     *
     * @param prefix 前缀（CHD / KPT / QST / MTH / WRD / GRP / WRT / ERR）
     * @return 业务ID
     */
    String next(String prefix);

    /**
     * 分配一个带中段前缀的业务ID，形如 {@code KPT-MA-000417}。
     *
     * @param prefix  前缀（KPT）
     * @param segment 中段（学科码 MA / CH / EN / CN）
     * @return 业务ID
     */
    String next(String prefix, String segment);
}
