package com.ruoyi.system.service.cost.variable.runtime;

import com.ruoyi.system.service.impl.cost.CostRunServiceImpl;

import java.util.Map;

@FunctionalInterface
public interface RuntimeRemoteValueResolver {
    Object resolve(CostRunServiceImpl.RuntimeVariable variable, Map<String, Object> baseContext);
}
