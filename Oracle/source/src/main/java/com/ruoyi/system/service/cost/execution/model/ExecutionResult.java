package com.ruoyi.system.service.cost.execution.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ExecutionResult {
    public Map<String, Object> variableView;
    public Map<String, Object> resultView;
    public Map<String, Object> explainView;
    public List<FeeExecutionResult> feeResults;
    public Map<String, Map<String, Object>> skippedFeeExplains = new LinkedHashMap<>();
}
