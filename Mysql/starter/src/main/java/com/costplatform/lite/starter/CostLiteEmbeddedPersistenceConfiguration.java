package com.costplatform.lite.starter;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.ruoyi.lite.config.CostLiteOracleSqlInterceptor;
import com.ruoyi.lite.config.CostLiteProperties;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.type.JdbcType;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

/**
 * 嵌入模式的独立数据库配置。
 *
 * <p>计费模块始终使用 costLite 前缀的 DataSource、SqlSessionFactory 和事务管理器，
 * 不覆盖客户应用自己的默认数据源。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "cost.lite.embedded", name = "enabled", havingValue = "true", matchIfMissing = true)
@MapperScan(basePackages = "com.ruoyi.system.mapper", sqlSessionFactoryRef = "costLiteSqlSessionFactory")
public class CostLiteEmbeddedPersistenceConfiguration {
    @Bean(name = "costLiteDataSource")
    @ConditionalOnMissingBean(name = "costLiteDataSource")
    @ConfigurationProperties("cost.lite.datasource.hikari")
    public HikariDataSource costLiteDataSource(
            Environment environment) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setDriverClassName(environment.getProperty(
                "cost.lite.datasource.driver-class-name", "com.mysql.cj.jdbc.Driver"));
        dataSource.setJdbcUrl(environment.getProperty("cost.lite.datasource.url"));
        dataSource.setUsername(environment.getProperty("cost.lite.datasource.username"));
        dataSource.setPassword(environment.getProperty("cost.lite.datasource.password"));
        return dataSource;
    }

    @Bean(name = "costLiteSqlSessionFactory")
    @ConditionalOnMissingBean(name = "costLiteSqlSessionFactory")
    public SqlSessionFactory costLiteSqlSessionFactory(
            @Qualifier("costLiteDataSource") DataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setTypeAliasesPackage("com.ruoyi.system.domain,com.ruoyi.common.core.domain");
        Resource[] mapperResources = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:cost-lite/mapper/**/*Mapper.xml");
        factory.setMapperLocations(mapperResources);

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setCallSettersOnNulls(true);
        configuration.setJdbcTypeForNull(JdbcType.NULL);
        factory.setConfiguration(configuration);
        factory.setPlugins(new CostLiteOracleSqlInterceptor());
        return factory.getObject();
    }

    @Bean(name = "costLiteTransactionManager")
    @ConditionalOnMissingBean(name = "costLiteTransactionManager")
    public PlatformTransactionManager costLiteTransactionManager(
            @Qualifier("costLiteDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean(name = "costLiteTransactionTemplate")
    @ConditionalOnMissingBean(name = "costLiteTransactionTemplate")
    public TransactionTemplate costLiteTransactionTemplate(
            @Qualifier("costLiteTransactionManager") PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
