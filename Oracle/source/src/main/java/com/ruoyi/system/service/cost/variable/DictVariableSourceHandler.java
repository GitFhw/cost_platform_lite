package com.ruoyi.system.service.cost.variable;

import com.ruoyi.system.domain.cost.CostVariable;
import org.springframework.stereotype.Component;

@Component
public class DictVariableSourceHandler extends AbstractVariableSourceHandler {
    @Override
    public boolean supports(String sourceType) {
        return "DICT".equalsIgnoreCase(sourceType);
    }

    @Override
    public void validate(CostVariable variable, VariableSourceHandlerSupport support) {
        support.validateDictTypeExists(variable.getDictType());
    }

    @Override
    public void normalize(CostVariable variable) {
        clearSourceSystem(variable);
        clearRemote(variable);
        clearFormula(variable);
        normalizeDataPath(variable);
    }
}
