package com.ruoyi.system.service.cost.execution.node;

import com.ruoyi.system.service.cost.execution.model.PricingResult;
import com.ruoyi.system.service.impl.cost.CostRunServiceImpl;

import java.util.LinkedHashMap;
import java.util.Map;

public class PricingContext {
    public final CostRunServiceImpl.RuntimeRule rule;
    public final CostRunServiceImpl.RuntimeTier tier;
    public final CostRunServiceImpl.RuntimeSnapshot snapshot;
    public final Map<String, Object> variableValues;
    public final Map<String, Object> baseContext;
    public final Map<String, Object> feeResultContext;
    public final PricingResult pricingResult;

    public PricingContext(CostRunServiceImpl.RuntimeRule rule,
                          CostRunServiceImpl.RuntimeTier tier,
                          CostRunServiceImpl.RuntimeSnapshot snapshot,
                          Map<String, Object> variableValues,
                          Map<String, Object> baseContext,
                          Map<String, Object> feeResultContext,
                          PricingResult pricingResult) {
        this.rule = rule;
        this.tier = tier;
        this.snapshot = snapshot;
        this.variableValues = variableValues;
        this.baseContext = baseContext;
        this.feeResultContext = feeResultContext;
        this.pricingResult = pricingResult;
    }

    public Map<String, Object> pricingExplain() {
        if (pricingResult.pricingExplain == null) {
            pricingResult.pricingExplain = new LinkedHashMap<>();
        }
        return pricingResult.pricingExplain;
    }
}
