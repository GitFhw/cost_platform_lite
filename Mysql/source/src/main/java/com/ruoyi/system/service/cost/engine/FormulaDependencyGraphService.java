package com.ruoyi.system.service.cost.engine;

import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.vo.CostExpressionAnalysisVo;
import com.ruoyi.system.service.cost.ICostExpressionService;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class FormulaDependencyGraphService {
    private final ICostExpressionService expressionService;

    public FormulaDependencyGraphService(ICostExpressionService expressionService) {
        this.expressionService = expressionService;
    }

    public FormulaDependencyCheckResult inspectPublishBundle(Map<String, Map<String, Object>> variablesByCode,
                                                             Map<String, Map<String, Object>> formulasByCode,
                                                             Map<String, Map<String, Object>> feesByCode) {
        Map<String, Map<String, Object>> safeVariables = variablesByCode == null ? Collections.emptyMap() : variablesByCode;
        Map<String, Map<String, Object>> safeFormulas = formulasByCode == null ? Collections.emptyMap() : formulasByCode;
        Map<String, Map<String, Object>> safeFees = feesByCode == null ? Collections.emptyMap() : feesByCode;

        LinkedHashMap<String, Set<String>> missingVariableRefs = new LinkedHashMap<>();
        LinkedHashMap<String, Set<String>> missingFeeRefs = new LinkedHashMap<>();
        LinkedHashMap<String, Set<String>> formulaVariableDependencies = new LinkedHashMap<>();

        for (Map<String, Object> variable : safeVariables.values()) {
            if (!"FORMULA".equalsIgnoreCase(stringValue(variable.get("sourceType")))) {
                continue;
            }
            String variableCode = stringValue(variable.get("variableCode"));
            String formulaCode = stringValue(variable.get("formulaCode"));
            Map<String, Object> formula = safeFormulas.get(formulaCode);
            if (StringUtils.isEmpty(variableCode) || formula == null) {
                continue;
            }
            String expression = stringValue(formula.get("formulaExpr"));
            CostExpressionAnalysisVo analysis = expressionService.analyzeExpression(expression);
            LinkedHashSet<String> variableRefs = new LinkedHashSet<>();
            for (String reference : analysis.getVariableReferences()) {
                String normalized = normalizeVariableReference(reference);
                if (StringUtils.isNotEmpty(normalized)) {
                    variableRefs.add(normalized);
                }
            }
            LinkedHashSet<String> feeRefs = new LinkedHashSet<>(analysis.getFeeReferences());

            variableRefs.remove(variableCode);

            LinkedHashSet<String> missingVariables = new LinkedHashSet<>();
            LinkedHashSet<String> dependencyFormulaVariables = new LinkedHashSet<>();
            for (String referencedVariable : variableRefs) {
                Map<String, Object> dependency = safeVariables.get(referencedVariable);
                if (dependency == null) {
                    missingVariables.add(referencedVariable);
                    continue;
                }
                if ("FORMULA".equalsIgnoreCase(stringValue(dependency.get("sourceType")))) {
                    dependencyFormulaVariables.add(referencedVariable);
                }
            }
            if (!missingVariables.isEmpty()) {
                missingVariableRefs.put(variableCode, missingVariables);
            }
            formulaVariableDependencies.put(variableCode, dependencyFormulaVariables);

            LinkedHashSet<String> missingFees = new LinkedHashSet<>();
            for (String feeCode : feeRefs) {
                if (!safeFees.containsKey(feeCode)) {
                    missingFees.add(feeCode);
                }
            }
            if (!missingFees.isEmpty()) {
                missingFeeRefs.put(variableCode, missingFees);
            }
        }

        FormulaDependencyCheckResult result = new FormulaDependencyCheckResult();
        result.setMissingVariableRefs(missingVariableRefs);
        result.setMissingFeeRefs(missingFeeRefs);
        result.setFormulaVariableCycles(detectCycles(formulaVariableDependencies));
        return result;
    }

    private List<List<String>> detectCycles(Map<String, Set<String>> dependencyGraph) {
        List<List<String>> cycles = new ArrayList<>();
        LinkedHashSet<String> visited = new LinkedHashSet<>();
        LinkedHashSet<String> visiting = new LinkedHashSet<>();
        for (String code : dependencyGraph.keySet()) {
            dfs(code, dependencyGraph, visited, visiting, cycles);
        }
        return cycles;
    }

    private void dfs(String current, Map<String, Set<String>> dependencyGraph, Set<String> visited,
                     LinkedHashSet<String> visiting, List<List<String>> cycles) {
        if (StringUtils.isEmpty(current) || visited.contains(current)) {
            return;
        }
        if (!visiting.add(current)) {
            List<String> cycle = buildCycle(visiting, current);
            if (!containsCycle(cycles, cycle)) {
                cycles.add(cycle);
            }
            return;
        }
        for (String next : dependencyGraph.getOrDefault(current, Collections.emptySet())) {
            if (visiting.contains(next)) {
                List<String> cycle = buildCycle(visiting, next);
                if (!containsCycle(cycles, cycle)) {
                    cycles.add(cycle);
                }
                continue;
            }
            dfs(next, dependencyGraph, visited, visiting, cycles);
        }
        visiting.remove(current);
        visited.add(current);
    }

    private List<String> buildCycle(LinkedHashSet<String> visiting, String entryPoint) {
        ArrayList<String> cycle = new ArrayList<>();
        boolean include = false;
        for (String node : visiting) {
            if (!include && StringUtils.equals(node, entryPoint)) {
                include = true;
            }
            if (include) {
                cycle.add(node);
            }
        }
        cycle.add(entryPoint);
        return cycle;
    }

    private boolean containsCycle(List<List<String>> cycles, List<String> candidate) {
        for (List<String> existing : cycles) {
            if (existing.equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String normalizeVariableReference(String reference) {
        String raw = stringValue(reference).trim();
        if (StringUtils.isEmpty(raw)) {
            return "";
        }
        int dotIndex = raw.indexOf('.');
        if (dotIndex <= 0 || dotIndex >= raw.length() - 1) {
            return raw;
        }
        String namespace = raw.substring(0, dotIndex);
        if (!"V".equalsIgnoreCase(namespace)) {
            return "";
        }
        return raw.substring(dotIndex + 1);
    }
}
