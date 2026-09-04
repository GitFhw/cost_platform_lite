package com.ruoyi.lite.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 轻量运行时 JSON 兼容配置。
 */
@Configuration(proxyBeanMethods = false)
public class CostLiteJacksonConfig {
    @Bean
    @ConditionalOnMissingBean(name = "costLiteSafeLongSerialization")
    public Jackson2ObjectMapperBuilderCustomizer costLiteSafeLongSerialization() {
        return builder -> builder
                .serializerByType(Long.class, CostLiteSafeLongSerializer.INSTANCE)
                .serializerByType(Long.TYPE, CostLiteSafeLongSerializer.INSTANCE);
    }
}
