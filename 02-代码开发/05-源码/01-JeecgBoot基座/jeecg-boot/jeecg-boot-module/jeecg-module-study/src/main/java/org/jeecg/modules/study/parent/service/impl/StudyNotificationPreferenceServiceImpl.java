package org.jeecg.modules.study.parent.service.impl;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;

import lombok.RequiredArgsConstructor;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.study.parent.entity.StudyNotificationPreference;
import org.jeecg.modules.study.parent.mapper.ParentNotificationPreferenceMapper;
import org.jeecg.modules.study.parent.service.StudyNotificationPreferenceService;
import org.jeecg.modules.study.parent.vo.NotificationPreferenceRequest;
import org.jeecg.modules.study.parent.vo.NotificationPreferenceVO;
import org.jeecg.modules.study.scope.service.ChildScopeService;

/**
 * 家长提醒推送偏好服务实现（F-PAR-04）。
 *
 * <p><b>隔离口径</b>：家长身份来自会话（{@code parentUserId}）；
 * 指定孩子时先过 {@link ChildScopeService#assertChildCodeAccess(String)}
 * （关系表求交集），越权一律 4010（不用 404，避免资源探测）。</p>
 *
 * <p><b>不传字段 = 不修改</b>：只覆盖请求里带了值的字段。</p>
 *
 * @author jeecg-study
 */
@Service
@RequiredArgsConstructor
public class StudyNotificationPreferenceServiceImpl implements StudyNotificationPreferenceService {

    /** 「全部孩子」这一档的 childCode 占位（空串） */
    private static final String CHILD_CODE_ALL = "";

    /** 默认复习提醒时间 */
    private static final String DEFAULT_REVIEW_TIME = "19:00:00";

    /** 默认护眼提醒时间 */
    private static final String DEFAULT_EYE_TIME = "19:30:00";

    /** 默认推送渠道 */
    private static final String DEFAULT_CHANNEL = "APP";

    /** 允许的推送渠道 */
    private static final List<String> CHANNELS = Arrays.asList("APP", "WECHAT", "SMS");

    private final ParentNotificationPreferenceMapper preferenceMapper;
    private final ChildScopeService scopeService;

    @Override
    public NotificationPreferenceVO preference(String childCode) {
        String parentUserId = scopeService.currentUserId();
        String code = normalizeChildCode(childCode);
        assertChildScope(code);

        StudyNotificationPreference row = preferenceMapper.selectByParentAndChild(parentUserId, code);
        if (row == null) {
            return defaults(code);
        }
        return toVO(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NotificationPreferenceVO update(NotificationPreferenceRequest request) {
        if (request == null) {
            throw new JeecgBootException("提醒偏好参数不能为空", 400);
        }
        String parentUserId = scopeService.currentUserId();
        String code = normalizeChildCode(request.getChildCode());
        assertChildScope(code);

        StudyNotificationPreference row = preferenceMapper.selectByParentAndChild(parentUserId, code);
        boolean created = row == null;
        if (created) {
            row = new StudyNotificationPreference();
            row.setId(IdWorker.get32UUID());
            row.setParentUserId(parentUserId);
            row.setChildCode(code);
            row.setReviewRemindEnabled(1);
            row.setReviewRemindTime(DEFAULT_REVIEW_TIME);
            row.setEyeRemindEnabled(1);
            row.setEyeRemindTime(DEFAULT_EYE_TIME);
            row.setPushChannel(DEFAULT_CHANNEL);
            row.setDelFlag(0);
        }

        if (request.getReviewRemindEnabled() != null) {
            row.setReviewRemindEnabled(request.getReviewRemindEnabled() ? 1 : 0);
        }
        if (request.getReviewRemindTime() != null) {
            row.setReviewRemindTime(normalizeTime(request.getReviewRemindTime(), DEFAULT_REVIEW_TIME));
        }
        if (request.getEyeRemindEnabled() != null) {
            row.setEyeRemindEnabled(request.getEyeRemindEnabled() ? 1 : 0);
        }
        if (request.getEyeRemindTime() != null) {
            row.setEyeRemindTime(normalizeTime(request.getEyeRemindTime(), DEFAULT_EYE_TIME));
        }
        if (request.getPushChannel() != null) {
            row.setPushChannel(normalizeChannel(request.getPushChannel()));
        }

        if (created) {
            preferenceMapper.insert(row);
        } else {
            preferenceMapper.updateById(row);
        }
        return toVO(row);
    }

    // ------------------------------------------------------------------ 内部

    /**
     * 孩子维度归一：空串 = 全部孩子这一档。
     */
    private String normalizeChildCode(String childCode) {
        return childCode == null ? CHILD_CODE_ALL : childCode.trim();
    }

    /**
     * 指定孩子时必须过关系表；「全部孩子」这一档不校验（它不指向具体孩子）。
     */
    private void assertChildScope(String childCode) {
        if (childCode == null || childCode.isEmpty()) {
            return;
        }
        scopeService.assertChildCodeAccess(childCode);
    }

    /**
     * 时间归一为 {@code HH:mm:ss}。
     */
    private String normalizeTime(String value, String fallback) {
        String text = value.trim();
        if (text.matches("\\d{1,2}:\\d{2}")) {
            String[] parts = text.split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                throw new JeecgBootException("推送时间格式应为 HH:mm", 400);
            }
            return String.format("%02d:%02d:00", hour, minute);
        }
        if (text.matches("\\d{1,2}:\\d{2}:\\d{2}")) {
            return text;
        }
        throw new JeecgBootException("推送时间格式应为 HH:mm", 400);
    }

    private String normalizeChannel(String value) {
        String text = value.trim().toUpperCase();
        if (!CHANNELS.contains(text)) {
            throw new JeecgBootException("推送渠道只能是 APP / WECHAT / SMS", 400);
        }
        return text;
    }

    private NotificationPreferenceVO defaults(String childCode) {
        NotificationPreferenceVO vo = new NotificationPreferenceVO();
        vo.setChildCode(childCode);
        vo.setReviewRemindEnabled(Boolean.TRUE);
        vo.setReviewRemindTime(DEFAULT_REVIEW_TIME);
        vo.setEyeRemindEnabled(Boolean.TRUE);
        vo.setEyeRemindTime(DEFAULT_EYE_TIME);
        vo.setPushChannel(DEFAULT_CHANNEL);
        return vo;
    }

    private NotificationPreferenceVO toVO(StudyNotificationPreference row) {
        NotificationPreferenceVO vo = new NotificationPreferenceVO();
        vo.setChildCode(row.getChildCode());
        vo.setReviewRemindEnabled(row.getReviewRemindEnabled() == null
                || row.getReviewRemindEnabled() == 1);
        vo.setReviewRemindTime(row.getReviewRemindTime());
        vo.setEyeRemindEnabled(row.getEyeRemindEnabled() == null
                || row.getEyeRemindEnabled() == 1);
        vo.setEyeRemindTime(row.getEyeRemindTime());
        vo.setPushChannel(row.getPushChannel());
        vo.setUpdateTime(row.getUpdateTime() == null ? new Date() : row.getUpdateTime());
        return vo;
    }
}
