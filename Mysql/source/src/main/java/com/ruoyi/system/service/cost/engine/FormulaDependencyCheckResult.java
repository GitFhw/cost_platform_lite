package com.ruoyi.system.service.cost.engine;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
public class FormulaDependencyCheckResult {
    private Map<String, Set<String>> missingVariableRefs;

    private Map<String, Set<String>> missingFeeRefs;

    private List<List<String>> formulaVariableCycles = new ArrayList<>();
}
