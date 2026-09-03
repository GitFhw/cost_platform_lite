package com.ruoyi.system.service.cost.execution.node;

import com.ruoyi.system.service.cost.execution.model.PricingResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import static com.ruoyi.system.service.cost.execution.CostExecutionConstants.*;

@Component
public class AmountNode implements PricingNode {

    @Override
    public void apply(PricingContext context, PricingSupport support) {
        PricingResult result = context.pricingResult;
        String ruleType = context.rule.ruleType;
        BigDecimal amountValue = result.amountValue;
        if (amountValue == null) {
            if (RULE_TYPE_FIXED_RATE.equals(ruleType) || RULE_TYPE_TIER_RATE.equals(ruleType)) {
                BigDecimal quantityValue = result.quantityValue == null ? BigDecimal.ONE : result.quantityValue;
                BigDecimal unitPrice = support.defaultZero(result.unitPrice);
                amountValue = unitPrice.multiply(quantityValue).setScale(2, RoundingMode.HALF_UP);
            } else if (RULE_TYPE_FIXED_AMOUNT.equals(ruleType) || RULE_TYPE_FORMULA.equals(ruleType)) {
                amountValue = support.defaultZero(result.unitPrice).setScale(2, RoundingMode.HALF_UP);
            }
        }
        result.amountValue = amountValue;
        if (result.unitPrice == null && amountValue != null) {
            result.unitPrice = amountValue.setScale(6, RoundingMode.HALF_UP);
        }
        Map<String, Object> explain = context.pricingExplain();
        explain.put("unitPrice", result.unitPrice);
        explain.put("amountValue", result.amountValue);
    }
}
