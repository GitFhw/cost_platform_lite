package com.ruoyi.system.service.cost.variable.runtime;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1000)
public class FallbackRuntimeVariableResolver implements RuntimeVariableResolver {
    @Override
    public boolean supports(RuntimeVariableResolveContext context) {
        return true;
    }

    @Override
    public Object resolve(RuntimeVariableResolveContext context,
                          RuntimeVariableResolveSupport support,
                          RuntimeVariableResolverChain chain) {
        return support.resolveInputVariableValue(context.getBaseContext(), context.getVariable());
    }
}
