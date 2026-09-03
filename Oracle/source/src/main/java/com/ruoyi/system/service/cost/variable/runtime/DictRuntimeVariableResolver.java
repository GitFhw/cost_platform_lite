package com.ruoyi.system.service.cost.variable.runtime;

import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.service.impl.cost.CostRunServiceImpl;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import static com.ruoyi.system.service.cost.constant.CostDomainConstants.*;

@Component
@Order(40)
public class DictRuntimeVariableResolver implements RuntimeVariableResolver {
    @Override
    public boolean supports(RuntimeVariableResolveContext context) {
        CostRunServiceImpl.RuntimeVariable variable = context.getVariable();
        return variable != null && StringUtils.equalsIgnoreCase(SOURCE_TYPE_DICT, variable.sourceType);
    }

    @Override
    public Object resolve(RuntimeVariableResolveContext context,
                          RuntimeVariableResolveSupport support,
                          RuntimeVariableResolverChain chain) {
        return support.resolveInputVariableValue(context.getBaseContext(), context.getVariable());
    }
}
