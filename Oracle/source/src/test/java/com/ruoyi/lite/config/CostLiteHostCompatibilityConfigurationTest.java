package com.ruoyi.lite.config;

import com.ruoyi.system.service.ISysConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class CostLiteHostCompatibilityConfigurationTest {
    @Test
    void suppliesFallbackOnlyWhenHostHasNoConfigService() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                CostLiteHostCompatibilityConfiguration.class)) {
            assertInstanceOf(LiteSysConfigService.class, context.getBean(ISysConfigService.class));
        }
    }

    @Test
    void preservesHostConfigService() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean("hostConfigService", ISysConfigService.class, LiteSysConfigService::new);
            context.register(CostLiteHostCompatibilityConfiguration.class);
            context.refresh();
            assertInstanceOf(LiteSysConfigService.class, context.getBean(ISysConfigService.class));
            assertInstanceOf(LiteSysConfigService.class, context.getBean("hostConfigService"));
        }
    }
}
