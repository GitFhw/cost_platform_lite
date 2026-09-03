package com.ruoyi.system.service.cost.execution.node;

public interface PricingNode {

    void apply(PricingContext context, PricingSupport support);
}
