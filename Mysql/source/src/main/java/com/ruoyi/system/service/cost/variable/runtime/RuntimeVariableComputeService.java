package com.ruoyi.system.service.cost.variable.runtime;

import com.ruoyi.system.service.impl.cost.CostRunServiceImpl;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class RuntimeVariableComputeService {
    private final RuntimeVariableResolutionService resolutionService;
    private final RuntimeRemoteVariableValueService remoteValueService;
    private final RuntimeVariableValueConverter valueConverter;

    public RuntimeVariableComputeService(RuntimeVariableResolutionService resolutionService,
                                         RuntimeRemoteVariableValueService remoteValueService,
                                         RuntimeVariableValueConverter valueConverter) {
        this.resolutionService = resolutionService;
        this.remoteValueService = remoteValueService;
        this.valueConverter = valueConverter;
    }

    public LinkedHashMap<String, Object> compute(CostRunServiceImpl.RuntimeSnapshot snapshot,
                                                 Map<String, Object> baseContext,
                                                 List<CostRunServiceImpl.RuntimeVariable> runtimeVariables) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        if (snapshot == null) {
            return values;
        }
        List<CostRunServiceImpl.RuntimeVariable> variablesToCompute = runtimeVariables == null
                ? snapshot.variables
                : runtimeVariables;
        if (variablesToCompute == null || variablesToCompute.isEmpty()) {
            return values;
        }
        Map<String, CostRunServiceImpl.RuntimeVariable> variableMap = snapshot.variablesByCode == null
                ? Collections.emptyMap()
                : snapshot.variablesByCode;
        Set<String> dependencyStack = new LinkedHashSet<>();
        Map<String, Object> context = baseContext == null ? new LinkedHashMap<>() : baseContext;
        for (CostRunServiceImpl.RuntimeVariable variable : variablesToCompute) {
            resolutionService.resolve(
                    snapshot,
                    variable,
                    context,
                    values,
                    variableMap,
                    dependencyStack,
                    remoteValueService::resolve,
                    valueConverter);
        }
        return values;
    }
}
