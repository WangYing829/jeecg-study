package org.jeecg.modules.study.family.service;

import java.util.List;

import org.jeecg.modules.study.family.vo.ChildCreateRequest;
import org.jeecg.modules.study.family.vo.ChildCreateVO;
import org.jeecg.modules.study.family.vo.ChildUpdateRequest;
import org.jeecg.modules.study.family.vo.ChildVO;
import org.jeecg.modules.study.family.vo.OverviewVO;
import org.jeecg.modules.study.family.vo.RelationVO;

/**
 * 孩子档案 + 家长↔孩子多对多关联（T-V0-15 / T-V0-16）。
 *
 * @author jeecg-study
 */
public interface StudyChildService {

    /**
     * 新增孩子档案，同时创建或绑定 sys_user + 角色 study_kid，并建立创建者的关联关系。
     *
     * @param request 入参
     * @return 创建结果（含一次性明文 PIN）
     */
    ChildCreateVO create(ChildCreateRequest request);

    /**
     * 我关联的孩子列表。
     *
     * @return 孩子列表
     */
    List<ChildVO> listChildren();

    /**
     * 孩子档案详情。
     *
     * @param childCode 孩子业务ID
     * @return 档案
     */
    ChildVO detail(String childCode);

    /**
     * 更新孩子档案（昵称 / 头像 / 年级 / 生日）。
     *
     * @param childCode 孩子业务ID
     * @param request   入参
     */
    void update(String childCode, ChildUpdateRequest request);

    /**
     * 停用孩子档案（仅主家长）。数据保留，不物理删除。
     *
     * @param childCode 孩子业务ID
     */
    void disable(String childCode);

    /**
     * 重置孩子 PIN（仅主家长）；明文仅本次返回。
     *
     * @param childCode 孩子业务ID
     * @return 新的 PIN 明文
     */
    String resetPin(String childCode);

    /**
     * 孩子的关联家长列表。
     *
     * @param childCode 孩子业务ID
     * @return 关系列表
     */
    List<RelationVO> listRelations(String childCode);

    /**
     * 关联家长。
     *
     * @param childCode     孩子业务ID
     * @param parentUserId  家长 sys_user.id
     * @param relationLabel 关系标签
     * @param isPrimary     是否主家长
     */
    void addRelation(String childCode, String parentUserId, String relationLabel, boolean isPrimary);

    /**
     * 解除关联（只能解除自己的关联）。
     *
     * @param childCode    孩子业务ID
     * @param parentUserId 家长 sys_user.id，必须等于当前登录者
     */
    void removeRelation(String childCode, String parentUserId);

    /**
     * 孩子概览（家长视角）。
     *
     * @param childCode 孩子业务ID
     * @return 概览
     */
    OverviewVO overview(String childCode);
}
