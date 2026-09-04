package com.costplatform.lite.starter;

import com.ruoyi.lite.web.CostLiteAdminTokenInterceptor;
import com.ruoyi.lite.web.CostLiteOperatorInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 嵌入模式只注册计费接口拦截器，不修改宿主的全局 CORS、SecurityFilterChain 或其他 URL。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "cost.lite.embedded", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CostLiteEmbeddedWebConfiguration implements WebMvcConfigurer {
    private final CostLiteOperatorInterceptor operatorInterceptor;
    private final CostLiteAdminTokenInterceptor adminTokenInterceptor;

    public CostLiteEmbeddedWebConfiguration(CostLiteOperatorInterceptor operatorInterceptor,
                                            CostLiteAdminTokenInterceptor adminTokenInterceptor) {
        this.operatorInterceptor = operatorInterceptor;
        this.adminTokenInterceptor = adminTokenInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(operatorInterceptor)
                .addPathPatterns("/cost/**")
                .order(0);
        registry.addInterceptor(adminTokenInterceptor)
                .addPathPatterns("/cost/**")
                .excludePathPatterns("/cost/open/**", "/cost/lite/health")
                .order(1);
    }
}
