package com.ruoyi.system.service.cost.execution.node;

import com.ruoyi.system.service.cost.execution.model.FeeExecutionResult;

import java.util.List;
import java.util.Map;

public class ExecutionAggregationContext {
    public final List<FeeExecutionResult> feeResults;
    public final Map<String, Object> resultView;

    public ExecutionAggregationContext(List<FeeExecutionResult> feeResults,
                                       Map<String, Object> resultView) {
        this.feeResults = feeResults;
        this.resultView = resultView;
    }
}
