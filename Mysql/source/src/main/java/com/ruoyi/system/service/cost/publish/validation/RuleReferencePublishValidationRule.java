package com.ruoyi.system.service.cost.publish.validation;

import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.vo.CostPublishCheckItemVo;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class RuleReferencePublishValidationRule implements PublishValidationRule {

    @Override
    public void validate(PublishValidationContext context, List<CostPublishCheckItemVo> items) {
        List<String> missingFeeRefs = new ArrayList<>();
        List<String> missingQuantityVariables = new ArrayList<>();
        List<String> missingConditionVariables = new ArrayList<>();
        Map<String, Map<String, Object>> variablesByCode = context.getVariablesByCode();
        Map<String, Map<String, Object>> feesByCode = context.getFeesByCode();

        for (Map<String, Object> rule : context.getRulesByCode().values()) {
            String ruleCode = PublishValidationSupport.stringValue(rule.get("ruleCode"));
            String ruleName = PublishValidationSupport.stringValue(rule.get("ruleName"));
            String feeCode = PublishValidationSupport.stringValue(rule.get("feeCode"));
            String ruleLabel = PublishValidationSupport.assetLabel(rule, "ruleName", "ruleCode");
            if (StringUtils.isEmpty(feeCode) || !feesByCode.containsKey(feeCode)) {
                missingFeeRefs.add(ruleLabel + " -> " + PublishValidationSupport.firstNonBlank(feeCode, "UNKNOWN_FEE"));
            }
            String quantityVariableCode = PublishValidationSupport.stringValue(rule.get("quantityVariableCode"));
            if (StringUtils.isNotEmpty(quantityVariableCode) && !variablesByCode.containsKey(quantityVariableCode)) {
                missingQuantityVariables.add(ruleLabel + " -> " + quantityVariableCode);
            }
            for (Map<String, Object> condition : context.getRuleConditionsByRuleCode().getOrDefault(ruleCode, List.of())) {
                String variableCode = PublishValidationSupport.stringValue(condition.get("variableCode"));
                if (StringUtils.isEmpty(variableCode)) {
                    continue;
                }
                if (!variablesByCode.containsKey(variableCode)) {
                    String conditionLabel = PublishValidationSupport.firstNonBlank(
                            PublishValidationSupport.stringValue(condition.get("displayName")), variableCode);
                    missingConditionVariables.add(ruleName + "(" + ruleCode + ")" + " -> " + conditionLabel + "(" + variableCode + ")");
                }
            }
        }

        if (!missingFeeRefs.isEmpty()) {
            items.add(PublishValidationSupport.checkItem("BLOCK", "RULE_FEE_MISSING", "规则引用完整性",
                    "以下规则引用的费用不存在或未启用：" + String.join("、", missingFeeRefs)));
        }
        if (!missingQuantityVariables.isEmpty()) {
            items.add(PublishValidationSupport.checkItem("BLOCK", "RULE_QUANTITY_VARIABLE_MISSING", "规则引用完整性",
                    "以下规则的计量变量不存在或未启用：" + String.join("、", missingQuantityVariables)));
        }
        if (!missingConditionVariables.isEmpty()) {
            items.add(PublishValidationSupport.checkItem("BLOCK", "RULE_CONDITION_VARIABLE_MISSING", "规则引用完整性",
                    "以下规则条件引用的变量不存在或未启用：" + String.join("、", missingConditionVariables)));
        }
        if (missingFeeRefs.isEmpty() && missingQuantityVariables.isEmpty() && missingConditionVariables.isEmpty()) {
            items.add(PublishValidationSupport.checkItem("PASS", "RULE_REFERENCE_OK", "规则引用完整性",
                    "规则引用的费用与变量均在当前场景快照中。"));
        }
    }
}
