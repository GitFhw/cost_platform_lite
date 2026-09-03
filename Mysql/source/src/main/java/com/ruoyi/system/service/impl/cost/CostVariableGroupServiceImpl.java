package com.ruoyi.system.service.impl.cost;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ruoyi.common.constant.UserConstants;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.cost.CostVariableGroup;
import com.ruoyi.system.mapper.cost.CostVariableGroupMapper;
import com.ruoyi.system.mapper.cost.CostVariableMapper;
import com.ruoyi.system.service.cost.ICostVariableGroupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 变量分组服务实现
 *
 * @author HwFan
 */
@Service
public class CostVariableGroupServiceImpl implements ICostVariableGroupService {
    @Autowired
    private CostVariableGroupMapper groupMapper;

    @Autowired
    private CostVariableMapper variableMapper;

    /**
     * 查询变量分组列表
     */
    @Override
    public List<CostVariableGroup> selectVariableGroupList(CostVariableGroup group) {
        return groupMapper.selectVariableGroupList(group);
    }

    /**
     * 查询变量分组详情
     */
    @Override
    public CostVariableGroup selectVariableGroupById(Long groupId) {
        return groupMapper.selectById(groupId);
    }

    /**
     * 查询变量分组选择框
     */
    @Override
    public List<CostVariableGroup> selectVariableGroupOptions(CostVariableGroup group) {
        return groupMapper.selectVariableGroupOptions(group);
    }

    /**
     * 校验分组编码唯一性（同一场景内唯一）
     */
    @Override
    public boolean checkGroupCodeUnique(CostVariableGroup group) {
        Long groupId = StringUtils.isNull(group.getGroupId()) ? -1L : group.getGroupId();
        Long count = groupMapper.selectCount(Wrappers.<CostVariableGroup>lambdaQuery()
                .eq(CostVariableGroup::getSceneId, group.getSceneId())
                .eq(CostVariableGroup::getGroupCode, group.getGroupCode())
                .ne(groupId.longValue() != -1L, CostVariableGroup::getGroupId, groupId));
        return count != null && count > 0 ? UserConstants.NOT_UNIQUE : UserConstants.UNIQUE;
    }

    /**
     * 新增变量分组
     */
    @Override
    public int insertVariableGroup(CostVariableGroup group) {
        if (group.getSortNo() == null) {
            group.setSortNo(10);
        }
        return groupMapper.insert(group);
    }

    /**
     * 修改变量分组
     */
    @Override
    public int updateVariableGroup(CostVariableGroup group) {
        return groupMapper.updateById(group);
    }

    /**
     * 批量删除变量分组
     */
    @Override
    public int deleteVariableGroupByIds(Long[] groupIds) {
        for (Long groupId : groupIds) {
            Long variableCount = variableMapper.selectCount(Wrappers.lambdaQuery(com.ruoyi.system.domain.cost.CostVariable.class)
                    .eq(com.ruoyi.system.domain.cost.CostVariable::getGroupId, groupId));
            if (variableCount != null && variableCount > 0) {
                throw new ServiceException("当前分组下仍存在变量，请先迁移或删除变量后再删除分组");
            }
        }
        return groupMapper.deleteBatchIds(Arrays.asList(groupIds));
    }
}
