package com.ruoyi.system.service.cost;

import com.ruoyi.system.domain.cost.CostVariableGroup;

import java.util.List;

/**
 * 变量分组服务接口
 *
 * @author HwFan
 */
public interface ICostVariableGroupService {
    /**
     * 查询变量分组列表
     */
    List<CostVariableGroup> selectVariableGroupList(CostVariableGroup group);

    /**
     * 查询变量分组详情
     */
    CostVariableGroup selectVariableGroupById(Long groupId);

    /**
     * 查询变量分组选择框
     */
    List<CostVariableGroup> selectVariableGroupOptions(CostVariableGroup group);

    /**
     * 校验分组编码是否唯一
     */
    boolean checkGroupCodeUnique(CostVariableGroup group);

    /**
     * 新增变量分组
     */
    int insertVariableGroup(CostVariableGroup group);

    /**
     * 修改变量分组
     */
    int updateVariableGroup(CostVariableGroup group);

    /**
     * 批量删除变量分组
     */
    int deleteVariableGroupByIds(Long[] groupIds);
}
