package com.ruoyi.lite.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.lite.config.CostLiteProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ServiceLoader;

/**
 * 从外部 plugins 目录加载 Java SPI 插件。
 */
@Component
public class CostLitePluginLoader {
    private static final Logger log = LoggerFactory.getLogger(CostLitePluginLoader.class);
    private final CostLiteProperties properties;
    private final CostLitePluginRegistry registry;
    private final ObjectMapper objectMapper;

    public CostLitePluginLoader(CostLiteProperties properties,
                                CostLitePluginRegistry registry,
                                ObjectMapper objectMapper) {
        this.properties = properties;
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void load() {
        if (!properties.isPluginEnabled()) {
            return;
        }
        File directory = new File(properties.getPluginDir() == null ? "plugins" : properties.getPluginDir());
        if (!directory.isDirectory()) {
            return;
        }
        File[] jars = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
        if (jars == null || jars.length == 0) {
            return;
        }
        for (File jar : jars) {
            loadJar(jar);
        }
    }

    private void loadJar(File jar) {
        try {
            URLClassLoader classLoader = new URLClassLoader(new URL[]{jar.toURI().toURL()},
                    CostLitePlugin.class.getClassLoader());
            ServiceLoader<CostLitePlugin> loader = ServiceLoader.load(CostLitePlugin.class, classLoader);
            for (CostLitePlugin plugin : loader) {
                registry.register(plugin);
                plugin.initialize(new CostLitePluginContext(objectMapper, registry));
                log.info("轻量计费插件已加载：{} ({})", plugin.getCode(), jar.getName());
            }
        } catch (Exception exception) {
            log.warn("轻量计费插件加载失败，已跳过：{}", jar.getAbsolutePath(), exception);
        }
    }
}
