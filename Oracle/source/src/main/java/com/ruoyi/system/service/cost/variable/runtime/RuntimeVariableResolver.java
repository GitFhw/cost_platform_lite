package com.ruoyi.system.service.cost.variable.runtime;

public interface RuntimeVariableResolver {
    boolean supports(RuntimeVariableResolveContext context);

    Object resolve(RuntimeVariableResolveContext context,
                   RuntimeVariableResolveSupport support,
                   RuntimeVariableResolverChain chain);
}
