package com.ruoyi.lite.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Objects;

/**
 * 插件初始化上下文。
 */
public final class CostLitePluginContext {
    private final ObjectMapper objectMapper;
    private final CostLitePluginRegistry registry;

    public CostLitePluginContext(ObjectMapper objectMapper, CostLitePluginRegistry registry) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.registry = Objects.requireNonNull(registry);
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    public CostLitePluginRegistry getRegistry() {
        return registry;
    }
}
