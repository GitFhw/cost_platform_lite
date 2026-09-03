package com.ruoyi.system.service.cost;

import com.ruoyi.system.domain.cost.CostPublishVersion;
import com.ruoyi.system.domain.cost.bo.CostPublishCreateBo;

import java.util.List;
import java.util.Map;

/**
 * 发布中心服务接口
 *
 * @author HwFan
 */
public interface ICostPublishService {
    /**
     * 查询发布统计
     */
    Map<String, Object> selectPublishStats(CostPublishVersion query);

    /**
     * 查询发布前检查
     */
    Map<String, Object> selectPublishPrecheck(Long sceneId);

    /**
     * 查询版本台账
     */
    List<CostPublishVersion> selectPublishVersionList(CostPublishVersion query);

    /**
     * 查询版本详情
     */
    Map<String, Object> selectPublishVersionDetail(Long versionId, String feeCode);

    /**
     * 查询版本差异
     */
    Map<String, Object> selectPublishDiff(Long fromVersionId, Long toVersionId, String feeCode);

    /**
     * 生成发布版本
     */
    int publishScene(CostPublishCreateBo bo);

    /**
     * 设为生效
     */
    int activateVersion(Long versionId);

    /**
     * 回滚到历史版本
     */
    int rollbackVersion(Long versionId);
}
