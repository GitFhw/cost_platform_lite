package com.ruoyi.system.service.cost;

import com.ruoyi.system.domain.cost.CostOpenApp;
import com.ruoyi.system.domain.cost.vo.CostOpenAppSession;

import java.util.List;
import java.util.Set;

/**
 * 开放应用服务接口
 */
public interface ICostOpenAppService {
    /**
     * 查询开放应用列表。
     */
    List<CostOpenApp> selectOpenAppList(CostOpenApp query);

    /**
     * 查询开放应用详情。
     */
    CostOpenApp selectOpenAppById(Long appId);

    /**
     * 校验开放应用编码是否唯一。
     */
    boolean checkOpenAppCodeUnique(CostOpenApp app);

    /**
     * 新增开放应用，并返回一次性明文密钥。
     */
    CostOpenApp insertOpenApp(CostOpenApp app);

    /**
     * 修改开放应用。
     */
    int updateOpenApp(CostOpenApp app);

    /**
     * 重置开放应用密钥，并返回一次性明文密钥。
     */
    CostOpenApp resetOpenAppSecret(Long appId, String updateBy);

    /**
     * 批量删除开放应用。
     */
    int deleteOpenAppByIds(Long[] appIds);

    /**
     * 认证开放应用身份。
     */
    CostOpenApp authenticateApp(String appCode, String appSecret);

    /**
     * 构建开放应用会话。
     */
    CostOpenAppSession buildSession(CostOpenApp app);

    /**
     * 是否可访问指定场景。
     */
    boolean canAccessScene(CostOpenAppSession session, Long sceneId);

    /**
     * 断言当前会话可访问指定场景。
     */
    void assertCanAccessScene(CostOpenAppSession session, Long sceneId);

    /**
     * 是否允许访问草稿快照。
     */
    boolean allowDraftSnapshot(CostOpenAppSession session);

    /**
     * 解析授权场景主键集合。
     */
    Set<Long> resolveAuthorizedSceneIds(CostOpenAppSession session);
}
