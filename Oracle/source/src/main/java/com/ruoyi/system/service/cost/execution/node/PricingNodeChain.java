package com.ruoyi.system.service.cost.execution.node;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PricingNodeChain {
    private final List<PricingNode> nodes;

    public PricingNodeChain(QuantityNode quantityNode,
                            UnitPriceNode unitPriceNode,
                            AmountNode amountNode) {
        this.nodes = List.of(quantityNode, unitPriceNode, amountNode);
    }

    public void apply(PricingContext context, PricingSupport support) {
        for (PricingNode node : nodes) {
            node.apply(context, support);
        }
    }
}
