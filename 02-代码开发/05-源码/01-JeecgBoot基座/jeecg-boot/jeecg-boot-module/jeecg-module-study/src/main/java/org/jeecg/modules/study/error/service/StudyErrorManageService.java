package org.jeecg.modules.study.error.service;

import java.util.List;

import org.jeecg.modules.study.error.vo.ErrorPoolReportVO;
import org.jeecg.modules.study.error.vo.ErrorVariantListVO;
import org.jeecg.modules.study.error.vo.ReviewCompleteCommand;
import org.jeecg.modules.study.error.vo.ReviewCompleteVO;
import org.jeecg.modules.study.error.vo.ReviewQueueItemVO;
import org.jeecg.modules.study.error.vo.StudyErrorReportCommand;

/**
 * 错题池扩展操作：变式、错因维护与复习完成。
 *
 * <p>与 {@link StudyErrorPoolService} 分开的原因是：入池 / 查询是<b>上游契约</b>
 * （V1–V4 各模块都依赖，签名不可变），而变式与复习完成是错题域自己的编排。</p>
 *
 * @author jeecg-study
 */
public interface StudyErrorManageService {

    /**
     * 错题入池并返回完整结果（错题本 / 拍照录入入口）。
     *
     * <p>与 {@link StudyErrorPoolService#report} 同一条写入路径，
     * 区别只在于返回体带「下次复习日期」与「是否命中幂等」。</p>
     *
     * @param command 入池入参
     * @return 入池结果
     */
    ErrorPoolReportVO reportToPool(StudyErrorReportCommand command);

    /**
     * 查某道错题的变式清单。
     *
     * <p>有母题 → 取母题变式（带层级标识）；无母题 → 降级为「同知识点同题型的换数字变式」；
     * 确实没有 → 标记「暂无可用变式」，但错题<b>仍然留在队列</b>里（TC-02-13…15）。</p>
     *
     * @param errorCode 错题业务ID
     * @return 变式清单
     */
    ErrorVariantListVO listVariants(String errorCode);

    /**
     * 事后修改错因（TC-02-06）。
     *
     * @param errorCode  错题业务ID
     * @param errorCause 新错因（五维之一）
     */
    void updateErrorCause(String errorCode, String errorCause);

    /**
     * 完成一项复习：推进排期、判定是否移出队列。
     *
     * <p>变式未全对时<b>不计入「做对」</b>，连续答对次数不增加（TC-02-14）。</p>
     *
     * @param command 完成复习入参
     * @return 完成结果
     */
    ReviewCompleteVO completeReview(ReviewCompleteCommand command);

    /**
     * 未来 N 天排期预览。
     *
     * @param childId 孩子 ID
     * @param days    天数（默认 7）
     * @return 排期预览
     */
    List<ReviewQueueItemVO> upcoming(String childId, int days);
}
