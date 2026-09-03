package com.ruoyi.lite.config;

import com.ruoyi.lite.web.CostLiteAdminTokenInterceptor;
import com.ruoyi.lite.web.CostLiteOperatorInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 轻量宿主 Web 配置。
 */
@Configuration
public class CostLiteWebConfig implements WebMvcConfigurer {
    private final CostLiteOperatorInterceptor operatorInterceptor;
    private final CostLiteAdminTokenInterceptor adminTokenInterceptor;

    public CostLiteWebConfig(CostLiteOperatorInterceptor operatorInterceptor,
                             CostLiteAdminTokenInterceptor adminTokenInterceptor) {
        this.operatorInterceptor = operatorInterceptor;
        this.adminTokenInterceptor = adminTokenInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(operatorInterceptor).addPathPatterns("/**").order(0);
        registry.addInterceptor(adminTokenInterceptor)
                .addPathPatterns("/cost/**")
                .excludePathPatterns("/cost/open/**", "/cost/lite/health")
                .order(1);
    }

    @Bean
    public CorsFilter costLiteCorsFilter() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.addAllowedOriginPattern("*");
        configuration.addAllowedHeader("*");
        configuration.addAllowedMethod("*");
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(1800L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return new CorsFilter(source);
    }
}
