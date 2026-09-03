package com.ruoyi.system.service.cost.execution.node;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.service.cost.execution.model.PricingResult;
import com.ruoyi.system.service.impl.cost.CostRunServiceImpl;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import static com.ruoyi.system.service.cost.execution.CostExecutionConstants.*;

@Component
public class UnitPriceNode implements PricingNode {

    @Override
    public void apply(PricingContext context, PricingSupport support) {
        PricingResult result = context.pricingResult;
        Map<String, Object> explain = context.pricingExplain();
        String ruleType = context.rule.ruleType;
        if (RULE_TYPE_FIXED_RATE.equals(ruleType)) {
            BigDecimal unitPrice = support.resolveGroupedPricingValue(context.rule, "rateValue");
            result.unitPrice = support.defaultZero(unitPrice).setScale(6, RoundingMode.HALF_UP);
            explain.put("pricingSource", "FIXED_RATE");
            return;
        }
        if (RULE_TYPE_FIXED_AMOUNT.equals(ruleType)) {
            BigDecimal amountValue = support.resolveGroupedPricingValue(context.rule, "amountValue");
            BigDecimal computed = support.defaultZero(amountValue).setScale(2, RoundingMode.HALF_UP);
            result.unitPrice = computed.setScale(6, RoundingMode.HALF_UP);
            result.amountValue = computed;
            explain.put("pricingSource", "FIXED_AMOUNT");
            return;
        }
        if (RULE_TYPE_FORMULA.equals(ruleType)) {
            CostRunServiceImpl.RuntimeFormula formula = support.requireRuleFormula(context.snapshot, context.rule);
            String expression = formula.formulaExpr;
            Object amountValue = support.evaluateExpression(expression,
                    support.mergeContext(context.baseContext, context.variableValues, context.feeResultContext));
            BigDecimal computed = support.defaultZero(support.toBigDecimal(amountValue)).setScale(2, RoundingMode.HALF_UP);
            result.unitPrice = computed.setScale(6, RoundingMode.HALF_UP);
            result.amountValue = computed;
            explain.put("pricingSource", "FORMULA");
            explain.put("formula", expression);
            explain.put("formulaCode", formula.formulaCode);
            explain.put("formulaName", formula.formulaName);
            explain.put("businessFormula", formula.businessFormula);
            return;
        }
        if (RULE_TYPE_TIER_RATE.equals(ruleType)) {
            BigDecimal unitPrice = context.tier == null ? BigDecimal.ZERO : support.defaultZero(context.tier.rateValue);
            result.unitPrice = support.defaultZero(unitPrice).setScale(6, RoundingMode.HALF_UP);
            explain.put("pricingSource", "TIER_RATE");
            explain.put("tierNo", context.tier == null ? null : context.tier.tierNo);
            explain.put("tierRange", context.tier == null ? null : context.tier.buildRangeSummary());
            return;
        }
        throw new ServiceException("鏆備笉鏀寔鐨勮鍒欑被鍨? " + ruleType);
    }
}
