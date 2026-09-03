package com.ruoyi.system.service.cost;

import com.ruoyi.system.domain.vo.CostExpressionAnalysisVo;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public interface ICostExpressionService {
    void validateExpression(String expression);

    void validateExpression(String expression, String namespaceScope);

    Object evaluate(String expression, Map<String, Object> context);

    CostExpressionAnalysisVo analyzeExpression(String expression);

    CostExpressionAnalysisVo analyzeExpression(String expression, String namespaceScope);

    Set<String> extractReferencedCodes(String expression, Collection<String> candidateCodes);

    Set<String> extractFeeReferences(String expression);
}
