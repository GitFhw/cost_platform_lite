package com.costplatform.lite.starter;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Oracle 专用计费库数据源配置。
 *
 * <p>该配置先于 Spring Boot 默认数据源处理，使只有计费库配置的宿主也能直接启动；
 * 数据源使用 costLite 命名空间，不覆盖宿主默认数据源。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "cost.lite.embedded", name = "enabled", havingValue = "true", matchIfMissing = true)
@Conditional(CostLiteDataSourceModeCondition.Dedicated.class)
@AutoConfigureBefore(DataSourceAutoConfiguration.class)
public class CostLiteDedicatedDataSourceConfiguration {
    @Bean(name = "costLiteDataSource", destroyMethod = "close")
    @ConditionalOnMissingBean(name = "costLiteDataSource")
    public HikariDataSource costLiteDataSource(Environment environment) {
        HikariDataSource dataSource = new HikariDataSource();
        Binder.get(environment).bind("cost.lite.datasource.hikari", Bindable.ofInstance(dataSource));
        String jdbcUrl = firstNonBlank(environment.getProperty("cost.lite.datasource.url"),
                environment.getProperty("COST_LITE_DB_URL"));
        if (jdbcUrl == null || jdbcUrl.trim().isEmpty()) {
            throw new IllegalStateException("cost.lite.datasource.mode=dedicated 时必须配置 cost.lite.datasource.url");
        }
        dataSource.setDriverClassName(firstNonBlank(environment.getProperty("cost.lite.datasource.driver-class-name"),
                environment.getProperty("COST_LITE_DB_DRIVER"), "oracle.jdbc.OracleDriver"));
        dataSource.setJdbcUrl(jdbcUrl);
        dataSource.setUsername(firstNonBlank(environment.getProperty("cost.lite.datasource.username"),
                environment.getProperty("COST_LITE_DB_USERNAME")));
        dataSource.setPassword(firstNonBlank(environment.getProperty("cost.lite.datasource.password"),
                environment.getProperty("COST_LITE_DB_PASSWORD")));
        return dataSource;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }
}
