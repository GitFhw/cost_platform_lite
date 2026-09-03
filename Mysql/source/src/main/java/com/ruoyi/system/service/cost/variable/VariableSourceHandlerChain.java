package com.ruoyi.system.service.cost.variable;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.cost.CostVariable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VariableSourceHandlerChain {
    private final List<VariableSourceHandler> handlers;

    public VariableSourceHandlerChain(List<VariableSourceHandler> handlers) {
        this.handlers = handlers;
    }

    public void validate(CostVariable variable, VariableSourceHandlerSupport support) {
        resolveHandler(variable).validate(variable, support);
    }

    public void normalize(CostVariable variable) {
        resolveHandler(variable).normalize(variable);
    }

    private VariableSourceHandler resolveHandler(CostVariable variable) {
        String sourceType = variable == null ? "" : StringUtils.defaultIfEmpty(StringUtils.trim(variable.getSourceType()), "INPUT");
        for (VariableSourceHandler handler : handlers) {
            if (handler.supports(sourceType)) {
                return handler;
            }
        }
        throw new ServiceException("暂不支持的变量来源类型：" + sourceType);
    }
}
