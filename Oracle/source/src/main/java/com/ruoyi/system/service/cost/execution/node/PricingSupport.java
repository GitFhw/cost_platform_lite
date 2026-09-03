package com.ruoyi.system.service.cost.execution.node;

import com.ruoyi.system.service.impl.cost.CostRunServiceImpl;

import java.math.BigDecimal;
import java.util.Map;

public interface PricingSupport {

    BigDecimal toBigDecimal(Object value);

    BigDecimal defaultZero(BigDecimal value);

    BigDecimal resolveGroupedPricingValue(CostRunServiceImpl.RuntimeRule rule, String valueKey);

    CostRunServiceImpl.RuntimeFormula requireRuleFormula(CostRunServiceImpl.RuntimeSnapshot snapshot,
                                                         CostRunServiceImpl.RuntimeRule rule);

    Object evaluateExpression(String expression, Map<String, Object> context);

    Map<String, Object> mergeContext(Map<String, Object> inputContext,
                                     Map<String, Object> variableValues,
                                     Map<String, Object> feeResultContext);
}
