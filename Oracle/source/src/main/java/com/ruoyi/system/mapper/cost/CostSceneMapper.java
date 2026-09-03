package com.ruoyi.system.mapper.cost;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.system.domain.cost.CostScene;
import com.ruoyi.system.domain.vo.CostSceneGovernanceCheckVo;
import com.ruoyi.system.domain.vo.CostSceneRecentTaskVo;

import java.util.List;
import java.util.Map;

/**
 * 场景中心Mapper接口
 *
 * @author HwFan
 */
public interface CostSceneMapper extends BaseMapper<CostScene> {
    /**
     * 查询场景列表
     *
     * @param scene 场景查询对象
     *
     * @return 场景集合
     */
    public List<CostScene> selectSceneList(CostScene scene);

    /**
     * 查询场景选择框
     *
     * @param scene 场景查询对象
     *
     * @return 场景集合
     */
    public List<CostScene> selectSceneOptions(CostScene scene);

    /**
     * 查询场景统计卡片
     *
     * @param scene 场景查询对象
     *
     * @return 统计结果
     */
    public Map<String, Object> selectSceneStats(CostScene scene);

    /**
     * 查询场景治理预检查结果
     *
     * @param sceneId 场景主键
     *
     * @return 结果
     */
    public CostSceneGovernanceCheckVo selectSceneGovernanceCheck(Long sceneId);

    /**
     * Query recent calculation tasks in a scene.
     *
     * @param sceneId scene id
     *
     * @return recent tasks
     */
    public List<CostSceneRecentTaskVo> selectRecentSceneTasks(Long sceneId);
}
