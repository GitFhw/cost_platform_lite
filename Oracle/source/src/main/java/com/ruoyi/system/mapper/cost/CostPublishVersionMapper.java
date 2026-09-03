package com.ruoyi.system.mapper.cost;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.system.domain.cost.CostPublishSnapshot;
import com.ruoyi.system.domain.cost.CostPublishVersion;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 发布版本 Mapper
 *
 * @author HwFan
 */
public interface CostPublishVersionMapper extends BaseMapper<CostPublishVersion> {
    /**
     * 查询版本台账
     */
    List<CostPublishVersion> selectPublishVersionList(CostPublishVersion query);

    /**
     * 查询发布统计
     */
    Map<String, Object> selectPublishStats(CostPublishVersion query);

    /**
     * 查询版本详情基础信息
     */
    CostPublishVersion selectPublishVersionDetail(Long versionId);

    /**
     * 查询场景最近一个版本
     */
    CostPublishVersion selectLatestVersionByScene(Long sceneId);

    /**
     * 查询场景当前生效版本
     */
    CostPublishVersion selectActiveVersionByScene(Long sceneId);

    /**
     * 查询指定版本的上一个版本
     */
    CostPublishVersion selectPreviousVersion(@Param("sceneId") Long sceneId, @Param("versionId") Long versionId);

    /**
     * 查询本月版本序号
     */
    Long selectMonthlyVersionSequence(@Param("sceneId") Long sceneId, @Param("versionPrefix") String versionPrefix);

    /**
     * 查询没有启用规则的费用
     */
    List<Map<String, Object>> selectEnabledFeesWithoutRule(Long sceneId);

    /**
     * 查询缺少阶梯明细的阶梯规则
     */
    List<Map<String, Object>> selectTierRulesWithoutTier(Long sceneId);

    /**
     * 查询发布快照源中的规则条件
     */
    List<Map<String, Object>> selectRuleConditionsForPublish(Long sceneId);

    /**
     * 查询发布快照源中的规则阶梯
     */
    List<Map<String, Object>> selectRuleTiersForPublish(Long sceneId);

    /**
     * 查询版本快照列表
     */
    List<CostPublishSnapshot> selectSnapshotList(@Param("versionId") Long versionId, @Param("snapshotType") String snapshotType);
}
