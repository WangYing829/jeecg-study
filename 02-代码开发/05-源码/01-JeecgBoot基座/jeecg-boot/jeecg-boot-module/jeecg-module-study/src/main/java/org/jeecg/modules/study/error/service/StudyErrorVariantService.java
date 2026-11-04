package org.jeecg.modules.study.error.service;

import org.jeecg.modules.study.error.vo.ErrorVariantGenerateVO;

/**
 * 错题变式生成服务（F-WRG-03）。
 *
 * <p><b>变式由母题机制生成</b>（F-WRG-03 的原文口径）：这道错题挂在哪个母题下，
 * 就给这个孩子生成那个母题的三层变式（换数字 / 换情境 / 逆向）；
 * 没有母题时降级为「同知识点同题型的换数字变式」，此时<b>不产生新题</b>，
 * 只把已有的候选题作为降级变式返回。</p>
 *
 * <p><b>为什么单独一个服务</b>：变式生成要调用母题域的生成能力，
 * 而母题域的作答链路又依赖错题池（本服务）。
 * 若把生成逻辑放进错题池服务实现里，就会形成
 * 「错题池服务 → 母题服务 → 错题池服务」的<b>循环依赖</b>
 * （Spring Boot 3 默认不允许循环引用）。拆成独立服务后依赖是单向的：
 * 变式服务 → 母题服务 → 错题池服务。</p>
 *
 * @author jeecg-study
 */
public interface StudyErrorVariantService {

    /**
     * 为一道错题生成变式（幂等：同一孩子 + 母题 + 层级只生成一次）。
     *
     * @param errorCode 错题业务ID
     * @return 变式生成结果
     */
    ErrorVariantGenerateVO generateVariants(String errorCode);
}
