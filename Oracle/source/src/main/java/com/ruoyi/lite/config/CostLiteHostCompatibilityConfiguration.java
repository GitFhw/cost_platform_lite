package com.ruoyi.lite.config;

import com.ruoyi.system.service.ISysConfigService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 为不带完整若依系统参数模块的宿主提供最小兼容实现。
 *
 * <p>只有宿主没有自己的 {@link ISysConfigService} 时才注册，避免嵌入成熟框架
 * 后覆盖宿主原有的系统配置服务。</p>
 */
@Configuration(proxyBeanMethods = false)
public class CostLiteHostCompatibilityConfiguration {
    @Bean
    @ConditionalOnMissingBean(ISysConfigService.class)
    public ISysConfigService liteSysConfigService() {
        return new LiteSysConfigService();
    }
}
