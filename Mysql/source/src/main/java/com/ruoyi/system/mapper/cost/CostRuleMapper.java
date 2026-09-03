package com.ruoyi.system.mapper.cost;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.system.domain.cost.CostRule;
import com.ruoyi.system.domain.cost.CostRuleCondition;
import com.ruoyi.system.domain.cost.CostRuleTier;
import com.ruoyi.system.domain.vo.CostRuleGovernanceCheckVo;

import java.util.List;
import java.util.Map;

/**
 * 规则中心 Mapper
 *
 * @author HwFan
 */
public interface CostRuleMapper extends BaseMapper<CostRule> {
    /**
     * 查询规则列表
     */
    List<CostRule> selectRuleList(CostRule rule);

    /**
     * 查询规则统计
     */
    Map<String, Object> selectRuleStats(CostRule rule);

    /**
     * 查询规则治理预检查
     */
    CostRuleGovernanceCheckVo selectRuleGovernanceCheck(Long ruleId);

    /**
     * 查询规则条件
     */
    List<CostRuleCondition> selectConditionsByRuleId(Long ruleId);

    /**
     * 查询规则阶梯
     */
    List<CostRuleTier> selectTiersByRuleId(Long ruleId);

    /**
     * 批量删除规则条件
     */
    int deleteConditionsByRuleIds(Long[] ruleIds);

    /**
     * 批量删除规则阶梯
     */
    int deleteTiersByRuleIds(Long[] ruleIds);
}
