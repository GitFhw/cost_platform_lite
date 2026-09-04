package com.costplatform.lite.starter;

import com.ruoyi.lite.config.CostLiteHttpConfig;
import com.ruoyi.lite.config.CostLiteJacksonConfig;
import com.ruoyi.lite.config.CostLiteProperties;
import com.ruoyi.lite.config.CostLiteThreadPoolConfig;
import com.ruoyi.lite.config.LiteSysConfigService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/**
 * 计费核心的嵌入式入口。
 *
 * <p>客户应用只需要依赖此 Starter，计费 Controller、Service、Mapper 和运行时组件
 * 就会注册到客户应用自己的 Spring 容器中，不会启动第二个进程。</p>
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(CostLiteProperties.class)
@ConditionalOnProperty(prefix = "cost.lite.embedded", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CostLiteProperties.class)
@Import({
        CostLiteEmbeddedPersistenceConfiguration.class,
        CostLiteEmbeddedWebConfiguration.class,
        CostLiteHttpConfig.class,
        CostLiteJacksonConfig.class,
        CostLiteThreadPoolConfig.class,
        LiteSysConfigService.class
})
@ComponentScan(basePackages = {
        "com.ruoyi.common.config",
        "com.ruoyi.common.core.redis",
        "com.ruoyi.common.utils.spring",
        "com.ruoyi.lite.controller",
        "com.ruoyi.lite.dictionary",
        "com.ruoyi.lite.plugin",
        "com.ruoyi.lite.service",
        "com.ruoyi.lite.web",
        "com.ruoyi.system.config.cost",
        "com.ruoyi.system.service.cost",
        "com.ruoyi.system.service.impl.cost"
})
public class CostLiteAutoConfiguration {
}
