package com.ruoyi.system.service.cost.variable.runtime;

import com.ruoyi.common.exception.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RuntimeVariableResolverChain {
    private final List<RuntimeVariableResolver> resolvers;

    @Autowired
    public RuntimeVariableResolverChain(List<RuntimeVariableResolver> resolvers) {
        this.resolvers = resolvers == null ? new ArrayList<>() : new ArrayList<>(resolvers);
        AnnotationAwareOrderComparator.sort(this.resolvers);
    }

    public RuntimeVariableResolverChain(RuntimeVariableResolver... resolvers) {
        this.resolvers = resolvers == null ? new ArrayList<>() : new ArrayList<>(List.of(resolvers));
        AnnotationAwareOrderComparator.sort(this.resolvers);
    }

    public Object resolve(RuntimeVariableResolveContext context, RuntimeVariableResolveSupport support) {
        for (RuntimeVariableResolver resolver : resolvers) {
            if (resolver.supports(context)) {
                return resolver.resolve(context, support, this);
            }
        }
        throw new ServiceException("未找到可处理的运行态变量解析器");
    }
}
