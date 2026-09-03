package com.ruoyi.system.service.cost.execution.node;

import com.ruoyi.system.service.cost.execution.model.FeeExecutionResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class AmountTotalAggregationNode implements ExecutionAggregationNode {

    @Override
    public void apply(ExecutionAggregationContext context) {
        List<FeeExecutionResult> feeResults = context.feeResults;
        BigDecimal total = BigDecimal.ZERO;
        if (feeResults != null) {
            for (FeeExecutionResult item : feeResults) {
                if (item != null && item.amountValue != null) {
                    total = total.add(item.amountValue);
                }
            }
        }
        context.resultView.put("amountTotal", total);
    }
}
