package com.ruoyi.lite.config;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.type.JdbcType;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

/**
 * 轻量宿主的数据源和 MyBatis 配置。
 *
 * <p>使用标准 Spring datasource 配置，MySQL 和 Oracle 只需要替换驱动、URL 和方言参数，
 * 不复制母体实体和表模型。</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "cost.lite.embedded", name = "enabled", havingValue = "false")
@MapperScan(basePackages = "com.ruoyi.system.mapper", sqlSessionFactoryRef = "costLiteSqlSessionFactory")
public class CostLitePersistenceConfig {
    @Bean(name = "costLiteDataSourceProperties")
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties costLiteDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "costLiteDataSource")
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource costLiteDataSource(
            @Qualifier("costLiteDataSourceProperties") DataSourceProperties costLiteDataSourceProperties) {
        return costLiteDataSourceProperties.initializeDataSourceBuilder().build();
    }

    @Bean(name = "costLiteSqlSessionFactory")
    public SqlSessionFactory costLiteSqlSessionFactory(
            @Qualifier("costLiteDataSource") DataSource costLiteDataSource) throws Exception {
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(costLiteDataSource);
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
    public PlatformTransactionManager costLiteTransactionManager(
            @Qualifier("costLiteDataSource") DataSource costLiteDataSource) {
        return new DataSourceTransactionManager(costLiteDataSource);
    }

    @Bean(name = "costLiteTransactionTemplate")
    public TransactionTemplate costLiteTransactionTemplate(
            @Qualifier("costLiteTransactionManager") PlatformTransactionManager costLiteTransactionManager) {
        return new TransactionTemplate(costLiteTransactionManager);
    }
}
