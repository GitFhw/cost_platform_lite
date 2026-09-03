package com.ruoyi.lite;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.ruoyi.lite.config.CostLiteProperties;

/**
 * 轻量计费平台独立启动入口。
 *
 * <p>这里只扫描母体计费服务和必要的基础 Mapper，不扫描完整若依后台，
 * 让交付给业务系统的 JAR 保持边界清晰。</p>
 */
@SpringBootApplication(
        scanBasePackages = {
                "com.ruoyi.lite",
                "com.ruoyi.common.core.redis",
                "com.ruoyi.system.config.cost",
                "com.ruoyi.system.service.cost",
                "com.ruoyi.system.service.impl.cost"
        },
        exclude = {
                RedisAutoConfiguration.class,
                RedisRepositoriesAutoConfiguration.class,
                UserDetailsServiceAutoConfiguration.class
        }
)
@EnableConfigurationProperties(CostLiteProperties.class)
public class CostLiteApplication {
    public static void main(String[] args) {
        SpringApplication.run(CostLiteApplication.class, args);
    }
}
