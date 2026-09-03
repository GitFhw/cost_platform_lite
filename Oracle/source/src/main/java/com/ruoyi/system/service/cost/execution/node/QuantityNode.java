package com.ruoyi.system.service.cost.execution.node;

import com.ruoyi.system.service.cost.execution.model.PricingResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Component
public class QuantityNode implements PricingNode {

    @Override
    public void apply(PricingContext context, PricingSupport support) {
        PricingResult result = context.pricingResult;
        BigDecimal quantityValue = support.toBigDecimal(context.variableValues.get(context.rule.quantityVariableCode));
        if (quantityValue == null) {
            quantityValue = BigDecimal.ONE;
        }
        quantityValue = quantityValue.setScale(4, RoundingMode.HALF_UP);
        result.quantityValue = quantityValue;
        Map<String, Object> explain = context.pricingExplain();
        explain.put("ruleCode", context.rule.ruleCode);
        explain.put("ruleType", context.rule.ruleType);
        explain.put("quantityVariableCode", context.rule.quantityVariableCode);
        explain.put("quantityValue", quantityValue);
        explain.put("pricingMode", context.rule.pricingMode);
        explain.put("matchedGroupNo", context.rule.matchedGroupNo);
    }
}
