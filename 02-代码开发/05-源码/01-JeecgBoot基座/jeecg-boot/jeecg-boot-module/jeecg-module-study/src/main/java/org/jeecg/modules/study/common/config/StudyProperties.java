package org.jeecg.modules.study.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * jeecg-study 业务配置。
 *
 * <p>家庭码用 HMAC-SHA256(pepper, code) 存储，pepper 必须由部署方配置，
 * 不落在代码里、也不落在库里。</p>
 *
 * @author jeecg-study
 */
@Component
public class StudyProperties {

    /** 家庭码 pepper（服务端机密） */
    @Value("${jeecg.study.family-code-pepper:}")
    private String familyCodePepper;

    /** 护眼默认提醒间隔（分钟） */
    @Value("${jeecg.study.eye-interval-min:15}")
    private int eyeIntervalMin = 15;

    public String getFamilyCodePepper() {
        return familyCodePepper;
    }

    public void setFamilyCodePepper(String familyCodePepper) {
        this.familyCodePepper = familyCodePepper;
    }

    public int getEyeIntervalMin() {
        return eyeIntervalMin;
    }

    public void setEyeIntervalMin(int eyeIntervalMin) {
        this.eyeIntervalMin = eyeIntervalMin;
    }
}
