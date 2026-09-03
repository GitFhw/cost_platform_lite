package com.ruoyi.lite.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 轻量宿主不依赖完整若依登录页，管理接口鉴权由轻量令牌拦截器负责。
 */
@Configuration
public class CostLiteSecurityConfig {
    @Bean
    public SecurityFilterChain costLiteSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .build();
    }
}
