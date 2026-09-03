package com.ruoyi.system.service.cost.variable;

import com.ruoyi.system.domain.cost.CostVariable;
import org.springframework.stereotype.Component;

@Component
public class InputVariableSourceHandler extends AbstractVariableSourceHandler {
    @Override
    public boolean supports(String sourceType) {
        return "INPUT".equalsIgnoreCase(sourceType);
    }

    @Override
    public void validate(CostVariable variable, VariableSourceHandlerSupport support) {
    }

    @Override
    public void normalize(CostVariable variable) {
        clearSourceSystem(variable);
        clearDict(variable);
        clearRemote(variable);
        clearFormula(variable);
        normalizeDataPath(variable);
    }
}
