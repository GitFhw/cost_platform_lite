package com.ruoyi.system.service.cost;

import com.ruoyi.system.domain.cost.CostScene;
import com.ruoyi.system.domain.cost.bo.CostSceneCopyBo;
import com.ruoyi.system.domain.vo.CostSceneGovernanceCheckVo;

import java.util.List;
import java.util.Map;

/**
 * 场景中心服务接口
 *
 * @author HwFan
 */
public interface ICostSceneService {
    /**
     * 查询场景列表
     *
     * @param scene 场景查询对象
     *
     * @return 场景集合
     */
    public List<CostScene> selectSceneList(CostScene scene);

    /**
     * 查询场景详情
     *
     * @param sceneId 场景主键
     *
     * @return 场景对象
     */
    public CostScene selectSceneById(Long sceneId);

    /**
     * 查询场景选择框
     *
     * @param scene 场景查询对象
     *
     * @return 场景集合
     */
    public List<CostScene> selectSceneOptions(CostScene scene);

    /**
     * 查询场景统计
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
     * 校验场景编码是否唯一
     *
     * @param scene 场景对象
     *
     * @return 结果
     */
    public boolean checkSceneCodeUnique(CostScene scene);

    /**
     * 新增场景
     *
     * @param scene 场景对象
     *
     * @return 结果
     */
    public int insertScene(CostScene scene);

    /**
     * 复制场景及可选配置
     *
     * @param request 复制请求
     *
     * @return 新场景
     */
    public CostScene copyScene(CostSceneCopyBo request);

    /**
     * 修改场景
     *
     * @param scene 场景对象
     *
     * @return 结果
     */
    public int updateScene(CostScene scene);

    /**
     * 批量删除场景
     *
     * @param sceneIds 场景主键数组
     *
     * @return 结果
     */
    public int deleteSceneByIds(Long[] sceneIds);
}
