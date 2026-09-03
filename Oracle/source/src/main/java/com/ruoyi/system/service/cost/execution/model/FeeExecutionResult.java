package com.ruoyi.system.service.cost.execution.model;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FeeExecutionResult {
    public Long feeId;
    public String feeCode;
    public String feeName;
    public String unitCode;
    public String objectDimension;
    public Long ruleId;
    public String ruleCode;
    public String ruleName;
    public Long tierId;
    public BigDecimal quantityValue;
    public BigDecimal unitPrice;
    public BigDecimal amountValue;
    public Map<String, Object> variableExplain;
    public List<Map<String, Object>> conditionExplain;
    public Map<String, Object> pricingExplain;
    public List<Map<String, Object>> timelineSteps;

    public Map<String, Object> toView() {
        LinkedHashMap<String, Object> item = new LinkedHashMap<>();
        item.put("feeId", feeId);
        item.put("feeCode", feeCode);
        item.put("feeName", feeName);
        item.put("unitCode", unitCode);
        item.put("ruleCode", ruleCode);
        item.put("ruleName", ruleName);
        item.put("quantityValue", quantityValue);
        item.put("unitPrice", unitPrice);
        item.put("amountValue", amountValue);
        return item;
    }

    public Map<String, Object> toExplainView() {
        LinkedHashMap<String, Object> item = new LinkedHashMap<>();
        item.put("feeCode", feeCode);
        item.put("feeName", feeName);
        item.put("unitCode", unitCode);
        item.put("ruleCode", ruleCode);
        item.put("ruleName", ruleName);
        item.put("conditions", conditionExplain);
        item.put("pricing", pricingExplain);
        item.put("timeline", timelineSteps);
        return item;
    }
}
