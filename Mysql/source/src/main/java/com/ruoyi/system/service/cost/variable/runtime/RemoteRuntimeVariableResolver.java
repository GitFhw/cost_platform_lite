package com.ruoyi.system.service.cost.variable.runtime;

import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.service.impl.cost.CostRunServiceImpl;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import static com.ruoyi.system.service.cost.constant.CostDomainConstants.*;

@Component
@Order(20)
public class RemoteRuntimeVariableResolver implements RuntimeVariableResolver {
    @Override
    public boolean supports(RuntimeVariableResolveContext context) {
        CostRunServiceImpl.RuntimeVariable variable = context.getVariable();
        return variable != null && StringUtils.equalsIgnoreCase(SOURCE_TYPE_REMOTE, variable.sourceType);
    }

    @Override
    public Object resolve(RuntimeVariableResolveContext context,
                          RuntimeVariableResolveSupport support,
                          RuntimeVariableResolverChain chain) {
        return support.resolveRemoteVariableValue(context.getVariable(), context.getBaseContext());
    }
}
