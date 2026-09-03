package com.ruoyi.system.service.cost.variable.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.service.impl.cost.CostRunServiceImpl;
import org.springframework.stereotype.Service;

import static com.ruoyi.system.service.cost.constant.CostDomainConstants.*;

import java.math.BigDecimal;

@Service
public class RuntimeVariableValueConverterImpl implements RuntimeVariableValueConverter {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Object convert(Object value, CostRunServiceImpl.RuntimeVariable variable) {
        if (variable == null) {
            return value;
        }
        String dataType = variable.dataType;
        Object defaultValue = variable.defaultValue;
        if (DATA_TYPE_NUMBER.equalsIgnoreCase(dataType)) {
            return toBigDecimal(value == null ? defaultValue : value);
        }
        if (DATA_TYPE_BOOLEAN.equalsIgnoreCase(dataType)) {
            return convertBoolean(value == null ? defaultValue : value);
        }
        if (DATA_TYPE_JSON.equalsIgnoreCase(dataType) && value instanceof String) {
            return parseJsonToObject(String.valueOf(value));
        }
        if (value == null) {
            return defaultValue;
        }
        return value;
    }

    private Object parseJsonToObject(String json) {
        if (StringUtils.isEmpty(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            throw new ServiceException("JSON 解析失败：" + e.getMessage());
        }
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null || StringUtils.isEmpty(String.valueOf(value))) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private Boolean convertBoolean(Object value) {
        if (value == null) {
            return Boolean.FALSE;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String normalized = String.valueOf(value);
        return "true".equalsIgnoreCase(normalized) || "1".equals(normalized);
    }
}
