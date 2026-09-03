package com.ruoyi.system.service.impl.cost.dictionary;

import com.ruoyi.system.mapper.SysDictDataMapper;
import com.ruoyi.system.mapper.SysDictTypeMapper;
import com.ruoyi.system.service.cost.dictionary.CostDictionaryProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 母体字典适配默认配置。
 */
@Configuration
public class CostDictionaryProviderConfiguration {
    @Bean
    @ConditionalOnMissingBean(CostDictionaryProvider.class)
    public CostDictionaryProvider systemCostDictionaryProvider(SysDictDataMapper dictDataMapper,
                                                                 SysDictTypeMapper dictTypeMapper) {
        return new SystemCostDictionaryProvider(dictDataMapper, dictTypeMapper);
    }
}
