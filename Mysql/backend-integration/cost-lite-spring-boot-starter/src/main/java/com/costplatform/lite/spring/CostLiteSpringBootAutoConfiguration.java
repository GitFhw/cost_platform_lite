package com.costplatform.lite.spring;

import com.costplatform.lite.client.CostLiteClient;
import com.costplatform.lite.client.DefaultCostLiteClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spring Boot 2/3 均可加载的自动配置。核心客户端本身不依赖 Spring。
 */
@Configuration
@ConditionalOnClass({RestController.class, ObjectMapper.class})
@ConditionalOnProperty(prefix = "cost.lite.integration", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CostLiteSpringProperties.class)
public class CostLiteSpringBootAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public CostLiteClient costLiteClient(CostLiteSpringProperties properties, ObjectMapper objectMapper) {
        return new DefaultCostLiteClient(properties.toClientProperties(), objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public CostLiteRouteResolver costLiteRouteResolver(CostLiteSpringProperties properties) {
        return new DefaultCostLiteRouteResolver(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public CostLiteProxyResponseFactory costLiteProxyResponseFactory(ObjectMapper objectMapper,
                                                                       CostLiteSpringProperties properties) {
        return new CostLiteProxyResponseFactory(objectMapper, properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "cost.lite.integration", name = "proxy-enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public CostLiteProxyController costLiteProxyController(CostLiteClient client,
                                                            CostLiteProxyResponseFactory responseFactory,
                                                            CostLiteRouteResolver routeResolver) {
        return new CostLiteProxyController(client, responseFactory, routeResolver);
    }
}
