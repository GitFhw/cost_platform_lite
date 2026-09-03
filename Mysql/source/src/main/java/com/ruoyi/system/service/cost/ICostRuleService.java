package com.ruoyi.system.service.cost;

import com.ruoyi.system.domain.cost.CostRule;
import com.ruoyi.system.domain.cost.bo.CostRuleCopyBo;
import com.ruoyi.system.domain.cost.bo.CostRuleSaveBo;
import com.ruoyi.system.domain.cost.bo.CostRuleTierPreviewBo;
import com.ruoyi.system.domain.vo.CostRuleConflictVo;
import com.ruoyi.system.domain.vo.CostRuleGovernanceCheckVo;
import com.ruoyi.system.domain.vo.CostRuleTierPreviewVo;

import java.util.List;
import java.util.Map;

/**
 * 规则中心服务接口
 *
 * @author HwFan
 */
public interface ICostRuleService {
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
     * 查询规则详情
     */
    CostRuleSaveBo selectRuleDetail(Long ruleId);

    /**
     * 校验规则编码唯一
     */
    boolean checkRuleCodeUnique(CostRuleSaveBo rule);

    /**
     * 新增规则
     */
    int insertRule(CostRuleSaveBo rule);

    /**
     * 修改规则
     */
    int updateRule(CostRuleSaveBo rule);

    /**
     * 复制规则并改条件值
     */
    int copyRule(CostRuleCopyBo request);

    /**
     * 删除规则
     */
    int deleteRuleByIds(Long[] ruleIds);

    /**
     * 阶梯命中预演
     */
    CostRuleTierPreviewVo previewTierHit(CostRuleTierPreviewBo request);

    /**
     * 规则冲突预览
     */
    List<CostRuleConflictVo> previewRuleConflicts(CostRuleSaveBo request);
}
