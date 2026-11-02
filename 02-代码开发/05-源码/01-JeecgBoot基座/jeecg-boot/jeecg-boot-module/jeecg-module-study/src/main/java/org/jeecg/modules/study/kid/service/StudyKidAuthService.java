package org.jeecg.modules.study.kid.service;

import java.util.List;

import org.jeecg.modules.study.kid.vo.KidCandidateVO;
import org.jeecg.modules.study.kid.vo.KidLoginVO;
import org.jeecg.modules.study.kid.vo.KidProfileVO;

/**
 * 孩子端两步登录（T-V0-15）。
 *
 * <p>① 家庭码 → 候选孩子列表（**只暴露 id / nickname / avatar**）；
 * ② 孩子 ID + PIN → 签发与家长同构的 JWT。</p>
 *
 * @author jeecg-study
 */
public interface StudyKidAuthService {

    /**
     * 按家庭码返回候选孩子列表。
     *
     * @param familyCode 家庭码
     * @return 候选列表
     */
    List<KidCandidateVO> lookup(String familyCode);

    /**
     * 孩子 PIN 登录。
     *
     * @param childId 孩子 ID
     * @param pin     PIN
     * @return 登录结果（含 token）
     */
    KidLoginVO login(String childId, String pin);

    /**
     * 退出登录（销毁 Redis 里的 token）。
     *
     * @param token 当前 token
     */
    void logout(String token);

    /**
     * 当前孩子信息。
     *
     * @return 孩子信息
     */
    KidProfileVO me();
}
