package com.ruoyi.system.service.cost.variable.runtime;

import com.ruoyi.system.service.impl.cost.CostRunServiceImpl;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class RuntimeVariableResolveContext {
    private final CostRunServiceImpl.RuntimeSnapshot snapshot;
    private final CostRunServiceImpl.RuntimeVariable variable;
    private final Map<String, Object> baseContext;
    private final LinkedHashMap<String, Object> computedValues;
    private final Map<String, CostRunServiceImpl.RuntimeVariable> variableMap;
    private final Set<String> dependencyStack;

    public RuntimeVariableResolveContext(CostRunServiceImpl.RuntimeSnapshot snapshot,
                                         CostRunServiceImpl.RuntimeVariable variable,
                                         Map<String, Object> baseContext,
                                         LinkedHashMap<String, Object> computedValues,
                                         Map<String, CostRunServiceImpl.RuntimeVariable> variableMap,
                                         Set<String> dependencyStack) {
        this.snapshot = snapshot;
        this.variable = variable;
        this.baseContext = baseContext;
        this.computedValues = computedValues;
        this.variableMap = variableMap;
        this.dependencyStack = dependencyStack;
    }

    public CostRunServiceImpl.RuntimeSnapshot getSnapshot() {
        return snapshot;
    }

    public CostRunServiceImpl.RuntimeVariable getVariable() {
        return variable;
    }

    public Map<String, Object> getBaseContext() {
        return baseContext;
    }

    public LinkedHashMap<String, Object> getComputedValues() {
        return computedValues;
    }

    public Map<String, CostRunServiceImpl.RuntimeVariable> getVariableMap() {
        return variableMap;
    }

    public Set<String> getDependencyStack() {
        return dependencyStack;
    }

    public RuntimeVariableResolveContext withVariable(CostRunServiceImpl.RuntimeVariable nextVariable) {
        return new RuntimeVariableResolveContext(snapshot, nextVariable, baseContext, computedValues, variableMap, dependencyStack);
    }
}
