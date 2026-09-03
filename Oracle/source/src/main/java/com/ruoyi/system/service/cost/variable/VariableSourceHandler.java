package com.ruoyi.system.service.cost.variable;

import com.ruoyi.system.domain.cost.CostVariable;

public interface VariableSourceHandler {
    boolean supports(String sourceType);

    void validate(CostVariable variable, VariableSourceHandlerSupport support);

    void normalize(CostVariable variable);
}
