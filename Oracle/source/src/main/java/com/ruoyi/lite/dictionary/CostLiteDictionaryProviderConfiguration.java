package com.ruoyi.lite.dictionary;

import com.ruoyi.lite.config.CostLiteProperties;
import com.ruoyi.system.mapper.SysDictDataMapper;
import com.ruoyi.system.mapper.SysDictTypeMapper;
import com.ruoyi.system.service.cost.dictionary.CostDictionaryProvider;
import com.costplatform.lite.extension.CostLiteDictionaryProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 轻量计费字典默认配置。
 *
 * <p>默认从当前计费数据源中的 {@code sys_dict_type}、{@code sys_dict_data} 读取字典。
 * 宿主若提供 {@link CostDictionaryProvider}，轻量默认实现不会覆盖宿主实现；
 * 需要简单配置型字典时可显式选择 {@code CONFIG}。</p>
 */
@Configuration(proxyBeanMethods = false)
public class CostLiteDictionaryProviderConfiguration {
    @Bean
    @ConditionalOnProperty(
            prefix = "cost.lite.dictionary",
            name = "provider",
            havingValue = "SYSTEM",
            matchIfMissing = true)
    @ConditionalOnMissingBean(CostDictionaryProvider.class)
    public CostDictionaryProvider costLiteSystemDictionaryProvider(
            CostLiteProperties properties,
            SysDictDataMapper dictDataMapper,
            SysDictTypeMapper dictTypeMapper) {
        return new CostLiteSystemDictionaryProvider(properties, dictDataMapper, dictTypeMapper);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "cost.lite.dictionary",
            name = "provider",
            havingValue = "CONFIG")
    @ConditionalOnMissingBean(CostDictionaryProvider.class)
    public CostDictionaryProvider costLiteConfigDictionaryProvider(CostLiteProperties properties) {
        return new CostLiteConfigDictionaryProvider(properties);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "cost.lite.dictionary",
            name = "provider",
            havingValue = "CUSTOM")
    @ConditionalOnMissingBean(CostDictionaryProvider.class)
    public CostDictionaryProvider costLiteCustomDictionaryProvider(
            ObjectProvider<CostLiteDictionaryProvider> provider) {
        CostLiteDictionaryProvider delegate = provider.getIfAvailable();
        if (delegate == null) {
            throw new IllegalStateException("字典提供方式为 CUSTOM 时，必须提供 CostLiteDictionaryProvider Bean");
        }
        return new CostDictionaryProvider() {
            @Override
            public boolean containsType(String dictType) {
                return delegate.containsType(dictType);
            }

            @Override
            public boolean containsValue(String dictType, String dictValue) {
                return delegate.containsValue(dictType, dictValue);
            }
        };
    }
}
