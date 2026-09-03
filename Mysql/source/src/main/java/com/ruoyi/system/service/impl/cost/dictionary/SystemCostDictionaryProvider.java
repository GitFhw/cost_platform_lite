package com.ruoyi.system.service.impl.cost.dictionary;

import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.system.mapper.SysDictDataMapper;
import com.ruoyi.system.mapper.SysDictTypeMapper;
import com.ruoyi.system.service.cost.dictionary.CostDictionaryProvider;

import java.util.List;

/**
 * 母体系统字典适配器。
 */
public class SystemCostDictionaryProvider implements CostDictionaryProvider {
    private final SysDictDataMapper dictDataMapper;
    private final SysDictTypeMapper dictTypeMapper;

    public SystemCostDictionaryProvider(SysDictDataMapper dictDataMapper, SysDictTypeMapper dictTypeMapper) {
        this.dictDataMapper = dictDataMapper;
        this.dictTypeMapper = dictTypeMapper;
    }

    @Override
    public boolean containsType(String dictType) {
        return dictTypeMapper.selectDictTypeByType(dictType) != null;
    }

    @Override
    public boolean containsValue(String dictType, String dictValue) {
        List<SysDictData> values = dictDataMapper.selectDictDataByType(dictType);
        return values.stream()
                .anyMatch(item -> dictValue.equals(item.getDictValue()) && "0".equals(item.getStatus()));
    }
}
