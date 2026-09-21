package com.costplatform.lite.starter;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;

/**
 * 复用宿主业务数据源的计费数据源配置。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "cost.lite.embedded", name = "enabled", havingValue = "true", matchIfMissing = true)
@Conditional(CostLiteDataSourceModeCondition.Host.class)
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
public class CostLiteHostDataSourceConfiguration {
    @Bean(name = "costLiteDataSource", autowireCandidate = false, defaultCandidate = false)
    @ConditionalOnMissingBean(name = "costLiteDataSource")
    public DataSource costLiteDataSource(Environment environment,
                                         ConfigurableListableBeanFactory beanFactory) {
        String hostBeanName = firstNonBlank(environment.getProperty("cost.lite.datasource.host-bean-name"),
                environment.getProperty("COST_LITE_HOST_DATASOURCE_BEAN"), "dataSource");
        if ("costLiteDataSource".equals(hostBeanName) || !beanFactory.containsBean(hostBeanName)) {
            throw new IllegalStateException("cost.lite.datasource.mode=host 时，宿主不存在名为 "
                    + hostBeanName + " 的 DataSource Bean");
        }
        return beanFactory.getBean(hostBeanName, DataSource.class);
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
