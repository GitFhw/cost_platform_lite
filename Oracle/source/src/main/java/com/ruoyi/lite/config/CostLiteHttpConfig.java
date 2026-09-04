package com.ruoyi.lite.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * 母体远程变量和告警链共用的 HTTP 客户端配置。
 */
@Configuration
public class CostLiteHttpConfig {
    @Bean(name = "costAccessRestTemplate")
    @ConditionalOnMissingBean(name = "costAccessRestTemplate")
    public RestTemplate costAccessRestTemplate(RestTemplateBuilder builder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000);
        requestFactory.setReadTimeout(30000);
        return builder
                .requestFactory(() -> requestFactory)
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
    }
}
