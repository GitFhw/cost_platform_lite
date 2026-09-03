package com.ruoyi.system.mapper.cost;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.system.domain.cost.CostVariableGroup;

import java.util.List;

/**
 * 变量分组Mapper接口
 *
 * @author HwFan
 */
public interface CostVariableGroupMapper extends BaseMapper<CostVariableGroup> {
    /**
     * 查询变量分组列表
     */
    List<CostVariableGroup> selectVariableGroupList(CostVariableGroup group);

    /**
     * 查询变量分组选择框
     */
    List<CostVariableGroup> selectVariableGroupOptions(CostVariableGroup group);
}
