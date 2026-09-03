package com.ruoyi.system.service.cost.execution.model;

import java.math.BigDecimal;
import java.util.Map;

public class PricingResult {
    public BigDecimal quantityValue;
    public BigDecimal unitPrice;
    public BigDecimal amountValue;
    public Map<String, Object> pricingExplain;
}
