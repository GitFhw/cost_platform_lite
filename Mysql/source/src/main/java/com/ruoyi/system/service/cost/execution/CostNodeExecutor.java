package com.ruoyi.system.service.cost.execution;

import com.ruoyi.system.service.cost.execution.model.ExecutionResult;
import com.ruoyi.system.service.impl.cost.CostRunServiceImpl;

import java.util.List;
import java.util.Map;

public interface CostNodeExecutor {

    ExecutionResult execute(CostRunServiceImpl.RuntimeSnapshot snapshot,
                            String taskNo,
                            String billMonth,
                            Map<String, Object> input,
                            String bizNo,
                            List<CostRunServiceImpl.RuntimeFee> targetFees,
                            boolean includeExplain,
                            Map<String, Object> baseContext,
                            Map<String, Object> variableValues);

    Map<String, Object> buildFeeNoMatchExplain(CostRunServiceImpl.RuntimeFee fee,
                                               List<CostRunServiceImpl.RuntimeRule> rules,
                                               Map<String, Object> variableValues,
                                               List<Map<String, Object>> ruleEvaluations);

    Map<String, Object> buildFeeFailureExplain(CostRunServiceImpl.RuntimeFee fee, Exception exception);
}
