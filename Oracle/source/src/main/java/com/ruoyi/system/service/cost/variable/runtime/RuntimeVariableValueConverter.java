package com.ruoyi.system.service.cost.variable.runtime;

import com.ruoyi.system.service.impl.cost.CostRunServiceImpl;

@FunctionalInterface
public interface RuntimeVariableValueConverter {
    Object convert(Object value, CostRunServiceImpl.RuntimeVariable variable);
}
