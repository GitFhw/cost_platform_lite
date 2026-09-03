package com.ruoyi.system.service.cost.variable.runtime;

import com.ruoyi.system.service.impl.cost.CostRunServiceImpl;

import java.util.Map;
import java.util.Set;

public interface RuntimeVariableResolveSupport {
    Object resolveDependency(RuntimeVariableResolveContext context, CostRunServiceImpl.RuntimeVariable dependency);

    String resolveVariableFormula(CostRunServiceImpl.RuntimeSnapshot snapshot, CostRunServiceImpl.RuntimeVariable variable);

    Set<String> extractExpressionVariableCodes(String expression,
                                               Map<String, CostRunServiceImpl.RuntimeVariable> variableMap);

    Map<String, Object> mergeExpressionContext(Map<String, Object> baseContext, Map<String, Object> computedValues);

    Object evaluateExpression(String expression, Map<String, Object> context);

    Object resolveRemoteVariableValue(CostRunServiceImpl.RuntimeVariable variable, Map<String, Object> baseContext);

    Object resolveInputVariableValue(Map<String, Object> baseContext, CostRunServiceImpl.RuntimeVariable variable);
}
