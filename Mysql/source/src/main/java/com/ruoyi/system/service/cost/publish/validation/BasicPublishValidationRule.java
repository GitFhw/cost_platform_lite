package com.ruoyi.system.service.cost.publish.validation;

import com.ruoyi.system.domain.vo.CostPublishCheckItemVo;
import com.ruoyi.system.mapper.cost.CostPublishVersionMapper;
import org.springframework.stereotype.Component;

import static com.ruoyi.system.service.cost.constant.CostDomainConstants.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class BasicPublishValidationRule implements PublishValidationRule {
    private final CostPublishVersionMapper publishVersionMapper;

    public BasicPublishValidationRule(CostPublishVersionMapper publishVersionMapper) {
        this.publishVersionMapper = publishVersionMapper;
    }

    @Override
    public void validate(PublishValidationContext context, List<CostPublishCheckItemVo> items) {
        if (!STATUS_ENABLED.equals(String.valueOf(context.getSceneSnapshot().get("status")))) {
            items.add(PublishValidationSupport.checkItem("BLOCK", "SCENE_DISABLED", "场景状态检查", "当前场景已停用，不能发布新版本。"));
        } else {
            items.add(PublishValidationSupport.checkItem("PASS", "SCENE_STATUS_OK", "场景状态检查", "当前场景状态正常，可进入发布治理。"));
        }
        if (context.getFeesByCode().isEmpty()) {
            items.add(PublishValidationSupport.checkItem("BLOCK", "NO_FEE", "费用完整性", "当前场景下暂无启用费用，无法形成可运行版本。"));
        }
        if (context.getVariablesByCode().isEmpty()) {
            items.add(PublishValidationSupport.checkItem("BLOCK", "NO_VARIABLE", "变量完整性", "当前场景下暂无启用变量，无法形成稳定的规则运行口径。"));
        }
        if (context.getRulesByCode().isEmpty()) {
            items.add(PublishValidationSupport.checkItem("BLOCK", "NO_RULE", "规则完整性", "当前场景下暂无启用规则，无法发布可运行版本。"));
        }

        appendFeeRuleCoverage(context, items);
        appendTierCoverage(context, items);
    }

    private void appendFeeRuleCoverage(PublishValidationContext context, List<CostPublishCheckItemVo> items) {
        List<Map<String, Object>> feesWithoutRule = publishVersionMapper.selectEnabledFeesWithoutRule(context.getSceneId());
        if (feesWithoutRule.isEmpty()) {
            items.add(PublishValidationSupport.checkItem("PASS", "FEE_RULE_COVERED", "费用规则覆盖", "所有启用费用都已挂载至少一条启用规则。"));
            return;
        }
        String joined = feesWithoutRule.stream()
                .map(item -> String.format("%1$s(%2$s)", item.get("feeName"), item.get("feeCode")))
                .collect(Collectors.joining("、"));
        items.add(PublishValidationSupport.checkItem("BLOCK", "FEE_RULE_MISSING", "费用规则覆盖", "以下费用尚未挂载启用规则：" + joined));
    }

    private void appendTierCoverage(PublishValidationContext context, List<CostPublishCheckItemVo> items) {
        List<Map<String, Object>> tierRuleWithoutTier = publishVersionMapper.selectTierRulesWithoutTier(context.getSceneId());
        if (tierRuleWithoutTier.isEmpty()) {
            items.add(PublishValidationSupport.checkItem("PASS", "RULE_TIER_OK", "阶梯结构检查", "当前场景的阶梯规则都已具备完整阶梯明细。"));
            return;
        }
        String joined = tierRuleWithoutTier.stream()
                .map(item -> String.format("%1$s(%2$s)", item.get("ruleName"), item.get("ruleCode")))
                .collect(Collectors.joining("、"));
        items.add(PublishValidationSupport.checkItem("BLOCK", "RULE_TIER_MISSING", "阶梯结构检查", "以下阶梯规则缺少阶梯明细：" + joined));
    }
}
