package com.ruoyi.system.service.cost.variable;

import com.ruoyi.system.domain.cost.CostVariable;
import org.springframework.stereotype.Component;

@Component
public class RemoteVariableSourceHandler extends AbstractVariableSourceHandler {
    @Override
    public boolean supports(String sourceType) {
        return "REMOTE".equalsIgnoreCase(sourceType);
    }

    @Override
    public void validate(CostVariable variable, VariableSourceHandlerSupport support) {
        support.validateRemoteVariableConfig(variable);
    }

    @Override
    public void normalize(CostVariable variable) {
        clearDict(variable);
        clearFormula(variable);
        normalizeRemote(variable);
    }
}
