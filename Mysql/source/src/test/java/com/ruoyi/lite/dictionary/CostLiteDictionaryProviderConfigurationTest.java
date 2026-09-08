package com.ruoyi.lite.dictionary;

import com.ruoyi.lite.config.CostLiteProperties;
import com.costplatform.lite.extension.CostLiteDictionaryProvider;
import com.ruoyi.system.mapper.SysDictDataMapper;
import com.ruoyi.system.mapper.SysDictTypeMapper;
import com.ruoyi.system.service.cost.dictionary.CostDictionaryProvider;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class CostLiteDictionaryProviderConfigurationTest {
    @Test
    void defaultsToDictionaryTablesWhenNoHostProviderExists() {
        try (AnnotationConfigApplicationContext context = newContext(null, false)) {
            CostDictionaryProvider provider = context.getBean(CostDictionaryProvider.class);
            assertInstanceOf(CostLiteSystemDictionaryProvider.class, provider);
        }
    }

    @Test
    void doesNotOverrideHostDictionaryProvider() {
        try (AnnotationConfigApplicationContext context = newContext("CUSTOM", true)) {
            assertEquals(1, context.getBeansOfType(CostDictionaryProvider.class).size());
            assertInstanceOf(HostDictionaryProvider.class, context.getBean(CostDictionaryProvider.class));
        }
    }

    @Test
    void supportsExplicitConfigurationDictionary() {
        try (AnnotationConfigApplicationContext context = newContext("CONFIG", false)) {
            assertInstanceOf(CostLiteConfigDictionaryProvider.class,
                    context.getBean(CostDictionaryProvider.class));
        }
    }

    @Test
    void adaptsPublicHostProviderWhenCustomModeIsEnabled() {
        try (AnnotationConfigApplicationContext context = newContext("CUSTOM", false, true)) {
            CostDictionaryProvider provider = context.getBean(CostDictionaryProvider.class);
            assertEquals(true, provider.containsType("host_type"));
            assertEquals(true, provider.containsValue("host_type", "host_value"));
        }
    }

    private AnnotationConfigApplicationContext newContext(String provider, boolean hostProvider) {
        return newContext(provider, hostProvider, false);
    }

    private AnnotationConfigApplicationContext newContext(String provider, boolean hostProvider,
                                                           boolean publicHostProvider) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(
                new org.springframework.core.env.MapPropertySource(
                        "test-properties",
                        provider == null
                                ? java.util.Collections.<String, Object>emptyMap()
                                : java.util.Collections.<String, Object>singletonMap(
                                "cost.lite.dictionary.provider", provider)));
        context.registerBean(CostLiteProperties.class, CostLiteProperties::new);
        context.registerBean(SysDictDataMapper.class, () -> org.mockito.Mockito.mock(SysDictDataMapper.class));
        context.registerBean(SysDictTypeMapper.class, () -> org.mockito.Mockito.mock(SysDictTypeMapper.class));
        if (hostProvider) {
            context.registerBean(CostDictionaryProvider.class, HostDictionaryProvider::new);
        }
        if (publicHostProvider) {
            context.registerBean(CostLiteDictionaryProvider.class, PublicHostDictionaryProvider::new);
        }
        context.register(CostLiteDictionaryProviderConfiguration.class);
        context.refresh();
        return context;
    }

    private static class HostDictionaryProvider implements CostDictionaryProvider {
        @Override
        public boolean containsType(String dictType) {
            return true;
        }

        @Override
        public boolean containsValue(String dictType, String dictValue) {
            return true;
        }
    }

    private static class PublicHostDictionaryProvider implements CostLiteDictionaryProvider {
        @Override
        public boolean containsType(String dictType) {
            return "host_type".equals(dictType);
        }

        @Override
        public boolean containsValue(String dictType, String dictValue) {
            return "host_type".equals(dictType) && "host_value".equals(dictValue);
        }
    }
}
