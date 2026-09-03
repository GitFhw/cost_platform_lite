package com.ruoyi.lite.plugin;

/**
 * 轻量计费插件 SPI。
 *
 * <p>插件只依赖本包中的稳定接口，未安装或删除插件不会影响核心计费链。</p>
 */
public interface CostLitePlugin {
    String getCode();

    default String getName() {
        return getCode();
    }

    void initialize(CostLitePluginContext context);
}
