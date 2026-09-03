package com.ruoyi.system.service.cost.variable;

import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.cost.CostVariable;
import org.springframework.stereotype.Component;

@Component
public class FormulaVariableSourceHandler extends AbstractVariableSourceHandler {
    @Override
    public boolean supports(String sourceType) {
        return "FORMULA".equalsIgnoreCase(sourceType);
    }

    @Override
    public void validate(CostVariable variable, VariableSourceHandlerSupport support) {
        support.validateFormulaVariableConfig(variable);
    }

    @Override
    public void normalize(CostVariable variable) {
        clearSourceSystem(variable);
        clearDict(variable);
        clearRemote(variable);
        clearDataPath(variable);
        variable.setFormulaCode(StringUtils.trim(variable.getFormulaCode()));
    }
}
