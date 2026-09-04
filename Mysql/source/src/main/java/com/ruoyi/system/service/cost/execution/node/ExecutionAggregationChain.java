package com.ruoyi.system.service.cost.execution.node;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ExecutionAggregationChain {
    private final List<ExecutionAggregationNode> nodes;

    public ExecutionAggregationChain(AmountTotalAggregationNode amountTotalAggregationNode) {
        this.nodes = java.util.Arrays.asList(amountTotalAggregationNode);
    }

    public void apply(ExecutionAggregationContext context) {
        for (ExecutionAggregationNode node : nodes) {
            node.apply(context);
        }
    }
}
