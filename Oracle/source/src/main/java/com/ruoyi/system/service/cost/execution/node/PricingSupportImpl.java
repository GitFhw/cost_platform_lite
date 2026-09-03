package com.ruoyi.system.service.cost.execution.node;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.service.cost.ICostExpressionService;
import com.ruoyi.system.service.impl.cost.CostRunServiceImpl;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.ruoyi.system.service.cost.execution.CostExecutionConstants.PRICING_MODE_GROUPED;

@Component
public class PricingSupportImpl implements PricingSupport {
    private final ICostExpressionService expressionService;

    public PricingSupportImpl(ICostExpressionService expressionService) {
        this.expressionService = expressionService;
    }

    @Override
    public BigDecimal resolveGroupedPricingValue(CostRunServiceImpl.RuntimeRule rule, String valueKey) {
        if (!PRICING_MODE_GROUPED.equalsIgnoreCase(rule.pricingMode)) {
            return toBigDecimal(rule.pricingConfig.get(valueKey));
        }
        Object rawGroupPrices = rule.pricingConfig.get("groupPrices");
        if (!(rawGroupPrices instanceof List<?> groupPrices)) {
            throw new ServiceException(String.format("规则 %s 未配置组合定价明细", rule.ruleCode));
        }
        for (Object item : groupPrices) {
            if (!(item instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Integer groupNo = intValue(rawMap.get("groupNo"));
            if (Objects.equals(groupNo, rule.matchedGroupNo)) {
                BigDecimal value = toBigDecimal(rawMap.get(valueKey));
                if (value == null) {
                    throw new ServiceException(String.format("规则 %s 的组合组 %s 未配置定价值", rule.ruleCode, groupNo));
                }
                return value;
            }
        }
        throw new ServiceException(String.format("规则 %s 未找到命中组合组 %s 对应的定价配置", rule.ruleCode, rule.matchedGroupNo));
    }

    @Override
    public Object evaluateExpression(String expression, Map<String, Object> context) {
        if (StringUtils.isEmpty(expression)) {
            return null;
        }
        try {
            return expressionService.evaluate(expression, context);
        } catch (Exception e) {
            throw new ServiceException("表达式执行失败：" + expression);
        }
    }

    @Override
    public Map<String, Object> mergeContext(Map<String, Object> inputContext,
                                            Map<String, Object> variableValues,
                                            Map<String, Object> feeResultContext) {
        LinkedHashMap<String, Object> context = new LinkedHashMap<>();
        if (inputContext != null) {
            context.putAll(inputContext);
        }
        if (variableValues != null) {
            context.putAll(variableValues);
        }
        context.put("I", inputContext == null ? new LinkedHashMap<>() : new LinkedHashMap<>(inputContext));
        context.put("V", variableValues == null ? new LinkedHashMap<>() : new LinkedHashMap<>(variableValues));
        LinkedHashMap<String, Object> common = new LinkedHashMap<>();
        common.put("sceneCode", inputContext == null ? null : inputContext.get("sceneCode"));
        common.put("sceneName", inputContext == null ? null : inputContext.get("sceneName"));
        common.put("versionNo", inputContext == null ? null : inputContext.get("versionNo"));
        common.put("billMonth", inputContext == null ? null : inputContext.get("billMonth"));
        context.put("C", common);
        context.put("F", feeResultContext == null ? new LinkedHashMap<>() : new LinkedHashMap<>(feeResultContext));
        context.put("T", new LinkedHashMap<>());
        return context;
    }

    @Override
    public CostRunServiceImpl.RuntimeFormula requireRuleFormula(CostRunServiceImpl.RuntimeSnapshot snapshot,
                                                                CostRunServiceImpl.RuntimeRule rule) {
        if (StringUtils.isEmpty(rule.amountFormulaCode)) {
            throw new ServiceException("当前运行配置中的公式规则[" + rule.ruleCode + "]未绑定金额公式编码，请先补齐配置后再执行");
        }
        CostRunServiceImpl.RuntimeFormula formula = snapshot.formulasByCode.get(rule.amountFormulaCode);
        if (formula == null || StringUtils.isEmpty(formula.formulaExpr)) {
            throw new ServiceException("当前运行配置中的公式规则[" + rule.ruleCode + "]引用的公式编码[" + rule.amountFormulaCode + "]不存在或不可执行，请先补齐配置后再执行");
        }
        return formula;
    }

    @Override
    public BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (Exception ex) {
            return null;
        }
    }

    @Override
    public BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private Integer intValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
