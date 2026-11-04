package org.jeecg.modules.study.parent.service;

import org.jeecg.modules.study.parent.vo.NotificationPreferenceRequest;
import org.jeecg.modules.study.parent.vo.NotificationPreferenceVO;

/**
 * 家长提醒推送偏好服务（F-PAR-04）。
 *
 * <p><b>归属</b>：偏好属于<b>家长账号</b>，维度是「家长 + 孩子」。
 * {@code parentUserId} 永远取自会话，不接受前端传值。</p>
 *
 * <p><b>数据范围</b>：指定孩子时必须与关系表求交集，越权一律 4010。</p>
 *
 * @author jeecg-study
 */
public interface StudyNotificationPreferenceService {

    /**
     * 查提醒偏好。
     *
     * @param childCode 孩子业务ID，可空；为空表示「全部孩子」这一档
     * @return 提醒偏好
     */
    NotificationPreferenceVO preference(String childCode);

    /**
     * 更新提醒偏好（幂等：同一「家长 + 孩子」只有一行，重复提交只更新）。
     *
     * @param request 更新入参
     * @return 更新后的提醒偏好
     */
    NotificationPreferenceVO update(NotificationPreferenceRequest request);
}
