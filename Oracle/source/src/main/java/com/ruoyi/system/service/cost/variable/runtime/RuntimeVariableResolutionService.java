package com.ruoyi.system.service.cost.variable.runtime;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.service.cost.ICostExpressionService;
import com.ruoyi.system.service.impl.cost.CostRunServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
public class RuntimeVariableResolutionService {
    private final RuntimeVariableResolverChain resolverChain;
    private final ICostExpressionService expressionService;

    @Autowired
    public RuntimeVariableResolutionService(RuntimeVariableResolverChain resolverChain,
                                            ICostExpressionService expressionService) {
        this.resolverChain = resolverChain;
        this.expressionService = expressionService;
    }

    public Object resolve(CostRunServiceImpl.RuntimeSnapshot snapshot,
                          CostRunServiceImpl.RuntimeVariable variable,
                          Map<String, Object> baseContext,
                          LinkedHashMap<String, Object> computedValues,
                          Map<String, CostRunServiceImpl.RuntimeVariable> variableMap,
                          Set<String> dependencyStack,
                          RuntimeRemoteValueResolver remoteValueResolver,
                          RuntimeVariableValueConverter valueConverter) {
        if (variable == null || StringUtils.isEmpty(variable.variableCode)) {
            return null;
        }
        if (computedValues.containsKey(variable.variableCode)) {
            return computedValues.get(variable.variableCode);
        }
        if (!dependencyStack.add(variable.variableCode)) {
            throw new ServiceException("公式变量存在循环依赖：" + String.join(" -> ", dependencyStack)
                    + " -> " + variable.variableCode);
        }
        try {
            RuntimeVariableResolveSupport support = buildSupport(remoteValueResolver, valueConverter);
            Object value = resolverChain.resolve(
                    new RuntimeVariableResolveContext(snapshot, variable, baseContext, computedValues, variableMap, dependencyStack),
                    support);
            Object converted = valueConverter.convert(value, variable);
            computedValues.put(variable.variableCode, converted);
            return converted;
        } finally {
            dependencyStack.remove(variable.variableCode);
        }
    }

    private RuntimeVariableResolveSupport buildSupport(RuntimeRemoteValueResolver remoteValueResolver,
                                                       RuntimeVariableValueConverter valueConverter) {
        return new RuntimeVariableResolveSupport() {
            @Override
            public Object resolveDependency(RuntimeVariableResolveContext context,
                                            CostRunServiceImpl.RuntimeVariable dependency) {
                return resolve(
                        context.getSnapshot(),
                        dependency,
                        context.getBaseContext(),
                        context.getComputedValues(),
                        context.getVariableMap(),
                        context.getDependencyStack(),
                        remoteValueResolver,
                        valueConverter);
            }

            @Override
            public String resolveVariableFormula(CostRunServiceImpl.RuntimeSnapshot snapshot,
                                                 CostRunServiceImpl.RuntimeVariable variable) {
                return RuntimeVariableResolutionService.this.resolveVariableFormula(snapshot, variable);
            }

            @Override
            public Set<String> extractExpressionVariableCodes(String expression,
                                                              Map<String, CostRunServiceImpl.RuntimeVariable> variableMap) {
                return expressionService.extractReferencedCodes(expression,
                        variableMap == null ? Collections.emptySet() : variableMap.keySet());
            }

            @Override
            public Map<String, Object> mergeExpressionContext(Map<String, Object> baseContext,
                                                              Map<String, Object> computedValues) {
                return mergeContext(baseContext, computedValues);
            }

            @Override
            public Object evaluateExpression(String expression, Map<String, Object> context) {
                return RuntimeVariableResolutionService.this.evaluateExpression(expression, context);
            }

            @Override
            public Object resolveRemoteVariableValue(CostRunServiceImpl.RuntimeVariable variable,
                                                     Map<String, Object> baseContext) {
                return remoteValueResolver.resolve(variable, baseContext);
            }

            @Override
            public Object resolveInputVariableValue(Map<String, Object> baseContext,
                                                    CostRunServiceImpl.RuntimeVariable variable) {
                return resolveValueFromInput(baseContext, variable);
            }
        };
    }

    private String resolveVariableFormula(CostRunServiceImpl.RuntimeSnapshot snapshot,
                                          CostRunServiceImpl.RuntimeVariable variable) {
        if (StringUtils.isEmpty(variable.formulaCode)) {
            throw new ServiceException("当前运行配置中的公式变量[" + variable.variableCode + "]未绑定公式编码，请先补齐配置后再执行");
        }
        CostRunServiceImpl.RuntimeFormula formula = snapshot.formulasByCode.get(variable.formulaCode);
        if (formula == null || StringUtils.isEmpty(formula.formulaExpr)) {
            throw new ServiceException("当前运行配置中的公式变量[" + variable.variableCode + "]引用的公式编码["
                    + variable.formulaCode + "]不存在或不可执行，请先补齐配置后再执行");
        }
        return formula.formulaExpr;
    }

    private Object evaluateExpression(String expression, Map<String, Object> context) {
        if (StringUtils.isEmpty(expression)) {
            return null;
        }
        try {
            return expressionService.evaluate(expression, context);
        } catch (Exception e) {
            throw new ServiceException("表达式执行失败：" + expression);
        }
    }

    private Map<String, Object> mergeContext(Map<String, Object> inputContext, Map<String, Object> variableValues) {
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
        context.put("F", new LinkedHashMap<>());
        context.put("T", new LinkedHashMap<>());
        return context;
    }

    private Object resolveValueFromInput(Map<String, Object> input, CostRunServiceImpl.RuntimeVariable variable) {
        if (variable == null) {
            return null;
        }
        Object value = null;
        if (StringUtils.isNotEmpty(variable.variableCode)) {
            value = resolveByPath(input, variable.variableCode);
        }
        if (value == null && StringUtils.isNotEmpty(variable.dataPath)) {
            value = resolveByPath(input, variable.dataPath);
        }
        return value != null ? value : variable.defaultValue;
    }

    private Object resolveByPath(Map<String, Object> input, String path) {
        if (input == null || StringUtils.isEmpty(path)) {
            return null;
        }
        String[] pieces = path.split("\\.");
        Object current = input;
        for (String piece : pieces) {
            if (!(current instanceof Map)) {
                return null;
            }
            current = ((Map<?, ?>) current).get(piece);
            if (current == null) {
                return null;
            }
        }
        return current;
    }
}
