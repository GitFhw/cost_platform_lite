package com.ruoyi.lite.dictionary;

import com.ruoyi.lite.config.CostLiteProperties;
import com.ruoyi.system.service.cost.dictionary.CostDictionaryProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
/**
 * 轻量运行时配置字典适配器。
 */
@Component
@ConditionalOnProperty(prefix = "cost.lite.dictionary", name = "provider", havingValue = "CONFIG", matchIfMissing = true)
public class CostLiteConfigDictionaryProvider implements CostDictionaryProvider {
    private final CostLiteProperties properties;

    public CostLiteConfigDictionaryProvider(CostLiteProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean containsType(String dictType) {
        CostLiteProperties.Dictionary dictionary = properties.getDictionary();
        if (!dictionary.isValidationEnabled()) {
            return true;
        }
        return dictionary.findConfiguredValues(dictType) != null || dictionary.isAllowUnconfiguredTypes();
    }

    @Override
    public boolean containsValue(String dictType, String dictValue) {
        CostLiteProperties.Dictionary dictionary = properties.getDictionary();
        if (!dictionary.isValidationEnabled()) {
            return true;
        }
        List<String> configuredValues = dictionary.findConfiguredValues(dictType);
        if (configuredValues == null || configuredValues.isEmpty()) {
            return dictionary.isAllowUnconfiguredTypes();
        }
        return configuredValues.contains(dictionary.resolveValue(dictType, dictValue));
    }
}
