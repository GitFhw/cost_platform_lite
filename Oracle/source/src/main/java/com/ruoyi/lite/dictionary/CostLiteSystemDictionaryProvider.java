package com.ruoyi.lite.dictionary;

import com.ruoyi.lite.config.CostLiteProperties;
import com.ruoyi.system.mapper.SysDictDataMapper;
import com.ruoyi.system.mapper.SysDictTypeMapper;
import com.ruoyi.system.service.cost.dictionary.CostDictionaryProvider;
import com.ruoyi.system.service.impl.cost.dictionary.SystemCostDictionaryProvider;

/**
 * 复用宿主系统字典的轻量适配器。
 *
 * <p>计费领域始终保留母体统一编码；轻量库中的字典类型和值不一致时，
 * 仅通过 {@code cost.lite.dictionary} 映射配置完成转换。</p>
 */
public class CostLiteSystemDictionaryProvider implements CostDictionaryProvider {
    private final CostLiteProperties.Dictionary dictionary;
    private final CostDictionaryProvider delegate;

    public CostLiteSystemDictionaryProvider(CostLiteProperties properties,
                                            SysDictDataMapper dictDataMapper,
                                            SysDictTypeMapper dictTypeMapper) {
        this.dictionary = properties.getDictionary();
        this.delegate = new SystemCostDictionaryProvider(dictDataMapper, dictTypeMapper);
    }

    @Override
    public boolean containsType(String dictType) {
        if (!dictionary.isValidationEnabled()) {
            return true;
        }
        return delegate.containsType(dictionary.resolveType(dictType));
    }

    @Override
    public boolean containsValue(String dictType, String dictValue) {
        if (!dictionary.isValidationEnabled()) {
            return true;
        }
        return delegate.containsValue(
                dictionary.resolveType(dictType),
                dictionary.resolveValue(dictType, dictValue));
    }
}
