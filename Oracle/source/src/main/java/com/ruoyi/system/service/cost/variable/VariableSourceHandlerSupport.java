package com.ruoyi.system.service.cost.variable;

import com.ruoyi.system.domain.cost.CostVariable;

public interface VariableSourceHandlerSupport {
    void validateDictTypeExists(String dictType);

    void validateRemoteVariableConfig(CostVariable variable);

    void validateFormulaVariableConfig(CostVariable variable);
}
